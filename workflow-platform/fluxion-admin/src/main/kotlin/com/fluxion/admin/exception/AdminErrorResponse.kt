package com.fluxion.admin.exception

/**
 * Admin 后台统一错误响应体。
 *
 * 后端所有未处理的异常最终都会被 [GlobalExceptionHandler] 捕获并转换为该结构返回，
 * 前端据此展示统一、可定位的错误提示。
 *
 * @property code 错误码，用于程序判断与问题定位
 * @property message 面向用户的错误描述
 * @property timestamp 错误发生时间戳（毫秒）
 */
data class AdminErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
