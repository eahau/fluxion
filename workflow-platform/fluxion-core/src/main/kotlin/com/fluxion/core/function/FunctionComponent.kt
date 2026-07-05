package com.fluxion.core.function

/**
 * 函数组件 SPI — 第三方开发者扩展工作流函数的入口
 *
 * 第三方模块只需要：
 *   1. 依赖 workflow-core（零框架依赖）
 *   2. 实现 [FunctionComponent]
 *   3. 通过以下任一方式注册：
 *      - **Spring 项目**：将实现类作为 Spring Bean 暴露（@Component / @Bean）
 *      - **非 Spring 项目**：在 jar 中放置 SPI 描述文件
 *        `META-INF/services/com.fluxion.core.function.FunctionComponent`
 *
 * workflow-admin 启动时会自动发现 Spring Bean 和 ServiceLoader 两种方式注册的组件。
 * 函数名和元信息统一由 [WorkflowFunction.meta] 提供，无需额外声明。
 */
interface FunctionComponent {

    /** 组件名称，用于日志和调试 */
    fun componentName(): String

    /**
     * 组件提供的函数列表
     *
     * 每个函数的名称、Schema 等元信息通过 [WorkflowFunction.meta] 获取。
     */
    fun functions(): List<WorkflowFunction<*>> = emptyList()

    /**
     * 组件提供的函数类列表。
     *
     * 与 [functions] 不同，这里只声明函数类，由 [com.fluxion.di.FunctionInstanceProvider]
     * 在注册阶段实例化并完成依赖注入。适用于需要 Spring / Guice 等 DI 容器注入依赖的自定义函数。
     */
    fun functionClasses(): List<Class<out WorkflowFunction<*>>> = emptyList()

    /**
     * 可选初始化钩子
     * 注册前调用，可读取配置做延迟初始化
     */
    fun initialize(config: Map<String, Any> = emptyMap()) {}
}
