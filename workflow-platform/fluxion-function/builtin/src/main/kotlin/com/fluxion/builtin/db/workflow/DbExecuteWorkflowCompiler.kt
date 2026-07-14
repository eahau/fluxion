package com.fluxion.builtin.db.workflow

import com.fluxion.builtin.db.sql.DbExecuteSqlCompiler
import com.fluxion.builtin.db.sql.ParameterSchema
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast

/**
 * Publish-time compiler that runs schema-aware SQL compilation against every
 * `builtin:dbExecute` node in a [WorkflowDefinition].
 *
 * Workflow order:
 * 1. Walk every node, resolve its output schema (by looking up the
 *    referenced function in [FunctionRegistry] and reading `outputSchema`).
 * 2. For each dbExecute node, compute the set of parameters *actually
 *    available* when it runs:
 *    - static literals from the node's own `params` map (incl. nested `params`)
 *    - explicit dependency outputs via `dependsOn`
 *    - last node's output as `directInput` in linear chains (no dependsOn)
 * 3. Call [DbExecuteSqlCompiler.compile] with the parameter list — this runs
 *    static safety + existence + type checks and returns [CompiledSql].
 * 4. Write the compiled result back under `node.params["compiledSql"]` so the
 *    runtime `DbExecuteFunction` can skip compilation entirely and just
 *    validate/render.
 *
 * Compilation is purely additive: nodes that aren't dbExecute or don't carry
 * an `sql` param are copied through unchanged.
 */
object DbExecuteWorkflowCompiler {

    private const val COMPILED_SQL_KEY = "compiledSql"

    /**
     * Compiles every dbExecute node in a workflow definition.
     *
     * @return a NEW WorkflowDefinition with `params` updated in-place on
     *   dbExecute nodes; non-sql nodes pass through unchanged.
     */
    fun compile(definition: WorkflowDefinition, functionRegistry: FunctionRegistry): WorkflowDefinition {
        val nodeOutputSchemas = computeNodeOutputSchemas(definition, functionRegistry)
        val compiledNodes = definition.nodes.map { node ->
            compileNode(node, definition.nodes, nodeOutputSchemas)
        }
        return definition.copy(nodes = compiledNodes)
    }

    private fun compileNode(
        node: WorkflowNode,
        allNodes: List<WorkflowNode>,
        nodeOutputSchemas: Map<String, Map<String, ParameterSchema>>
    ): WorkflowNode {
        if (node.functionRef != "builtin:dbExecute") return node
        val params = node.params ?: return node
        val sql = params["sql"] as? String ?: return node

        val available = buildAvailableParameters(node, allNodes, nodeOutputSchemas)
        val compiledSql = DbExecuteSqlCompiler.compile(sql, available)
        val compiledMap = mutableMapOf<String, Any>("sql" to compiledSql)

        val compensateSql = params["compensateSql"] as? String
        if (!compensateSql.isNullOrBlank()) {
            compiledMap["compensateSql"] = DbExecuteSqlCompiler.compile(compensateSql, available)
        }

        val newParams = params.toMutableMap()
        newParams[COMPILED_SQL_KEY] = compiledMap
        return node.copy(params = newParams)
    }

    /**
     * Builds a map of `nodeId` → `{paramName: ParameterSchema}` for every
     * node in the workflow. Used by the compiler to answer "what fields can
     * this dbExecute node reference at runtime?".
     */
    private fun computeNodeOutputSchemas(
        definition: WorkflowDefinition,
        functionRegistry: FunctionRegistry
    ): Map<String, Map<String, ParameterSchema>> {
        return definition.nodes.associate { node ->
            node.id to extractSchemaProperties(resolveOutputSchema(node.functionRef, functionRegistry))
        }
    }

    private fun resolveOutputSchema(functionRef: String, functionRegistry: FunctionRegistry): Any? {
        // Accept either "builtin:xxx" or unprefixed "xxx" names so the admin
        // UI, which often strips the prefix, still resolves correctly.
        return functionRegistry.getMeta(functionRef)?.outputSchema
            ?: functionRegistry.getMeta(functionRef.removePrefix("builtin:"))?.outputSchema
    }

    /**
     * Flattens a JSON Schema object into a name→type ParameterSchema map.
     *
     * Only handles the common case (`type: object` with `properties` +
     * `required` arrays). For schemas that aren't objects or can't be parsed,
     * an empty map is returned — the runtime still has the final say via
     * schema-agnostic compilation fallback.
     */
    private fun extractSchemaProperties(schema: Any?): Map<String, ParameterSchema> {
        if (schema == null) return emptyMap()
        val json = when (schema) {
            is String -> schema
            else -> runCatching { JsonUtil.serialize(schema) }.getOrNull() ?: return emptyMap()
        }
        val schemaMap = runCatching { JsonUtil.toMap(json) }.getOrNull()
            ?: return emptyMap()
        val properties = schemaMap["properties"].uncheckedCast<Map<String, Map<String, Any>>>() ?: return emptyMap()
        val required = (schemaMap["required"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
        return properties.map { (name, prop) ->
            val type = prop["type"]?.toString()
            ParameterSchema(name, type, required.contains(name))
        }.associateBy { it.name }
    }

    /**
     * Assembles the union of parameters a specific dbExecute node can see,
     * combining static literals, explicit dependsOn outputs, and implicit
     * direct-input from the previous node in linear execution.
     */
    private fun buildAvailableParameters(
        node: WorkflowNode,
        allNodes: List<WorkflowNode>,
        nodeOutputSchemas: Map<String, Map<String, ParameterSchema>>
    ): Map<String, ParameterSchema> {
        val available = mutableMapOf<String, ParameterSchema>()

        // 1. Static literals declared directly on the node.
        node.params?.forEach { (k, v) ->
            if (k == COMPILED_SQL_KEY) return@forEach
            if (k == "params" && v is Map<*, *>) {
                v.uncheckedCast<Map<String, Any?>>()?.forEach { (subK, subV) ->
                    available[subK] = ParameterSchema(subK, inferType(subV), true)
                }
            } else {
                available[k] = ParameterSchema(k, inferType(v), true)
            }
        }

        // 2. Explicit declared dependencies.
        node.dependsOn?.forEach { depId ->
            nodeOutputSchemas[depId]?.let { available.putAll(it) }
        }

        // 3. Implicit "previous node direct input" for linear DAGs.
        if (node.dependsOn.isNullOrEmpty()) {
            val idx = allNodes.indexOf(node)
            if (idx > 0) {
                nodeOutputSchemas[allNodes[idx - 1].id]?.let { available.putAll(it) }
            }
        }

        return available
    }

    private fun inferType(value: Any?): String? = when (value) {
        is String -> "string"
        is Int, is Long, is Short, is Byte -> "integer"
        is Double, is Float -> "number"
        is Boolean -> "boolean"
        is List<*> -> "array"
        is Map<*, *> -> "object"
        else -> null
    }
}
