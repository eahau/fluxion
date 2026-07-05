package com.fluxion.schema.exception

/**
 * Schema 严格校验失败时抛出的异常。
 *
 * @property errors 校验错误信息列表
 */
class SchemaValidationException(
    message: String,
    val errors: List<String>
) : RuntimeException(message)
