package com.fluxion.schema.model

/**
 * Schema 校验错误详情。
 *
 * 结构化的错误信息，便于前端做字段级错误展示和定位。
 *
 * @property path       数据中的字段路径，如 `user.addresses[0].city`
 * @property message    人类可读的错误信息
 * @property errorType  错误类型码，如 `required` / `type` / `format` / `pattern`（可选）
 * @property schemaPath Schema 定义中的路径（可选）
 */
data class ValidationError(
    val path: String,
    val message: String,
    val errorType: String? = null,
    val schemaPath: String? = null
)

/**
 * Schema 校验结果。
 *
 * @property valid    校验是否通过
 * @property errors   错误消息列表（纯文本，向后兼容）
 * @property details  结构化错误详情（新增，便于前端定位展示）
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val details: List<ValidationError> = emptyList()
) {
    companion object {
        @JvmStatic
        fun ok() = ValidationResult(true, emptyList(), emptyList())

        /**
         * 从文本错误列表创建失败结果（向后兼容）。
         */
        @JvmStatic
        fun fail(errors: List<String>) = ValidationResult(
            false,
            errors.toList(),
            errors.map { ValidationError(path = "", message = it) }
        )

        /**
         * 从结构化错误列表创建失败结果。
         */
        @JvmStatic
        fun failDetailed(details: List<ValidationError>) = ValidationResult(
            false,
            details.map { it.message },
            details
        )
    }
}
