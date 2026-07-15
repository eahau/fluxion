package com.fluxion.builtin.config

import org.springframework.beans.factory.InitializingBean
import com.fluxion.outbound.mq.spi.MqPublisher
import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.cache.CacheGetFunction
import com.fluxion.builtin.cache.CacheSetFunction
import com.fluxion.builtin.cache.CaffeineCacheStore
import com.fluxion.builtin.db.*
import com.fluxion.builtin.db.dialect.SqlDialect
import com.fluxion.builtin.db.function.DbExecuteFunction
import com.fluxion.builtin.db.transaction.*
import com.fluxion.builtin.flow.ConditionBranchFunction
import com.fluxion.builtin.flow.FilterFunction
import com.fluxion.builtin.flow.LoopAggregatorFunction
import com.fluxion.builtin.http.HttpCallFunction
import com.fluxion.builtin.http.okhttp.OkHttpClientAdapter
import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.builtin.json.*
import com.fluxion.builtin.mq.MqPublishFunction
import com.fluxion.builtin.pagination.PaginateFunction
import com.fluxion.builtin.response.ErrorWrapperFunction
import com.fluxion.builtin.response.ResponseWrapperFunction
import com.fluxion.builtin.signal.WaitForSignalFunction
import com.fluxion.builtin.validation.ParamValidateFunction
import com.fluxion.cache.FluxionCacheFactory
import com.fluxion.decorator.decorator.CacheStore
import com.fluxion.decorator.decorator.DecoratorRegistry
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.util.uncheckedCast
import okhttp3.OkHttpClient
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * Spring Boot auto-configuration for the `fluxion-function:builtin` module.
 *
 * Registers every built-in workflow function as a conditional Spring bean —
 * functions whose dependencies aren't satisfied (e.g. `MqPublishFunction`
 * without an `MqPublisher` bean on the classpath) simply don't materialize,
 * which lets deployments opt in/out per adapter. Beans that DO require
 * external dependencies use `@ConditionalOnBean`; pure-JVM utilities
 * (JSON helpers, response wrappers, flow control) are always registered.
 *
 * Non-function infrastructure beans:
 * - Default [OkHttpClient] + [OkHttpClientAdapter] when OkHttp is present.
 * - [DataSourceProvider] backed by the `ApplicationContext`'s DataSource map,
 *   wrapped with [SpringTransactionAwareDataSourceProxy] for tx participation,
 *   with dialect resolution via [UrlAndMetadataDialectResolver].
 * - [TransactionManager] adapter (only when Spring's
 *   `PlatformTransactionManager` is already wired by the host app).
 * - [CaffeineCacheStore] as the default in-process [CacheStore].
 *
 * After all beans are created, [BuiltinFunctionRegistrar] walks the
 * [BuiltinFunction]-implementing beans and registers them into the
 * global [FunctionRegistry] so the engine can resolve `builtin:*` refs.
 */
