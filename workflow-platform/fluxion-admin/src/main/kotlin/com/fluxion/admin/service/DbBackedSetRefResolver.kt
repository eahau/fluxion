package com.fluxion.admin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fluxion.admin.repository.WfFunctionRepository
import com.fluxion.core.spi.SetRefResolver
import com.fluxion.core.spi.SetRefTarget
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Admin-side [SetRefResolver] implementation.
 *
 * Reads published SET_REF entries straight from the admin DB (`wf_function` table)
 * and converts them to [SetRefTarget] records so the engine-side `DagExecutor` can
 * treat them as transparent SUB_WORKFLOW nodes.
 *
 * ### Cache strategy
 *
 * `wf_function` config JSON is effectively static between publishes (published
 * function sets rarely change); we therefore cache resolved workflowIds in a
 * Caffeine-style small LRU (implemented here as a simple bounded CHM for zero
 * dependencies). A null/negative hit is also cached so repeated lookups against
 * BUILTIN / EXTERNAL refs do not pay a DB round trip.
 *
 * `FunctionSetService.publish / unpublish` evict the corresponding entry on every
 * change by publishing an event — or they can call [evict] directly.
 */
@Service
class DbBackedSetRefResolver(
    private val funcRepo: WfFunctionRepository,
    private val om: ObjectMapper = ObjectMapper().findAndRegisterModules()
) : SetRefResolver {
    private val log = LoggerFactory.getLogger(javaClass)

    private val positive: ConcurrentHashMap<String, SetRefTarget> = ConcurrentHashMap()
    private val negative: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    companion object {
        const val TYPE_SET_REF = "SET_REF"
        const val MAX_POSITIVE = 1_000
        const val MAX_NEGATIVE = 2_000
    }

    override fun resolve(functionRef: String): SetRefTarget? {
        if (functionRef.isBlank()) return null
        positive[functionRef]?.let { return it }
        if (negative[functionRef] == true) return null

        val func = funcRepo.findByFunctionName(functionRef).orElse(null) ?: run {
            rememberNegative(functionRef); return null
        }
        if (func.functionType != TYPE_SET_REF) {
            rememberNegative(functionRef); return null
        }
        val cfg = try {
            val raw = func.config ?: run {
                log.warn { "SET_REF functionName='$functionRef' has empty config JSON (should contain setWorkflowId)" }
                return null
            }
            om.readTree(raw)
        } catch (t: Throwable) {
            log.warn(t) { "SET_REF functionName='$functionRef' failed to parse config JSON" }
            return null
        }
        val workflowId = cfg["setWorkflowId"]?.textValue() ?: return null
        val params = mutableMapOf<String, Any>()
        cfg.path("workflowParams").fields().forEach { (k, v) -> params[k] = om.treeToValue(v, Any::class.java) }
        val defaultMapping = cfg.path("inputMapping")
        if (!defaultMapping.isMissingNode && defaultMapping.isObject) {
            @Suppress("UNCHECKED_CAST")
            val asMap = om.treeToValue(defaultMapping, Map::class.java) as? Map<String, Any>
            if (asMap != null) params["inputMapping"] = asMap
        }
        val target = SetRefTarget(targetWorkflowId = workflowId, workflowParams = params)
        rememberPositive(functionRef, target)
        return target
    }

    fun evict(functionRef: String) {
        positive.remove(functionRef)
        negative.remove(functionRef)
    }

    private fun rememberPositive(k: String, v: SetRefTarget) {
        if (positive.size > MAX_POSITIVE) positive.clear()
        positive[k] = v
    }

    private fun rememberNegative(k: String) {
        if (negative.size > MAX_NEGATIVE) negative.clear()
        negative[k] = true
    }
}
