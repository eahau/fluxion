package com.fluxion.builtin.pagination

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult
import kotlin.math.ceil

/**
 * 内置分页封装函数（builtin:paginate）
 */
class PaginateFunction : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    override fun apply(input: NodeInput): FunctionResult<Map<String, Any?>> {
        val page = input.paramAsInt("page", 1)
        val size = input.paramAsInt("size", 20)
        val total = input.paramAsLong("total", 0L)

        val items = (input.directInput as? List<*>) ?: emptyList<Any>()
        val totalPages = ceil(total.toDouble() / size).toInt()

        val result = mapOf(
            "items" to items,
            "page" to page,
            "size" to size,
            "total" to total,
            "totalPages" to totalPages,
            "hasNext" to (page < totalPages),
            "hasPrev" to (page > 1)
        )
        return FunctionResult.success(result)
    }

    override fun meta() = BuiltinFunctionMetas.PAGINATE
}
