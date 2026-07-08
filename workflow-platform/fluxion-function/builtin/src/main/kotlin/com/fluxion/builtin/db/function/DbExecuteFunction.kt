package com.fluxion.builtin.db.function

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.db.DataSourceProvider
import com.fluxion.builtin.db.sql.CompiledSql
import com.fluxion.builtin.db.sql.DbExecuteSqlCompiler
import com.fluxion.builtin.db.sql.executeCompiled
import com.fluxion.builtin.db.sql.extractWriteTableName
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionResult
import com.fluxion.core.value.SideEffect
import org.slf4j.*
/** Node-param key under which publish-time compiled SQL is stored. */
const val COMPILED_SQL_KEY = "compiledSql"

/**
 * Built-in generic DB executor (`builtin:dbExecute`).
 *
 * Unified entry point for hand-authored SQL:
 * - **Read** statements (SELECT / WITH / EXPLAIN / SHOW / DESCRIBE) go through
 *   executeQuery; result is shaped via `resultType` param (`list` default,
 *   `one`, `count`) and capped at [DEFAULT_DB_QUERY_LIMIT] rows (override
 *   via `limit`, 0 = unlimited).
 * - **Write** statements (INSERT / UPDATE / DELETE) go through executeUpdate.
 *   For INSERTs the generated primary key is returned, otherwise the count of
 *   affected rows. A [SideEffect] with the write target-table and bind-values
 *   is emitted for Saga / audit tracking, plus an optional compensation
 *   side-effect for reverse operations.
 *
 * Compensation path: if the admin configured `compensateSql` or
 * `compensateFunctionRef` on the node, the corresponding compensation
 * `SideEffect` is attached to the successful result so the engine can replay
 * it during rollback / compensate phases.
 *
 * @param dataSourceProvider pluggable DS resolver (Spring-aware wrapper or raw).
 */
class DbExecuteFunction(private val dataSourceProvider: DataSourceProvider) : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:dbExecute"

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
}

/**
 * Reads the publish-time precompiled SQL from node params (serialized as a
 * nested Jackson Map). Returns null when the admin bypassed compilation or
 * the node was authored before compilation was introduced; in that case the
 * caller falls back to on-the-fly [DbExecuteSqlCompiler.compile].
 */
private fun resolveCompiledSql(input: NodeInput): CompiledSql? {
    val compiledMap = input.param<Map<String, Any>>(COMPILED_SQL_KEY) ?: return null
    val sqlMap = compiledMap["sql"].uncheckedCast<Map<String, Any>>() ?: return null
    return JsonUtil.convertValue(sqlMap, CompiledSql::class.java)
}

/** Same as [resolveCompiledSql] but for the compensation SQL branch. */
private fun resolveCompiledCompensateSql(input: NodeInput): CompiledSql? {
    val compiledMap = input.param<Map<String, Any>>(COMPILED_SQL_KEY) ?: return null
    val sqlMap = compiledMap["compensateSql"].uncheckedCast<Map<String, Any>>() ?: return null
    return JsonUtil.convertValue(sqlMap, CompiledSql::class.java)
}
