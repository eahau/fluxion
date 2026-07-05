package com.fluxion.runtime.spring.boot.store

import com.fluxion.core.value.EngineResult
import com.fluxion.runtime.core.spi.ExecutionSnapshotStore
import org.slf4j.*

/**
 * 默认执行快照存储实现：仅输出结构化日志，不依赖任何外部存储。
 *
 * 适合 Runtime 作为无状态 sidecar 部署的场景。
 */
class LoggingExecutionSnapshotStore : ExecutionSnapshotStore {

    private val log = LoggerFactory.getLogger("WorkflowExecutionSnapshot")

    override fun save(result: EngineResult, workflowId: String, version: Int) {
        if (result.success) {
            log.info {
                "Snapshot saved workflowId=$workflowId version=$version " +
                    "executionId=${result.executionId}"
            }
        } else {
            log.warn {
                "Snapshot saved workflowId=$workflowId version=$version " +
                    "executionId=${result.executionId} error=${result.errorMsg}"
            }
        }
    }
}
