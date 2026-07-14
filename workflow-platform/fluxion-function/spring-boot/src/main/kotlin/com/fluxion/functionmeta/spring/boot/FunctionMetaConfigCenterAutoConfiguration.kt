package com.fluxion.functionmeta.spring.boot

import com.fluxion.core.function.FunctionRegistry
import com.fluxion.functionmeta.api.FunctionMetaConfigCenter
import com.fluxion.functionmeta.api.FunctionMetaConfigCenterSelector
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.env.Environment

/**
 * Selects and wires an active [FunctionMetaConfigCenter] then binds it to the
 * [FunctionRegistry] via [FunctionMetaAutoBinder].
 *
 * Multiple config-center implementations may co-exist on the classpath (Apollo,
 * Nacos, HTTP registry). They are discovered via [ObjectProvider] so even beans
 * declared in other auto-configurations are picked up. Selection is driven by
 * the runtime property `fluxion.function-meta.config-center` (default:
 * `"http"`), delegated to [FunctionMetaConfigCenterSelector].
 *
 * Gated by `fluxion.function-meta.config-center-enabled=true` (default on) so
 * deployments that manage metadata entirely in-process (or via the JDBC
 * registry on Admin) can disable the watcher path.
 *
 * Returns `null` from the bean factory method when no registry / no config
 * center beans are available—Spring gracefully skips downstream wiring instead
 * of failing context startup.
 *
 * @see FunctionMetaConfigCenterSelector the actual priority / name-matching
 *   logic.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "fluxion.function-meta",
    name = ["config-center-enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class FunctionMetaConfigCenterAutoConfiguration {

    @Bean
    fun functionMetaAutoBinder(
        centers: ObjectProvider<FunctionMetaConfigCenter>,
        functionRegistry: ObjectProvider<FunctionRegistry>,
        env: Environment
    ): ApplicationListener<ContextRefreshedEvent> {
        val centerList = centers.stream().toList()
        val registry = functionRegistry.getIfAvailable() ?: return noopListener
        if (centerList.isEmpty()) return noopListener

        val preferred = env.getProperty("fluxion.function-meta.config-center", "http")
        val selector = FunctionMetaConfigCenterSelector(centerList, preferred)
        val activeCenter = selector.active ?: return noopListener

        return FunctionMetaAutoBinder(registry, activeCenter)
    }

    companion object {
        private val noopListener = ApplicationListener<ContextRefreshedEvent> { }
    }
}
