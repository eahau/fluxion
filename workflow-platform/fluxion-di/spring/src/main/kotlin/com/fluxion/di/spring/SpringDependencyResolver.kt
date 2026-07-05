package com.fluxion.di.spring

import com.fluxion.di.DependencyResolver
import org.slf4j.*
import org.springframework.context.ApplicationContext

/**
 * Spring 依赖解析器
 *
 * 脚本函数通过此解析器按名称或类型从 Spring [ApplicationContext] 获取 Bean。
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
