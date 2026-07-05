package com.fluxion.core.enums

/** 节点执行状态（执行记录用） */
enum class NodeStatus {
    SUCCESS,   // 正常完成
    FAILED,    // 失败（错误策略=FAIL 或重试耗尽）
    SKIPPED,   // 跳过（错误策略=SKIP）
    FALLBACK,  // 降级成功（错误策略=FALLBACK）
    RETRY      // 重试中（中间状态，最终为 SUCCESS 或 FAILED）
}
