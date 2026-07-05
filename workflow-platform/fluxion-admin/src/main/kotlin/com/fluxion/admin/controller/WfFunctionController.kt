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
import com.fluxion.core.decorator.NodeDecorator
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
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
 * 工作流函数管理 Controller
 *
 * 实现 OpenAPI 生成的 FunctionsApi 接口，确保前后端契约一致。
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

    override fun listFunctions(
        keyword: String?,
        category: FunctionCategory?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseFunctionDefinition> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))

        // 1. BUILTIN 函数：以 FunctionRegistry（代码）为唯一权威数据源
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

        // 2. 非 BUILTIN 函数（SCRIPT / EXTERNAL / CUSTOM）：以 DB 为权威数据源
        val accessibleGroups = securityContext.accessibleAppGroups()
        val dbFunctions = functionRepository.findAll()
            .filter { fn -> fn.functionType != "BUILTIN" }  // BUILTIN 只看 Registry
            .filter { fn ->
                // 租户过滤：非 ADMIN 用户只能看到 PLATFORM + 自己 appGroup 的 PRIVATE
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

        // 3. 合并后分页（Registry BUILTIN + DB 非 BUILTIN）
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

    override fun getFunction(functionName: String): ResponseEntity<FunctionDefinition> {
        // BUILTIN 函数：以 FunctionRegistry（代码）为权威数据源
        val registryMeta = functionRegistry.getMeta(functionName)
        if (registryMeta != null) {
            return ResponseEntity.ok(functionMapper.registryMetaToDto(registryMeta))
        }
        // 非 BUILTIN 函数：从 DB 查找
        val entity = functionRepository.findByFunctionName(functionName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(functionMapper.toDto(entity))
    }

    override fun createFunction(functionDefinition: FunctionDefinition): ResponseEntity<FunctionDefinition> {
        val entity = functionMapper.toEntity(functionDefinition)
        // PRIVATE scope 必须指定 appGroup
        if (entity.scope == "PRIVATE" && entity.appGroup.isNullOrBlank()) {
            throw WorkflowAdminException.badRequest("SCOPE_APP_GROUP_REQUIRED", "PRIVATE 作用域的函数必须指定 appGroup")
        }
        // 租户权限校验
        entity.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        // scope-aware 函数名唯一性校验
        val existingFn = if (entity.scope == "PRIVATE" && !entity.appGroup.isNullOrBlank()) {
            functionRepository.findByScopeAndAppGroupAndFunctionName("PRIVATE", entity.appGroup!!, entity.functionName).orElse(null)
        } else {
            functionRepository.findByScopeAndFunctionName(entity.scope, entity.functionName).orElse(null)
        }
        if (existingFn != null) {
            throw WorkflowAdminException.badRequest("FUNCTION_NAME_CONFLICT", "函数名 [${entity.functionName}] 已在 scope=${entity.scope} 中被占用")
        }
        val saved = functionService.save(entity)
        return ResponseEntity.ok(functionMapper.toDto(saved))
    }

    override fun updateFunction(
        functionName: String,
        functionDefinition: FunctionDefinition
    ): ResponseEntity<FunctionDefinition> {
        val existing = functionRepository.findByFunctionName(functionName)
            .orElseThrow { WorkflowAdminException.notFound("FUNCTION_NOT_FOUND", "函数不存在: $functionName") }
        // 租户权限校验（校验原始 appGroup）
        existing.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        functionMapper.updateEntity(functionDefinition, existing)
        // PRIVATE scope 必须指定 appGroup
        if (existing.scope == "PRIVATE" && existing.appGroup.isNullOrBlank()) {
            throw WorkflowAdminException.badRequest("SCOPE_APP_GROUP_REQUIRED", "PRIVATE 作用域的函数必须指定 appGroup")
        }
        // 若 appGroup 变更，还需校验新 appGroup 的权限
        existing.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        val saved = functionService.save(existing)
        return ResponseEntity.ok(functionMapper.toDto(saved))
    }

    override fun deleteFunction(functionName: String): ResponseEntity<Unit> {
        val existing = functionRepository.findByFunctionName(functionName).orElse(null)
        existing?.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        functionService.delete(functionName)
        return ResponseEntity.noContent().build()
    }

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

        // 测试输入默认：nodeParams 来自 inputs；directInput 未显式指定时回退到 inputs，
        // 保持简单场景下依赖直接入参的函数仍可正常测试。
        val nodeInput = NodeInput(
            directInput = testFunctionRequest.directInput ?: inputs,
            nodeParams = inputs,
            workflowInput = testFunctionRequest.workflowInput ?: emptyMap(),
            declaredDeps = testFunctionRequest.declaredDeps ?: emptyMap()
        )

        // 构造一个虚拟的 WorkflowNode 用于装饰器
        val virtualNode = WorkflowNode(
            id = "test",
            name = functionName,
            functionRef = functionName,
            workflowId = "test"
        )
        // 使用测试专用装饰器，统一格式并收集日志
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

    /** 截取异常堆栈前 8 帧，用于前端调试展示。 */
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
     * 解析可用于测试的函数实例及其出参 schema。
     *
     * 1. 优先从 FunctionRegistry 读取（BUILTIN / 已发布到 Registry 的函数）；
     * 2. 若 Registry 中不存在，尝试从 DB 的函数定义构造临时实例，支持 SCRIPT_GROOVY / EXTERNAL；
     * 3. 均不存在时返回 null，由调用方返回 400。
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

        // 租户权限校验：非 ADMIN 用户只能测试本应用分组的 PRIVATE 函数
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
                    transportRegistry = ExternalFunctionTransportRegistry(),
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

    /**
     * 测试函数解析结果包装。
     */
    private data class ResolvedTestFunction(
        val function: WorkflowFunction<Any>,
        val outputSchema: Any?
    )

    /**
     * 校验函数输出是否符合出参 schema。
     *
     * 使用 fluxion-schema 的 [SchemaManager] 统一能力；解析异常会被捕获并转为校验失败结果，
     * 与原有 [com.fluxion.core.schema.SchemaValidator] 兼容壳行为保持一致。
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

    private fun isEmptySchema(schema: Any): Boolean {
        val str = if (schema is String) schema.trim() else JsonUtil.serialize(schema)
        return str.isBlank() || str == "{}" || str == "null"
    }

    /**
     * 测试专用装饰器：统一记录函数执行日志并收集到列表中返回给前端。
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
