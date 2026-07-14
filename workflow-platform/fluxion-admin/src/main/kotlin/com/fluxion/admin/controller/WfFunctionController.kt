package com.fluxion.admin.controller

import com.fluxion.admin.exception.WorkflowAdminException
import com.fluxion.admin.generated.api.FunctionsApi
import com.fluxion.admin.generated.model.FunctionCategory
import com.fluxion.admin.generated.model.FunctionDefinition
import com.fluxion.admin.generated.model.PageResponseFunctionDefinition
import com.fluxion.admin.generated.model.TestFunction200Response
import com.fluxion.admin.generated.model.TestFunctionRequest
import com.fluxion.admin.mapper.FunctionMapper
import com.fluxion.admin.mapper.JsonMapperHelper
import com.fluxion.admin.repository.WfFunctionRepository
import com.fluxion.admin.security.SecurityContextHelper
import com.fluxion.admin.service.WfFunctionService
import com.fluxion.decorator.decorator.NodeDecorator
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.outbound.OutboundTransportRegistry
import com.fluxion.core.model.NodeInput
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.model.ValidationResult
import com.fluxion.script.function.ExternalWorkflowFunction
import com.fluxion.script.function.ScriptWorkflowFunction
import com.fluxion.script.groovy.GroovyScriptFunction
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Function registry & testing REST controller (implements OpenAPI-generated [FunctionsApi]).
 *
 * Functions have two distinct sources of truth that are merged for list / get endpoints:
 *   1. **BUILTIN functions** — source of truth is the in-memory [FunctionRegistry]
 *      (code-registered at boot). These functions do NOT have DB rows.
 *   2. **Non-BUILTIN functions** (SCRIPT_GROOVY / EXTERNAL / CUSTOM) — source of truth
 *      is the `wf_function` DB table (tenant-scoped, versioned).
 *
 * The `/test` endpoint lets developers ad-hoc execute a function by name against
 * provided inputs — it constructs a temporary function instance (Registry for BUILTINs,
 * DB definition for scripts/externals), wraps it with a capturing test decorator,
 * and validates outputs against the declared outputSchema.
 *
 * Collaborates with: FunctionRegistry (builtin functions + resolution),
 * WfFunctionRepository (DB persistence), FunctionMapper (DTO mapping),
 * WfFunctionService (persist + worker push), SecurityContextHelper (tenant filtering
 * for list + tenant gating for PRIVATE edits), JsonMapperHelper (config JSON parsing),
 * GroovyScriptFunction (script execution engine for tests), SchemaManager (output
 * validation during tests).
 */
