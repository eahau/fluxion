package com.fluxion.admin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fluxion.admin.entity.App
import com.fluxion.admin.entity.WfDefinition
import com.fluxion.admin.entity.WfFunction
import com.fluxion.admin.generated.model.FunctionStatus
import com.fluxion.admin.repository.WfDefinitionRepository
import com.fluxion.admin.repository.WfFunctionRepository
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Lifecycle service for the new "function set" concept.
 *
 * A "function set" is just a regular [WfDefinition] that has been published for
 * reuse by *other* DAGs. Publishing produces a matching row in `wf_function` with
 * `function_type=SET_REF` so:
 *   1. The set appears in the designer's node function-picker dropdown (no new UI path),
 *   2. Runtime code can reuse the existing `SubWorkflowExecutor` dispatch pipeline,
 *   3. Permissions / scope / marketplace rules continue to work exactly as with
 *      BUILTIN / EXTERNAL functions.
 *
 * ### Contract for publishability
 *
 * A DAG can be published only when:
 *   - `status == ACTIVE` (drafts are not reusable units),
 *   - BOTH `inputSchema` and `outputSchema` are populated — because a function set
 *     without a declared interface is useless for composition (how would a caller
 *     validate its wiring?). Null schemas are allowed during the DRAFT design phase
 *     but block publish.
 *
 * ### Why SET_REF in wf_function instead of a new dedicated table?
 *
 * *Zero new concepts in the runtime*. The engine already knows how to:
 *   - resolve a `functionRef` to a `WfFunction`,
 *   - look up a SUB_WORKFLOW node and invoke `SubWorkflowExecutor`.
 *
 * Adding a SET_REF function type simply reuses those two paths with a tiny dispatch
 * shim inside DagExecutor: "if functionType == SET_REF, load the referenced
 * set-workflow and treat the node as SUB_WORKFLOW with mapping applied around it."
 */
