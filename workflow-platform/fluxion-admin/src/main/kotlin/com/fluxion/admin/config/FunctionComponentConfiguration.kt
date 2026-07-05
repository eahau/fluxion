package com.fluxion.admin.config

import com.fluxion.core.function.FunctionComponent
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.value.FunctionMeta
import com.fluxion.di.FunctionInstanceProvider
import org.slf4j.*
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component
import java.util.ServiceLoader

/**
 * 第三方函数自动注册配置
 *
 * 通过 [FunctionComponent] SPI 批量注册第三方函数，支持两种发现方式：
 *   - Spring Bean（@Component / @Bean）
 *   - Java ServiceLoader（META-INF/services，适用于非 Spring 项目）
 *
 * 内置函数由各自的 AutoConfiguration 注册（BuiltinConfig、RedisWorkflowAutoConfiguration 等），
 * 此配置仅处理第三方扩展。
 *
 * 使用 [SmartInitializingSingleton] 确保所有内置函数注册器先执行完成，
 * 避免第三方函数覆盖内置函数。
 */
@Component
class FunctionComponentConfiguration(
    private val registry: FunctionRegistry,
    private val components: List<FunctionComponent>,
    private val instanceProvider: FunctionInstanceProvider? = null
) : SmartInitializingSingleton {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterSingletonsInstantiated() {
        // 合并 Spring Bean + ServiceLoader 发现的组件（按类去重）
        val springClasses = components.map { it::class }.toSet()
        val spiComponents = ServiceLoader.load(FunctionComponent::class.java)
            .filter { it::class !in springClasses }

        (components + spiComponents).forEach { component ->
            component.initialize(emptyMap())

            // 1. 处理已构造好的函数实例（向后兼容）
            component.functions().forEach { function ->
                val meta = function.meta()
                register(meta.name, meta, function, "component [${component.componentName()}]")
            }

            // 2. 处理函数类，由 DI 容器实例化并注入依赖
            component.functionClasses().forEach { functionClass ->
                val function = instanceProvider?.getInstance(functionClass)
                    ?: functionClass.getDeclaredConstructor().newInstance()
                val meta = function.meta()
                register(meta.name, meta, function, "component [${component.componentName()}] class [${functionClass.name}]")
            }
        }
    }

    private fun register(name: String, meta: FunctionMeta, function: WorkflowFunction<*>, source: String) {
        if (registry.contains(name)) {
            log.warn { "Function [$name] from $source skipped — name already registered (builtin functions cannot be overridden)" }
            return
        }
        registry.register(name, meta, function)
        log.info { "Registered function [$name] from $source" }
    }
}
