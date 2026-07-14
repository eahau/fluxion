package com.fluxion.builtin.pagination

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult
import kotlin.math.ceil

/**
 * Built-in pagination envelope helper (`builtin:paginate`).
 *
 * Wraps a raw result list together with paging metadata into the standard
 * `{items, page, size, total, totalPages, hasNext, hasPrev}` envelope used
 * by most REST list endpoints. Placed as the terminal node after a
 * `builtin:dbExecute` (or any list-producing function) it produces a
 * uniform shape regardless of the upstream data source.
 *
 * Node params (all optional — sensible defaults match typical REST APIs):
 * - `page`  — 1-based page number, default `1`.
 * - `size`  — items per page, default `20`.
 * - `total` — total matching rows in the backing store, default `0`.
 *             Usually filled in by a preceding `SELECT count(*)` call or
 *             by the DB driver's own found-rows metadata.
 *
 * The actual `items` list is ALWAYS taken from `directInput` (the upstream
 * node's output) because the page/size/total are just metadata — the real
 * data has already been sliced by the query itself.
 */
class PaginateFunction : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    override val functionName: String = "builtin:paginate"

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
}