@Service
class FunctionSetService(
    private val defRepo: WfDefinitionRepository,
    private val funcRepo: WfFunctionRepository,
    private val compat: SchemaCompatibilityService,
    private val om: ObjectMapper = ObjectMapper().findAndRegisterModules()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TYPE_SET_REF = "SET_REF"
        val REF_NAME_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9_\-:.]{1,126}$""")
    }

    // ═══════════════════════════════════════════════════════════════
    // Publish / Unpublish
    // ═══════════════════════════════════════════════════════════════

    /**
     * Publish `definitionId` as a callable function set.
     *
     * @param setRefName optional short function-name. When `null` the default
     *   `set:<workflowId>` is used. Must match [REF_NAME_REGEX] and be globally
     *   unique across both the `wf_definition.set_ref_name` column and the
     *   `wf_function.function_name` column.
     * @throws IllegalArgumentException if the DAG is not publishable or the name
     *   collides.
     */
    @Transactional
    fun publish(definitionId: Long, setRefName: String? = null): WfDefinition {
        val def = requireNotNull(defRepo.findById(definitionId).orElse(null)) {
            "WfDefinition id=$definitionId not found"
        }
        require(def.status == "ACTIVE") {
            "Only ACTIVE definitions can be published as function sets (current=${def.status})"
        }
        require(!def.inputSchema.isNullOrBlank() && !def.outputSchema.isNullOrBlank()) {
            "Publishing requires both inputSchema and outputSchema to be non-empty (this DAG has no interface contract)."
        }

        val refName = validateAndNormalizeRefName(setRefName, def)

        // Upsert the wf_function row (SET_REF type). If a SET_REF already points to
        // the same DAG under a DIFFERENT refName we first delete the stale row so
        // users can rename their function alias safely.
        if (def.isPublishedSet && def.setRefName != null && def.setRefName != refName) {
            val stale = funcRepo.findByFunctionTypeAndFunctionName(TYPE_SET_REF, def.setRefName!!)
            stale.ifPresent { funcRepo.delete(it) }
        }

        val functionRow = funcRepo.findByFunctionTypeAndFunctionName(TYPE_SET_REF, refName)
            .orElseGet {
                WfFunction().apply {
                    functionType = TYPE_SET_REF
                    functionName = refName
                    status = FunctionStatus.ACTIVE
                    scope = def.scope
                    appGroup = def.appGroup
                    app = def.app   // same owning app
                    domain = "FUNCTION_SET"
                }
            }
        functionRow.config = om.writeValueAsString(buildConfig(def, refName))
        functionRow.scope = def.scope            // keep in sync with def changes
        functionRow.appGroup = def.appGroup
        functionRow.status = FunctionStatus.ACTIVE
        funcRepo.save(functionRow)

        def.isPublishedSet = true
        def.setRefName = refName
        return defRepo.save(def).also {
            log.info { "Published function-set ${it.workflowId} as ref='$refName' (scope=${it.scope} appGroup=${it.appGroup})" }
        }
    }

    /** Undo a publish: delete the SET_REF function row and clear the flags. */
    @Transactional
    fun unpublish(definitionId: Long): WfDefinition {
        val def = requireNotNull(defRepo.findById(definitionId).orElse(null)) {
            "WfDefinition id=$definitionId not found"
        }
        if (!def.isPublishedSet || def.setRefName == null) return def   // idempotent

        val func = funcRepo.findByFunctionTypeAndFunctionName(TYPE_SET_REF, def.setRefName!!)
        func.ifPresent { funcRepo.delete(it) }

        def.isPublishedSet = false
        def.setRefName = null
        return defRepo.save(def).also {
            log.info { "Unpublished function-set ${it.workflowId} (was ref='$func')" }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Queries
    // ═══════════════════════════════════════════════════════════════

    fun listPublishedSets(appId: Long? = null, scope: String? = null): List<WfDefinition> {
        val all = defRepo.findAllPublishedSets()
        return all.filter {
            (appId == null || it.app?.id == appId) &&
                (scope == null || it.scope == scope)
        }
    }

    /** Shortcut used by the designer's "compose after set" widget — pre-check composability. */
    fun canComposeAfter(publishedSetRefName: String, nextSetRefName: String, mappingJson: String? = null): CompositionResult {
        val a = defRepo.findBySetRefName(publishedSetRefName)
            .orElseThrow { IllegalArgumentException("Unknown set A refName=$publishedSetRefName") }
        val b = defRepo.findBySetRefName(nextSetRefName)
            .orElseThrow { IllegalArgumentException("Unknown set B refName=$nextSetRefName") }
        val check = compat.checkWithMapping(a.outputSchema, b.inputSchema, mappingJson)
        return CompositionResult(ok = check.ok, diagnostics = check, A = a.workflowId, B = b.workflowId)
    }

    // ═══════════════════════════════════════════════════════════════
    // Internals
    // ═══════════════════════════════════════════════════════════════

    private fun validateAndNormalizeRefName(requested: String?, def: WfDefinition): String {
        val raw = requested ?: "set:${def.workflowId}"
        require(raw.matches(REF_NAME_REGEX)) {
            "setRefName '$raw' does not match pattern $REF_NAME_REGEX"
        }
        // Conflicts: another definition already uses this name.
        val otherDef = defRepo.findBySetRefName(raw)
        if (otherDef.isPresent && otherDef.get().id != def.id) {
            throw IllegalArgumentException("setRefName '$raw' is already used by WfDefinition id=${otherDef.get().id}")
        }
        // Conflicts: another function (non-SET_REF) uses this function name.
        val sameNameFunc = funcRepo.findByFunctionName(raw)
        if (sameNameFunc.isPresent && sameNameFunc.get().functionType != TYPE_SET_REF) {
            throw IllegalArgumentException("functionName '$raw' is already taken by a ${sameNameFunc.get().functionType} function")
        }
        return raw
    }

    private fun buildConfig(def: WfDefinition, refName: String): Map<String, Any?> {
        val inputSchemaNode = om.readTree(def.inputSchema)
        val outputSchemaNode = om.readTree(def.outputSchema)
        return mapOf(
            "setWorkflowId" to def.workflowId,
            "definitionId" to def.id,
            "setRefName" to refName,
            "category" to def.category,
            "inputSchema" to inputSchemaNode,
            "outputSchema" to outputSchemaNode,
            "_v" to 1
        )
    }
}

data class CompositionResult(
    val ok: Boolean,
    val diagnostics: CompatResult,
    val A: String,
    val B: String
)
