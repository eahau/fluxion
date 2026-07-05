package com.fluxion.builtin.response

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult

/**
 * 内置响应封装函数（builtin:responseWrapper）
 */
class ResponseWrapperFunction : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    override fun meta() = BuiltinFunctionMetas.RESPONSE_WRAPPER

    override fun apply(input: NodeInput): FunctionResult<Map<String, Any?>> {
        val code = input.paramAsInt("code", 200)
        val message = input.param("message", "success")
        // 优先从节点参数读取 data，未配置时回退到 directInput（上游输出）
        val data = input.param<Any>("data") ?: input.directInput ?: emptyMap<String, Any>()

        val response = mapOf(
            "code" to code,
            "message" to message,
            "data" to data
        )
        return FunctionResult.success(response)
    }
}

/**
 * 内置错误响应封装函数（builtin:errorWrapper）
 */
class ErrorWrapperFunction : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    override fun meta() = BuiltinFunctionMetas.ERROR_WRAPPER

    override fun apply(input: NodeInput): FunctionResult<Map<String, Any?>> {
        val code = input.paramAsInt("code", 500)
        // message 优先使用节点参数，未配置时从 directInput 提取错误信息
        val message = input.param<String>("message") ?: extractErrorMessage(input.directInput)
        // data 优先从节点参数读取，未配置时回退到 directInput
        val data = input.param<Any>("data") ?: input.directInput ?: emptyMap<String, Any>()

        val response = mapOf<String, Any?>(
            "code" to code,
            "message" to message,
            "data" to data
        )
        return FunctionResult.success(response)
    }

    private fun extractErrorMessage(input: Any?): String = when {
        input == null -> "Unknown error"
        input is Throwable -> input.message ?: input.javaClass.simpleName
        input is Map<*, *> && input.containsKey("message") -> input["message"].toString()
        else -> input.toString()
    }
}
