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
 * Admin-specific Spring beans that complement the shared core auto-configuration from
 * `com.fluxion.core.spring.boot.FluxionCoreAutoConfiguration`.
 *
 * The auto-configuration module already contributes generic pieces such as `FunctionRegistry`,
 * `WorkflowEngine` and `DagExecutor`, which are reused by both the admin console and runtime
 * workers. This class adds admin-only wiring, namely:
 *   - A layered `WorkflowRouter` that wraps the runtime executor with idempotency + distributed
 *     lock routers (when the corresponding SPI beans are present).
 *   - A `DebugService` backed by `WfExecutionSnapshotService`, which simultaneously implements
 *     both `ExecutionLogRepository` and `DefinitionLoader` SPIs for the step-through debugger.
 */
@Configuration
class WorkflowCoreConfiguration {

    /**
     * Build the admin-side workflow router stack.
     *
     * Delegates to the runtime `RuntimeWorkflowRouter` for core execution, then conditionally
     * wraps it (in order) with the idempotency router and distributed-lock router whenever the
     * underlying SPI implementations are present and non-NOOP.
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

    @Bean
    fun debugService(
        workflowEngine: WorkflowEngine,
        snapshotService: WfExecutionSnapshotService
    ): DebugService = DebugService(workflowEngine, snapshotService, snapshotService)
}