@RestController
class WfFunctionController(
    private val functionRegistry: FunctionRegistry,
    private val functionRepository: WfFunctionRepository,
    private val functionMapper: FunctionMapper,
    private val functionService: WfFunctionService,
    private val securityContext: SecurityContextHelper,
    private val jsonMapperHelper: JsonMapperHelper,
    private val groovyScriptFunction: GroovyScriptFunction?,
    private val schemaManager: SchemaManager,
) : FunctionsApi {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Paginated function list with optional keyword + category filter.
     *
     * Results are the concatenation of (registry builtins) + (DB non-builtins),
     * filtered in memory, then manually sliced into a page (Spring Data pageables
     * cannot span two heterogeneous data sources). Tenant filtering is applied to
     * DB rows the same way as schemas/workflows: ADMIN sees everything, non-ADMIN
     * sees PLATFORM + MARKETPLACE + PRIVATE rows whose appGroup they belong to.
     */
    override fun listFunctions(
        keyword: String?,
        category: FunctionCategory?,
        page: Int,
        pageSize: Int,
        appId: Long?,
        sortBy: String,
        sortDir: String
    ): ResponseEntity<PageResponseFunctionDefinition> {
        val size = pageSize.coerceAtMost(100)
        val direction = if (sortDir.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page, size, Sort.by(direction, sortBy))

        // Source 1: BUILTIN functions registered in the engine's FunctionRegistry
        val registryFunctions = functionRegistry.listAll()
            .map { meta -> functionMapper.registryMetaToDto(meta) }
            .filter { dto ->
                val matchKeyword = keyword.isNullOrBlank() ||
                    dto.name.lowercase().contains(keyword.lowercase()) ||
                    dto.config?.get("description")?.toString()?.lowercase()?.contains(keyword.lowercase()) == true
                val matchCategory = category == null || dto.category == category
                matchKeyword && matchCategory
            }
            .sortedBy { it.name }

        // Source 2: DB-backed non-BUILTIN functions
        val accessibleGroups = securityContext.accessibleAppGroups()
        val dbFunctions = functionRepository.findAll()
            .filter { fn -> fn.functionType != "BUILTIN" }
            .filter { fn ->
                accessibleGroups == null || fn.scope == "PLATFORM" || fn.scope == "MARKETPLACE" ||
                    (fn.scope == "PRIVATE" && fn.appGroup in accessibleGroups)
            }
            .map { functionMapper.toDto(it) }
            .filter { dto ->
                val matchKeyword = keyword.isNullOrBlank() ||
                    dto.name.lowercase().contains(keyword.lowercase()) ||
                    dto.config?.get("description")?.toString()?.lowercase()?.contains(keyword.lowercase()) == true
                val matchCategory = category == null || dto.category == category
                matchKeyword && matchCategory
            }

        // Manual pagination across the merged list
        val all = registryFunctions + dbFunctions
        val start = pageable.offset.toInt()
        val end = (start + pageable.pageSize).coerceAtMost(all.size)
        val pageContent = if (start < all.size) all.subList(start, end) else emptyList()
        return ResponseEntity.ok(PageResponseFunctionDefinition().apply {
            list = pageContent.toMutableList()
            total = all.size.toLong()
            this.page = page
            this.pageSize = size
        })
    }

    /** Fetch one function DTO — Registry lookup first for BUILTIN, DB fallback otherwise. */
    override fun getFunction(functionName: String): ResponseEntity<FunctionDefinition> {
        val registryMeta = functionRegistry.getMeta(functionName)
        if (registryMeta != null) {
            return ResponseEntity.ok(functionMapper.registryMetaToDto(registryMeta))
        }
        val entity = functionRepository.findByFunctionName(functionName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(functionMapper.toDto(entity))
    }

    /**
     * Create a new DB-backed function.
     *
     * Uniqueness is scope-aware:
     *   - PRIVATE scoped: unique triple (scope, appGroup, functionName)
     *   - PLATFORM / MARKETPLACE: unique pair (scope, functionName)
     */
    override fun createFunction(functionDefinition: FunctionDefinition): ResponseEntity<FunctionDefinition> {
        val entity = functionMapper.toEntity(functionDefinition)
        if (entity.scope == "PRIVATE" && entity.appGroup.isNullOrBlank()) {
            throw WorkflowAdminException.badRequest("SCOPE_APP_GROUP_REQUIRED", "PRIVATE scope functions require appGroup")
        }
        entity.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        val existingFn = if (entity.scope == "PRIVATE" && !entity.appGroup.isNullOrBlank()) {
            functionRepository.findByScopeAndAppGroupAndFunctionName("PRIVATE", entity.appGroup!!, entity.functionName).orElse(null)
        } else {
            functionRepository.findByScopeAndFunctionName(entity.scope, entity.functionName).orElse(null)
        }
        if (existingFn != null) {
            throw WorkflowAdminException.badRequest("FUNCTION_NAME_CONFLICT", "Function name [${entity.functionName}] already exists in scope=${entity.scope}")
        }
        val saved = functionService.save(entity)
        return ResponseEntity.ok(functionMapper.toDto(saved))
    }

    /** Update a function's mutable fields + replace its function config blob. */
    override fun updateFunction(
        functionName: String,
        functionDefinition: FunctionDefinition
    ): ResponseEntity<FunctionDefinition> {
        val existing = functionRepository.findByFunctionName(functionName)
            .orElseThrow { WorkflowAdminException.notFound("FUNCTION_NOT_FOUND", "Function not found: $functionName") }
        existing.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        functionMapper.updateEntity(functionDefinition, existing)
        if (existing.scope == "PRIVATE" && existing.appGroup.isNullOrBlank()) {
            throw WorkflowAdminException.badRequest("SCOPE_APP_GROUP_REQUIRED", "PRIVATE scope functions require appGroup")
        }
        existing.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        val saved = functionService.save(existing)
        return ResponseEntity.ok(functionMapper.toDto(saved))
    }

    /** Delete a function by name (tenant-gated by existing row appGroup). */
    override fun deleteFunction(functionName: String): ResponseEntity<Unit> {
        val existing = functionRepository.findByFunctionName(functionName).orElse(null)
        existing?.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        functionService.delete(functionName)
        return ResponseEntity.noContent().build()
    }

    /**
     * Ad-hoc function test runner. Executes the function with caller-supplied inputs,
     * wraps the function with a test-only decorator that captures every log line, then
     * validates the returned output against the function's declared outputSchema.
     *
     * Non-200 responses:
     *   - 400 when the function cannot be resolved (missing type / missing script body)
     *   - Returns a 200 with `success=false` for actual invocation exceptions (so the UI
     *     can display the inline stack-trace summary instead of a generic 500).
     */
    override fun testFunction(
        functionName: String,
        testFunctionRequest: TestFunctionRequest
    ): ResponseEntity<TestFunction200Response> {
        val inputs = testFunctionRequest.inputs ?: emptyMap<String, Any>()
        val start = System.currentTimeMillis()
        val logs = mutableListOf<String>()

        val resolved = resolveTestFunction(functionName, logs)
            ?: return ResponseEntity.badRequest().body(
                TestFunction200Response().apply {
                    success = false
                    error = "Function not found: $functionName"
                    durationMs = 0L
                    this.logs = logs.toMutableList()
                    outputValid = true
                    validationErrors = mutableListOf()
                }
            )
        val fn = resolved.function
        val outputSchema = resolved.outputSchema

        // default fallbacks: test UI only fills nodeParams in simple cases
        val nodeInput = NodeInput(
            directInput = testFunctionRequest.directInput ?: inputs,
            nodeParams = inputs,
            workflowInput = testFunctionRequest.workflowInput ?: emptyMap(),
            declaredDeps = testFunctionRequest.declaredDeps ?: emptyMap()
        )

        val virtualNode = WorkflowNode(
            id = "test",
            name = functionName,
            functionRef = functionName,
            workflowId = "test"
        )
        val decoratedFn = CapturingTestDecorator(logs).decorate(fn, virtualNode)

        return try {
            val result = decoratedFn.apply(nodeInput)
            val validation = validateOutput(outputSchema, result.output, logs)
            ResponseEntity.ok(TestFunction200Response().apply {
                success = true
                output = result.output
                durationMs = System.currentTimeMillis() - start
                this.logs = logs.toMutableList()
                outputValid = validation.valid
                validationErrors = validation.errors.toMutableList()
            })
        } catch (e: Exception) {
            ResponseEntity.ok(TestFunction200Response().apply {
                success = false
                error = e.message
                errorType = e.javaClass.simpleName
                errorStack = summarizeStackTrace(e)
                durationMs = System.currentTimeMillis() - start
                this.logs = logs.toMutableList()
            })
        }
    }

    /** Compact 8-frame stack trace for the UI error panel (no need for full 100-line trace). */
    private fun summarizeStackTrace(e: Throwable, maxFrames: Int = 8): String {
        val frames = e.stackTrace?.take(maxFrames) ?: emptyList()
        val sb = StringBuilder("${e.javaClass.name}: ${e.message}\n")
        for (frame in frames) {
            sb.append("    at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})\n")
        }
        if ((e.stackTrace?.size ?: 0) > maxFrames) {
            sb.append("    ... ${e.stackTrace!!.size - maxFrames} more")
        }
        return sb.toString().trimEnd()
    }

    /**
     * Resolve a function to an executable [WorkflowFunction] instance suitable for testing.
     *
     * Resolution order (matches runtime semantics):
     *   1. FunctionRegistry (BUILTIN + already-published DB functions)
     *   2. DB row → instantiate a transient SCRIPT or EXTERNAL function for testing
     *      (SCRIPT_GROOVY needs the Groovy engine; EXTERNAL uses its own transport SPI)
     *   3. null = caller reports Function not found to the user
     *
     * For DB rows, tenant gating is re-applied so users cannot test PRIVATE functions
     * belonging to another team.
     */
    private fun resolveTestFunction(functionName: String, logs: MutableList<String>): ResolvedTestFunction? {
        if (functionRegistry.contains(functionName)) {
            logs.add("[TEST] resolve function [$functionName] from FunctionRegistry")
            val fn = functionRegistry.resolve(functionName)
            val outputSchema = functionRegistry.getMeta(functionName)?.outputSchema
            return ResolvedTestFunction(fn, outputSchema)
        }

        val entity = functionRepository.findByFunctionName(functionName).orElse(null)
            ?: return null

        entity.appGroup?.let { securityContext.requireAppGroupAccess(it) }

        val config = jsonMapperHelper.functionToConfig(entity) ?: emptyMap()
        val paramSchema = jsonMapperHelper.functionConfigToParamSchema(config)
        val outputSchema = jsonMapperHelper.functionConfigToOutputSchema(config)
        val description = jsonMapperHelper.functionConfigToDescription(config)

        val fn: WorkflowFunction<Any> = when (entity.functionType.uppercase()) {
            "SCRIPT_GROOVY", "GROOVY" -> {
                val scriptBody = jsonMapperHelper.functionConfigToScriptBody(config)
                if (scriptBody.isNullOrBlank()) {
                    logs.add("[TEST] function [$functionName] scriptBody is empty")
                    return null
                }
                val engine = groovyScriptFunction
                    ?: throw IllegalStateException("Groovy script engine not available for testing [$functionName]")
                logs.add("[TEST] construct SCRIPT function [$functionName] from database definition")
                ScriptWorkflowFunction(
                    name = functionName,
                    scriptBody = scriptBody,
                    paramSchema = paramSchema,
                    outputSchema = outputSchema,
                    description = description,
                    groovyEngine = engine
                ) as WorkflowFunction<Any>
            }

            "EXTERNAL", "JAVA_CLASS" -> {
                logs.add("[TEST] construct EXTERNAL function [$functionName] from database definition")
                ExternalWorkflowFunction(
                    name = functionName,
                    configMap = config,
                    transportRegistry = OutboundTransportRegistry(),
                    paramSchema = paramSchema,
                    outputSchema = outputSchema,
                    description = description
                ) as WorkflowFunction<Any>
            }

            else -> {
                logs.add("[TEST] unsupported function type [${entity.functionType}] for [$functionName]")
                return null
            }
        }
        return ResolvedTestFunction(fn, outputSchema)
    }

    /** Private holder for a resolved test function + its declared output schema. */
    private data class ResolvedTestFunction(
        val function: WorkflowFunction<Any>,
        val outputSchema: Any?
    )

    /**
     * Validate a function's test output against its declared outputSchema (if any).
     *
     * Uses the fluxion-schema module's [SchemaManager] for validation. Parse errors
     * are treated as a validation failure with a descriptive message rather than
     * bubbling up as a 500. Empty schemas (`{}` / blank / null) skip validation with
     * an automatic pass.
     */
    private fun validateOutput(outputSchema: Any?, output: Any?, logs: MutableList<String>): ValidationResult {
        if (outputSchema == null || isEmptySchema(outputSchema)) {
            return ValidationResult.ok()
        }
        logs.add("[TEST] validating output against outputSchema")
        return try {
            val schemaRaw = if (outputSchema is String) outputSchema else JsonUtil.serialize(outputSchema)
            val schema = schemaManager.parseJson(schemaRaw)
            val result = schemaManager.validate(schema, output)
            if (!result.valid) {
                logs.add("[TEST] output validation failed: ${result.errors.joinToString("; ")}")
            } else {
                logs.add("[TEST] output validation passed")
            }
            result
        } catch (e: Exception) {
            val message = "Schema validation internal error: ${e.message}"
            logs.add("[TEST] $message")
            ValidationResult.fail(listOf(message))
        }
    }

    /** Catch-all "empty schema" check to skip validation for blank / placeholder values. */
    private fun isEmptySchema(schema: Any): Boolean {
        val str = if (schema is String) schema.trim() else JsonUtil.serialize(schema)
        return str.isBlank() || str == "{}" || str == "null"
    }

    /**
     * Decorator used exclusively by the `/functions/test` endpoint. Prepends a
     * `[TEST] function=...` log line on entry + on exit (success with output,
     * failure with error message). Long payloads are truncated to 2000 chars to
     * avoid blowing up the UI debug panel.
     */
    private class CapturingTestDecorator(private val logs: MutableList<String>) : NodeDecorator {

        override fun name(): String = "test:capture"

        override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> =
            WorkflowFunction { input ->
                logs.add(
                    "[TEST] workflow=${node.workflowId} node=${node.name} function=${node.functionRef} " +
                        "inputs=${safeSerialize(input.nodeParams)}"
                )
                val fnStart = System.currentTimeMillis()
                try {
                    val result = function.apply(input)
                    val duration = System.currentTimeMillis() - fnStart
                    logs.add(
                        "[TEST] success function=${node.functionRef} duration=${duration}ms " +
                            "output=${safeSerialize(result.output)}"
                    )
                    result
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - fnStart
                    logs.add(
                        "[TEST] failed function=${node.functionRef} duration=${duration}ms error=${e.message}"
                    )
                    throw e
                }
            }

        /** Safe JSON serializer for debug logs — falls back to toString() and truncates at 2000 chars. */
        private fun safeSerialize(value: Any?, maxLength: Int = 2000): String {
            val serialized = try {
                JsonUtil.serialize(value)
            } catch (e: Exception) {
                value?.toString() ?: "null"
            }
            return if (serialized.length <= maxLength) serialized
            else serialized.take(maxLength) + "...(${serialized.length - maxLength} chars omitted)"
        }
    }

}
