package com.fluxion.di

/**
 * 依赖解析器 —— 脚本函数访问 DI 容器的入口
 *
 * Groovy / JS 等脚本函数通过此 SPI 按名称或类型获取已注入的 Bean/服务实例，
 * 无需关心底层是 Spring、Guice 还是其他 IoC 容器。
 */
interface DependencyResolver {

    /**
     * 按名称解析依赖实例。
     *
     * @param name 依赖在 DI 容器中的标识（如 Spring Bean name）
     * @return 依赖实例，不存在时返回 null
     */
    fun resolve(name: String): Any?

    /**
     * 按类型解析依赖实例。
     *
     * @param type 依赖类型
     * @return 依赖实例，不存在时返回 null
     */
    fun <T> resolveByType(type: Class<T>): T?
}
