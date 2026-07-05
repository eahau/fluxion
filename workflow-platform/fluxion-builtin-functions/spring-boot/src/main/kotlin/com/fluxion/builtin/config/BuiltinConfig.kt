package com.fluxion.builtin.config

import com.fluxion.adapter.spi.mq.MqPublisher
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
import com.fluxion.core.decorator.CacheStore
import com.fluxion.core.decorator.DecoratorRegistry
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.util.uncheckedCast
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.slf4j.info
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
 * 内置函数自动配置
 */
@AutoConfiguration
class BuiltinFunctionAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    // ── HTTP 适配器（SPI）────────────────────────────────────────────

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

    // ── JSON 函数（始终注册）────────────────────────────────────────

    @Bean fun convertFunction() = ConvertFunction()
    @Bean fun jsonExtractFunction() = JsonExtractFunction()
    @Bean fun jsonParseFunction() = JsonParseFunction()
    @Bean fun jsonSerializeFunction() = JsonSerializeFunction()
    @Bean fun jsonTransformFunction() = JsonTransformFunction()

    // ── DB 函数────────────────────────────────────────────────────

    /**
     * Spring Boot 方言解析器：从 Environment 读取 JDBC URL，失败时回退连接元数据。
     */
    @Bean
    @ConditionalOnMissingBean(DialectResolver::class)
    fun dialectResolver(env: Environment): DialectResolver {
        return UrlAndMetadataDialectResolver { name -> resolveUrlFromEnv(env, name) }
    }

    /**
     * 数据源提供者。
     *
     * 使用 Spring 的 [org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy]
     * 包装真实数据源，使 DB 函数自动复用 Spring 事务同步器绑定的连接。
     */
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
        }
        return SpringTransactionAwareDataSourceProvider(rawProvider)
    }

    /**
     * 事务管理器：仅在有 Spring [PlatformTransactionManager] 时启用。
     *
     * 将 `tx` 装饰器的事务语义委托给 Spring 事务框架，
     * 从而与 @Transactional、Hibernate、MyBatis 等共享同一套事务生态。
     */
    @Bean
    @ConditionalOnBean(PlatformTransactionManager::class)
    fun transactionManager(platformTransactionManager: PlatformTransactionManager): TransactionManager =
        SpringTransactionManager(platformTransactionManager)

    /**
     * 从 Spring Environment 中解析 JDBC URL。
     *
     * 查找顺序：
     * 1. spring.datasource.{dsName}.url — 具名数据源
     * 2. spring.datasource.url           — 默认数据源
     */
    private fun resolveUrlFromEnv(env: Environment, dsName: String): String? {
        if (dsName.isNotBlank() && dsName != "__default__") {
            env.getProperty("spring.datasource.$dsName.url")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return env.getProperty("spring.datasource.url")?.takeIf { it.isNotBlank() }
    }

    @Bean @ConditionalOnBean(DataSourceProvider::class)
    fun dbExecuteFunction(dataSourceProvider: DataSourceProvider) = DbExecuteFunction(dataSourceProvider)

    // ── 事务装饰器─────────────────────────────────────────────────

    @Bean @ConditionalOnBean(TransactionManager::class)
    fun transactionDecorator(transactionManager: TransactionManager) =
        TransactionDecorator(transactionManager)

    @Bean @ConditionalOnBean(TransactionManager::class)
    fun workflowTransactionDecorator(transactionManager: TransactionManager) =
        com.fluxion.builtin.db.transaction.WorkflowTransactionDecorator(transactionManager)

    // ── MQ 函数────────────────────────────────────────────────────

    @Bean @ConditionalOnBean(MqPublisher::class)
    fun mqPublishFunction(mqPublisher: MqPublisher) = MqPublishFunction(mqPublisher)

    // ── Validation 函数────────────────────────────────────────────
    @Bean fun paramValidateFunction() = ParamValidateFunction()

    // ── Response 函数（始终注册）──────────────────────────────────
    @Bean fun responseWrapperFunction() = ResponseWrapperFunction()
    @Bean fun errorWrapperFunction() = ErrorWrapperFunction()

    // ── Cache 函数─────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(CacheStore::class)
    fun caffeineCacheStore(): CacheStore = CaffeineCacheStore()

    @Bean @ConditionalOnBean(CacheStore::class)
    fun cacheGetFunction(cacheStore: CacheStore) = CacheGetFunction(cacheStore)

    @Bean @ConditionalOnBean(CacheStore::class)
    fun cacheSetFunction(cacheStore: CacheStore) = CacheSetFunction(cacheStore)

    // ── Flow 函数（始终注册）──────────────────────────────────────
    @Bean fun conditionBranchFunction() = ConditionBranchFunction()
    @Bean fun filterFunction() = FilterFunction()
    @Bean fun loopAggregatorFunction() = LoopAggregatorFunction()

    // ── Pagination 函数（始终注册）────────────────────────────────
    @Bean fun paginateFunction() = PaginateFunction()

    // ── Signal 函数（SignalBroker 存在时注册）────────────────────────
    @Bean @ConditionalOnBean(SignalBroker::class)
    fun waitForSignalFunction(signalBroker: SignalBroker) = WaitForSignalFunction(signalBroker)

    // ── 注册器────────────────────────────────────────────────────

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
 * 内置函数注册器
 */
class BuiltinFunctionRegistrar(
    registry: FunctionRegistry,
    functions: List<BuiltinFunction>
) {
    init {
        var count = 0
        functions.forEach { fn ->
            val function = fn.uncheckedCast<WorkflowFunction<*>>()!!
            val meta = function.meta()
            registry.register(meta.name, meta, function)
            count++
        }
        log.info { "Registered $count built-in workflow functions" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BuiltinFunctionRegistrar::class.java)
    }
}

/**
 * 事务装饰器注册器
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
