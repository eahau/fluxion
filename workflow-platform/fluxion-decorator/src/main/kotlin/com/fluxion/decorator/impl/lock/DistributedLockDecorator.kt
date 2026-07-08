package com.fluxion.decorator.impl.lock

import com.fluxion.decorator.decorator.NodeDecorator
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.model.decoratorParams
import com.fluxion.decorator.lock.LockContext
import com.fluxion.decorator.lock.lockWithContext
import org.slf4j.*

/**
 * Node-level distributed lock decorator -- serialises concurrent invocations
 * of the same function for a given business key.
 *
 * ### Config (key `lock:distributed` in `WorkflowNode.decoratorParams`)
 *
 * | Key                   | Description                                                              | Default               |
 * |-----------------------|--------------------------------------------------------------------------|-----------------------|
 * | `lockKey`             | Static lock key (highest priority)                                       |                       |
 * | `lockKeyExpression`   | Template with `${path}` placeholders; vars listed below                 |                       |
 * | `waitMillis`          | Max time to wait for the lock (ms)                                       | 0 (fail immediately)  |
 * | `leaseMillis`         | Max lock hold time before auto-expiry (ms)                               | 30000                 |
 * | `retry`               | Max additional acquire attempts after the first failure                 | 0                     |
 * | `retryIntervalMillis` | Sleep between retries                                                    | 100                   |
 * | `sync`                | When `true`, block until the lock is available (caution!)                | false                 |
 * | `failOnLocked`        | When `false`, skip the lock and run the function anyway on contention    | true                  |
 * | `prefix`              | Prepended to the generated business key                                  | `fluxion:lock:`       |
 *
 * `${path}` template variables: `input`, `nodeId`, `nodeName`, `workflowId`,
 * `executionId`, `functionRef`.
 *
 * Resolution priority for the final lock key:
 * `lockKey` > `lockKeyExpression` > `{prefix}{nodeId}`.
 */
class DistributedLockDecorator(
    private val lockProvider: DistributedLockProvider
) : NodeDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "lock:distributed"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> {
        return WorkflowFunction { input ->
            val context = LockContext(
                params = node.decoratorParams(name()),
                appGroup = node.appGroup,
                domain = node.workflowId,
                defaultPrefix = "fluxion:lock:",
                defaultBusinessKey = node.id,
                contextDescription = "node [${node.name}]",
                variables = mapOf(
                    "input" to input.directInput,
                    "nodeId" to node.id,
                    "nodeName" to node.name,
                    "workflowId" to node.workflowId,
                    "executionId" to (input.meta?.executionId ?: ""),
                    "functionRef" to node.functionRef
                )
            )
            lockProvider.lockWithContext(context, log) { function.apply(input) }
        }
    }
}
