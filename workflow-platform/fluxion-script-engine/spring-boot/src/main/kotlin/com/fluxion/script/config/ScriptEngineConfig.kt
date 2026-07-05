package com.fluxion.script.config

import com.fluxion.adapter.spi.config.FunctionConfigSubscriber
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
import com.fluxion.di.DependencyResolver
import com.fluxion.script.groovy.GroovyScriptFunction
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * 脚本引擎自动配置
 */
@AutoConfiguration
class ScriptEngineAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnClass(name = ["groovy.lang.GroovyShell"])
    fun groovyScriptFunction(
        @Autowired(required = false) dependencyResolver: DependencyResolver?
    ): GroovyScriptFunction {
        log.info { "Registering Groovy script engine (builtin:groovyScript)" }
        return GroovyScriptFunction().apply {
            this.dependencyResolver = dependencyResolver
        }
    }

    @Bean
    fun scriptEngineRegistrar(
        registry: FunctionRegistry,
        @Autowired(required = false) groovyScriptFunction: GroovyScriptFunction?
    ): ScriptEngineRegistrar = ScriptEngineRegistrar(registry, groovyScriptFunction)
}

/**
 * 脚本引擎函数注册器
 */
class ScriptEngineRegistrar(
    registry: FunctionRegistry,
    groovyScriptFunction: GroovyScriptFunction?
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        var count = 0
        groovyScriptFunction?.let {
            registry.register("builtin:groovyScript", it.meta(), it)
            count++
        }
        log.info { "Registered $count script engine functions" }
    }
}

/**
 * Worker 侧函数配置应用器自动装配。
 *
 * 当当前实例角色为 worker 且存在 FunctionConfigSubscriber Bean 时，
 * 启动时从配置中心拉取函数配置并注册到 FunctionRegistry。
 */
@Configuration
@ConditionalOnProperty(name = ["workflow.instance.role"], havingValue = "worker")
@ConditionalOnBean(FunctionConfigSubscriber::class)
class FunctionConfigApplierConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun functionConfigApplier(
        subscriber: FunctionConfigSubscriber,
        registry: FunctionRegistry,
        @Autowired(required = false) groovyScriptFunction: GroovyScriptFunction?,
        @Autowired(required = false) transportRegistry: ExternalFunctionTransportRegistry?,
        env: Environment
    ): FunctionConfigApplier {
        val appGroup = env.getProperty("workflow.instance.app-group")

        // 为脚本引擎设置 scriptRef 解析器，使其能从配置中心读取脚本内容
        val resolver: (String) -> String? = { ref -> subscriber.get(ref)?.scriptBody }
        groovyScriptFunction?.scriptRefResolver = resolver

        val applier = FunctionConfigApplier(
            subscriber = subscriber,
            registry = registry,
            groovyEngine = groovyScriptFunction,
            appGroup = appGroup,
            transportRegistry = transportRegistry ?: ExternalFunctionTransportRegistry()
        )
        applier.init()
        log.info { "FunctionConfigApplier initialized for appGroup=$appGroup" }
        return applier
    }
}
