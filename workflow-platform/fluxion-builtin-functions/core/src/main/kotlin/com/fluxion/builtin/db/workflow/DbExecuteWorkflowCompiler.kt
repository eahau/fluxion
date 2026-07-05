package com.fluxion.builtin.db.workflow

import com.fluxion.builtin.db.sql.DbExecuteSqlCompiler
import com.fluxion.builtin.db.sql.ParameterSchema
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast

/**
 * 基于上游节点输出 schema 对 dbExecute 节点做预编译。
 *
 * 在工作流发布阶段调用：
 * 1. 根据每个节点的 functionRef 从 FunctionRegistry 获取 outputSchema；
 * 2. 对每个 dbExecute 节点，汇总当前可用的参数 schema：
 *    - 当前节点 nodeParams 中的静态常量（含嵌套 params）；
 *    - 显式依赖（dependsOn）节点的输出属性；
 *    - 线性流中上一节点的输出属性（directInput）。
 * 3. 调用 [DbExecuteSqlCompiler.compile] 做安全校验 + 参数存在性/类型校验；
 * 4. 将编译结果写回节点参数的 `compiledSql` 字段，供运行时直接复用。
 */
object DbExecuteWorkflowCompiler {

    private const val COMPILED_SQL_KEY = "compiledSql"

    /**
     * 编译整个工作流定义中的 dbExecute 节点。
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
     * 计算每个节点的输出属性 schema（按节点 ID 索引）。
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
        return functionRegistry.getMeta(functionRef)?.outputSchema
            ?: functionRegistry.getMeta(functionRef.removePrefix("builtin:"))?.outputSchema
    }

    /**
     * 从 JSON Schema 中提取对象属性名及类型。
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
     * 汇总当前 dbExecute 节点可用的参数 schema。
     */
    private fun buildAvailableParameters(
        node: WorkflowNode,
        allNodes: List<WorkflowNode>,
        nodeOutputSchemas: Map<String, Map<String, ParameterSchema>>
    ): Map<String, ParameterSchema> {
        val available = mutableMapOf<String, ParameterSchema>()

        // 1. 当前节点 nodeParams 中的静态常量（含嵌套 params）
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

        // 2. 显式依赖节点的输出属性
        node.dependsOn?.forEach { depId ->
            nodeOutputSchemas[depId]?.let { available.putAll(it) }
        }

        // 3. 线性流：上一节点的输出作为 directInput
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
