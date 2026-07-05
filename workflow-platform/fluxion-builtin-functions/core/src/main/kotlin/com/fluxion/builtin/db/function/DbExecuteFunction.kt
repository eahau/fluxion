package com.fluxion.builtin.db.function

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.db.DataSourceProvider
import com.fluxion.builtin.db.sql.CompiledSql
import com.fluxion.builtin.db.sql.DbExecuteSqlCompiler
import com.fluxion.builtin.db.sql.executeCompiled
import com.fluxion.builtin.db.sql.extractWriteTableName
import com.fluxion.builtin.meta.BuiltinFunctionMetas
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionResult
import com.fluxion.core.value.SideEffect
import org.slf4j.LoggerFactory
import org.slf4j.debug

/** 节点参数中预编译 SQL 的存储键。 */
const val COMPILED_SQL_KEY = "compiledSql"

/**
 * 内置数据库通用执行函数（builtin:dbExecute）。
 * 手写 SQL 的统一入口，自动识别 SELECT/WITH/EXPLAIN/SHOW/DESC 走查询，
 * INSERT/UPDATE/DELETE 走写操作并记录 SideEffect。
 * 查询语句默认最多返回 1000 行，可通过 limit 参数覆盖；<=0 表示不限。
 */
class DbExecuteFunction(private val dataSourceProvider: DataSourceProvider) : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val sql = input.requireParam<String>("sql")
        val compiled = resolveCompiledSql(input) ?: DbExecuteSqlCompiler.compile(sql)
        val params = bindingParams(input)
        val dataSourceName = input.param("dataSource", "default")
        val dataSource = dataSourceProvider.getDataSource(dataSourceName)
        val compensateSql = input.param<String>("compensateSql")
        val compiledCompensate = resolveCompiledCompensateSql(input)
            ?: if (!compensateSql.isNullOrBlank()) DbExecuteSqlCompiler.compile(compensateSql) else null
        val compensateFunctionRef = input.param<String>("compensateFunctionRef")

        log.debug { "DbExecuteFunction executing SQL: $sql" }

        return if (compiled.isQuery) {
            val resultType = input.param("resultType", "list")
            val limit = input.paramAsInt("limit", DEFAULT_DB_QUERY_LIMIT)
            val result = dataSource.executeCompiled(compiled, params) { stmt ->
                if (limit > 0) stmt.maxRows = limit
                stmt.executeQuery().use { rs -> rs.fetchResult(resultType) }
            }
            FunctionResult.success(result)
        } else {
            val result = dataSource.executeCompiled(compiled, params, generatedKeys = compiled.isInsert) { stmt ->
                val affected = stmt.executeUpdate()
                if (compiled.isInsert) stmt.generatedKeyOrAffected(affected) else affected
            }
            val renderedSql = compiled.renderTemplate(params)
            val sideEffect = SideEffect(
                "DB_WRITE",
                compiled.writeTableName ?: extractWriteTableName(renderedSql),
                params,
                compensateFunctionRef
            )
            val effects = mutableListOf(sideEffect)
            if (compiledCompensate != null) {
                effects.add(
                    SideEffect(
                        "DB_WRITE",
                        dataSourceName,
                        mapOf("sql" to compensateSql, "params" to params),
                        "builtin:dbExecute"
                    )
                )
            }
            FunctionResult.successWithEffects(result, effects)
        }
    }

    override fun meta() = BuiltinFunctionMetas.DB_EXECUTE
}

/**
 * 从 nodeParams 中读取发布阶段预编译的 SQL；不存在则返回 null，由运行时兜底编译。
 */
private fun resolveCompiledSql(input: NodeInput): CompiledSql? {
    val compiledMap = input.param<Map<String, Any>>(COMPILED_SQL_KEY) ?: return null
    val sqlMap = compiledMap["sql"].uncheckedCast<Map<String, Any>>() ?: return null
    return JsonUtil.convertValue(sqlMap, CompiledSql::class.java)
}

/**
 * 从 nodeParams 中读取发布阶段预编译的补偿 SQL。
 */
private fun resolveCompiledCompensateSql(input: NodeInput): CompiledSql? {
    val compiledMap = input.param<Map<String, Any>>(COMPILED_SQL_KEY) ?: return null
    val sqlMap = compiledMap["compensateSql"].uncheckedCast<Map<String, Any>>() ?: return null
    return JsonUtil.convertValue(sqlMap, CompiledSql::class.java)
}
