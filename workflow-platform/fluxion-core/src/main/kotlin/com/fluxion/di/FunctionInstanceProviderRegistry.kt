package com.fluxion.di

import com.fluxion.core.function.WorkflowFunction

/**
 * [FunctionInstanceProvider] 的全局注册表。
 *
 * 工作流引擎在注册函数组件时，通过此注册表挑选第一个支持目标函数类的提供者，
 * 完成函数实例的创建与依赖注入。
 */
object FunctionInstanceProviderRegistry {

    private val providers = mutableListOf<FunctionInstanceProvider>()

    /**
     * 注册一个函数实例提供者。
     */
    fun register(provider: FunctionInstanceProvider) {
        providers.add(provider)
    }

    /**
     * 获取第一个支持指定函数类的提供者。
     */
    fun getProvider(functionClass: Class<out WorkflowFunction<*>>): FunctionInstanceProvider? {
        return providers.firstOrNull { it.supports(functionClass) }
    }

    /**
     * 清空已注册提供者（主要用于测试）。
     */
    fun clear() {
        providers.clear()
    }
}
