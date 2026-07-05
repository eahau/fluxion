package com.fluxion.core.enums

/** 函数执行结果状态 */
enum class FunctionStatus {
    /** 正常返回输出 */
    SUCCESS,
    /** 函数执行失败（抛出异常） */
    FAILED,
    /** 函数主动跳过（如前置条件不满足） */
    SKIPPED
}
