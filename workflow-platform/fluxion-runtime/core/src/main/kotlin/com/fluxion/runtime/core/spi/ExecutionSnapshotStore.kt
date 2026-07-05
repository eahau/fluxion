package com.fluxion.runtime.core.spi

import com.fluxion.core.value.EngineResult

/**
 * 执行快照持久化 SPI。
 *
 * Runtime 侧不强制要求 DB，实现可以是：
 * - 仅日志输出
 * - 写入本地文件
 * - 写入集中式存储（Admin DB / Redis / S3 等）
 */
interface ExecutionSnapshotStore {

    /**
     * 保存一次工作流执行结果。
     *
     * @param result 引擎执行结果
     * @param workflowId 工作流 ID
     * @param version 工作流版本号
     */
    fun save(result: EngineResult, workflowId: String, version: Int)
}
