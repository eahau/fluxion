package com.fluxion.decorator.lock

import com.fluxion.decorator.decorator.WorkflowDecorator
import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.decoratorParams
import com.fluxion.core.value.EngineResult
import org.slf4j.*

/**
 * Workflow-level distributed lock decorator -- guarantees that at most one
 * execution of a workflow (per business key) is in flight at any time.
 *
 * Use this for workflows that need cross-node consistency, e.g. inventory
 * decrement followed by order state transitions.
 *
 * ### Config (key `workflow:lock` in `WorkflowDefinition.workflowDecoratorParams`)
 *
 * Same parameter schema as [DistributedLockDecorator] except:
 *  - default `prefix` is `fluxion:lock:workflow:`.
 *  - default business key is the workflow id.
 *  - template variables are `input` and `workflowId`.
 *
 * Resolution priority: `lockKey` > `lockKeyExpression` > `{prefix}{workflowId}`.
 */
class WorkflowLockDecorator(
    private val lockProvider: DistributedLockProvider
) : WorkflowDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "workflow:lock"

    override suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult {
        val context = LockContext(
            params = def.decoratorParams(name()),
            appGroup = def.appGroup,
            domain = def.id,
            defaultPrefix = "fluxion:lock:workflow:",
            defaultBusinessKey = def.id,
            contextDescription = "workflow [${def.id}]",
            variables = mapOf(
                "input" to rawInput,
                "workflowId" to def.id
            )
        )
        return lockProvider.lockSuspendingWithContext(context, log, execute)
    }
}
