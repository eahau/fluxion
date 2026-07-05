package com.fluxion.di

import com.fluxion.core.function.WorkflowFunction

/**
 * 函数实例提供者 —— DI 容器适配器 SPI
 *
 * 负责将 [WorkflowFunction] 的实现类实例化，并从底层 DI 容器（Spring、Guice 等）
 * 注入其依赖。实现类只需声明自己支持的函数类型，[FunctionInstanceProviderRegistry]
 * 会按顺序挑选第一个 `supports()` 返回 true 的提供者。
 */
interface FunctionInstanceProvider {

    /**
     * 判断当前提供者能否实例化指定的函数类。
     */
    fun supports(functionClass: Class<*>): Boolean

    /**
     * 实例化函数类并注入依赖。
     *
     * 实现应保证返回的实例已完成 DI 注入；若无法创建，应抛出异常。
     */
    fun getInstance(functionClass: Class<out WorkflowFunction<*>>): WorkflowFunction<*>
}