@AutoConfiguration
class BuiltinFunctionAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnClass(OkHttpClient::class)
    @ConditionalOnMissingBean(OkHttpClient::class)
    fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(30))
        .build()

    @Bean
    @ConditionalOnClass(OkHttpClient::class)
    @ConditionalOnMissingBean(HttpClientAdapter::class)
    fun okHttpClientAdapter(httpClient: OkHttpClient): HttpClientAdapter = OkHttpClientAdapter(httpClient)

    @Bean
    @ConditionalOnBean(HttpClientAdapter::class)
    fun httpCallFunction(httpClient: HttpClientAdapter) = HttpCallFunction(httpClient)

    @Bean fun convertFunction() = ConvertFunction()
    @Bean fun jsonExtractFunction() = JsonExtractFunction()
    @Bean fun jsonParseFunction() = JsonParseFunction()
    @Bean fun jsonSerializeFunction() = JsonSerializeFunction()
    @Bean fun jsonTransformFunction() = JsonTransformFunction()

    @Bean
    @ConditionalOnMissingBean(DialectResolver::class)
    fun dialectResolver(env: Environment): DialectResolver {
        return UrlAndMetadataDialectResolver { name -> resolveUrlFromEnv(env, name) }
    }

    @Bean
    fun dataSourceProvider(
        dataSources: Map<String, DataSource>,
        resolver: DialectResolver
    ): DataSourceProvider {
        val dialectCache = ConcurrentHashMap<String, SqlDialect>()
        val rawProvider = object : DataSourceProvider {
            override fun getDataSource(name: String): DataSource {
                return dataSources[name]
                    ?: dataSources["dataSource"]
                    ?: dataSources.values.firstOrNull()
                    ?: throw IllegalStateException("No DataSource available")
            }

            override fun getDialect(name: String): SqlDialect {
                val key = name.ifBlank { "__default__" }
                return dialectCache.computeIfAbsent(key) {
                    val ds = getDataSource(name)
                    resolver.resolve(name, ds)
                }
            }

            override fun listDataSourceNames(): List<String> = dataSources.keys.toList()
        }
        return SpringTransactionAwareDataSourceProvider(rawProvider)
    }

    @Bean
    @ConditionalOnBean(PlatformTransactionManager::class)
    fun transactionManager(platformTransactionManager: PlatformTransactionManager): TransactionManager =
        SpringTransactionManager(platformTransactionManager)

    private fun resolveUrlFromEnv(env: Environment, dsName: String): String? {
        if (dsName.isNotBlank() && dsName != "__default__") {
            env.getProperty("spring.datasource.$dsName.url")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return env.getProperty("spring.datasource.url")?.takeIf { it.isNotBlank() }
    }

    @Bean @ConditionalOnBean(DataSourceProvider::class)
    fun dbExecuteFunction(dataSourceProvider: DataSourceProvider) = DbExecuteFunction(dataSourceProvider)

    @Bean @ConditionalOnBean(TransactionManager::class)
    fun transactionDecorator(transactionManager: TransactionManager) =
        TransactionDecorator(transactionManager)

    @Bean @ConditionalOnBean(TransactionManager::class)
    fun workflowTransactionDecorator(transactionManager: TransactionManager) =
        com.fluxion.builtin.db.transaction.WorkflowTransactionDecorator(transactionManager)

    @Bean @ConditionalOnBean(MqPublisher::class)
    fun mqPublishFunction(mqPublisher: MqPublisher) = MqPublishFunction(mqPublisher)

    @Bean fun paramValidateFunction() = ParamValidateFunction()

    @Bean fun responseWrapperFunction() = ResponseWrapperFunction()
    @Bean fun errorWrapperFunction() = ErrorWrapperFunction()

    @Bean
    @ConditionalOnMissingBean(CacheStore::class)
    fun caffeineCacheStore(): CacheStore = CaffeineCacheStore()

    @Bean @ConditionalOnBean(CacheStore::class)
    fun cacheGetFunction(cacheStore: CacheStore) = CacheGetFunction(cacheStore)

    @Bean @ConditionalOnBean(CacheStore::class)
    fun cacheSetFunction(cacheStore: CacheStore) = CacheSetFunction(cacheStore)

    @Bean fun conditionBranchFunction() = ConditionBranchFunction()
    @Bean fun filterFunction() = FilterFunction()
    @Bean fun loopAggregatorFunction() = LoopAggregatorFunction()

    @Bean fun paginateFunction() = PaginateFunction()

    @Bean @ConditionalOnBean(SignalBroker::class)
    fun waitForSignalFunction(signalBroker: SignalBroker) = WaitForSignalFunction(signalBroker)

    @Bean
    fun builtinFunctionRegistrar(
        registry: FunctionRegistry,
        functions: List<BuiltinFunction>
    ) = BuiltinFunctionRegistrar(registry, functions)

    @Bean
    fun builtinTransactionDecoratorRegistrar(
        registry: DecoratorRegistry,
        @Autowired(required = false) transactionDecorator: TransactionDecorator?
    ) = BuiltinTransactionDecoratorRegistrar(
        registry,
        transactionDecorator
    )
}

/**
 * Registers every [BuiltinFunction] Spring bean into the global
 * [FunctionRegistry] at context-refresh time.
 *
 * Runs as a side-effect of constructor execution (Spring instantiates this
 * bean eagerly). Any function whose dependencies weren't met is simply not
 * in the injected list, so the registrar never sees unresolvable entries.
 */
class BuiltinFunctionRegistrar(
    registry: FunctionRegistry,
    functions: List<BuiltinFunction>
) {
    init {
        var count = 0
        functions.forEach { fn ->
            val function = fn.uncheckedCast<WorkflowFunction<*>>()!!
            val name = function.functionName
            registry.register(name, function)
            count++
        }
        log.info { "Registered $count built-in workflow functions" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BuiltinFunctionRegistrar::class.java)
    }
}

/**
 * Registers the optional [TransactionDecorator] node decorator into the
 * global [DecoratorRegistry]. If no transaction manager is present in the
 * host app (no `PlatformTransactionManager` bean) the injected decorator is
 * null and registration becomes a no-op — this lets the same builtin module
 * work both in full JDBC-backed deployments and in lighter serverless-style
 * environments with no database.
 */
class BuiltinTransactionDecoratorRegistrar(
    registry: DecoratorRegistry,
    transactionDecorator: TransactionDecorator?
) {
    init {
        var count = 0
        transactionDecorator?.let { registry.register(it); count++ }
        log.info { "Registered $count transaction decorator" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BuiltinTransactionDecoratorRegistrar::class.java)
    }
}
