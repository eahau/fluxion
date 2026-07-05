package com.fluxion.admin.config

import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.admin.service.WfExecutionSnapshotService
import com.fluxion.core.debug.DebugService
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.engine.IdempotencyStore
import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.runtime.core.provider.DefinitionProvider
import com.fluxion.runtime.core.router.DistributedLockRouter
import com.fluxion.runtime.core.router.IdempotencyRouter
import com.fluxion.runtime.core.router.RuntimeWorkflowRouter
import com.fluxion.runtime.core.spi.ExecutionSnapshotStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * workflow-admin 专属的核心组件补充配置。
 *
 * 通用核心组件（FunctionRegistry、WorkflowEngine、DagExecutor 等）已迁移到
 * fluxion-core-spring-boot 的 [com.fluxion.core.spring.boot.FluxionCoreAutoConfiguration]，
 * 供控制面（admin）和运行面（runtime）共用。
 *
 * 此类仅保留 admin 特有的 Bean，例如调试服务。
 */
@Configuration
class WorkflowCoreConfiguration {

    /**
     * Admin 侧工作流路由器。
     *
     * 直接复用运行面的 [RuntimeWorkflowRouter]，
     * 按顺序包装幂等路由器与分布式锁路由器。
     */
    @Bean
    fun workflowRouter(
        definitionProvider: DefinitionProvider,
        dagExecutor: DagExecutor,
        snapshotStore: ExecutionSnapshotStore?,
        @Autowired(required = false) idempotencyStore: IdempotencyStore?,
        @Autowired(required = false) lockProvider: DistributedLockProvider?
    ): WorkflowRouter {
        var router: WorkflowRouter = RuntimeWorkflowRouter(definitionProvider, dagExecutor, snapshotStore)

        if (idempotencyStore != null && idempotencyStore != IdempotencyStore.NOOP) {
            router = IdempotencyRouter(router, idempotencyStore)
        }

        if (lockProvider != null && lockProvider != DistributedLockProvider.NOOP) {
            router = DistributedLockRouter(router, lockProvider)
        }

        return router
    }

    /**
     * 调试服务 Bean
     * 依赖 WfExecutionSnapshotService 同时实现 ExecutionLogRepository 和 DefinitionLoader 两个 SPI
     */
    @Bean
    fun debugService(
        workflowEngine: WorkflowEngine,
        snapshotService: WfExecutionSnapshotService
    ): DebugService = DebugService(workflowEngine, snapshotService, snapshotService)
}
