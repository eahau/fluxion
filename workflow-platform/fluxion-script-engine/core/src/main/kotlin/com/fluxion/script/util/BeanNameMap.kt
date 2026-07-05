package com.fluxion.script.util

import com.fluxion.di.DependencyResolver

/**
 * Bean 名称代理 Map。
 *
 * 脚本中可通过 `beans.userService` 或 `beans['userService']` 访问 DI 容器中的实例，
 * 实际访问会委托给 [DependencyResolver.resolve]。
 */
class BeanNameMap(
    private val resolver: DependencyResolver
) : AbstractMap<String, Any?>() {

    override fun get(key: String): Any? = resolver.resolve(key)

    override val entries: Set<Map.Entry<String, Any?>>
        get() = throw UnsupportedOperationException("beans is read-only proxy")

    override fun containsKey(key: String): Boolean = resolver.resolve(key) != null
}
