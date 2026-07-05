package com.fluxion.builtin

/**
 * 内置函数标记接口。
 *
 * 实现该接口的 [com.fluxion.core.function.WorkflowFunction] 将由 [com.fluxion.builtin.config.BuiltinFunctionAutoConfiguration]
 * 自动收集并注册到 [com.fluxion.core.function.FunctionRegistry]。
 *
 * 说明：该接口不继承 [com.fluxion.core.function.WorkflowFunction]，因为各内置函数的输出类型签名不同
 *（如 [com.fluxion.core.value.FunctionResult]<String>、[FunctionResult]<Map<...>> 等），保持为纯标记可避免
 * 破坏现有函数的类型声明。
 */
interface BuiltinFunction
