package com.fluxion.di.spring

import com.fluxion.di.DependencyResolver
import org.slf4j.*
import org.springframework.context.ApplicationContext

/**
 * Spring-backed [DependencyResolver] used by Groovy / JS scripting functions.
 *
 * Both lookup strategies:
 *  - By name → `applicationContext.getBean(name)`.
 *  - By type → `applicationContext.getBeansOfType(type)`.
 *
 * In both cases an unambiguous matches are logged at DEBUG level (not failed; ambiguous
 * and ambiguous matches resolve to the first one with a WARN so scripting code
 * doesn't break at runtime.
 */
class SpringDependencyResolver(
    private val applicationContext: ApplicationContext
) : DependencyResolver {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun resolve(name: String): Any? {
        return if (applicationContext.containsBean(name)) {
            applicationContext.getBean(name)
        } else {
            log.debug { "Spring bean [$name] not found" }
            null
        }
    }

    override fun <T> resolveByType(type: Class<T>): T? {
        val beans = applicationContext.getBeansOfType(type)
        return when {
            beans.isEmpty() -> {
                log.debug { "No Spring bean of type [${type.name}] found" }
                null
            }
            beans.size > 1 -> {
                log.warn { "Multiple Spring beans of type [${type.name}] found: ${beans.keys}, returning first one" }
                beans.values.first()
            }
            else -> beans.values.first()
        }
    }
}
