package com.fluxion.decorator.decorator

import com.fluxion.core.exception.DecoratorNotFoundException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.value.EngineResult
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Workflow-level decorator SPI -- wraps the entire [DagExecutor.execute] call.
 *
 * Unlike [NodeDecorator], which targets an individual function invocation, a
 * `WorkflowDecorator` sees the raw input map, the [WorkflowDefinition] and a
 * suspend `execute` lambda representing the rest of the pipeline. This makes
 * it the right extension point for:
 *
 *  - Cross-node distributed locking (see `WorkflowLockDecorator`).
 *  - DB transaction boundaries spanning the whole DAG.
 *  - Workflow-wide rate limiting.
 *
 * Decorators run in the order declared by [WorkflowDefinition.workflowDecorators].
 */
interface WorkflowDecorator {

    /**
     * Unique decorator identifier used in [WorkflowDefinition.workflowDecorators]
     * and as the key for parameters in [WorkflowDefinition.workflowDecoratorParams].
     */
    fun name(): String

    /**
     * Intercept a workflow execution.
     *
     * Implementations **must** eventually invoke `execute()` exactly once unless
     * they short-circuit the workflow on purpose (rate-limit rejection, lock
     * contention with `failOnLocked`, etc).
     *
     * @param def      the workflow definition being run
     * @param rawInput caller-provided top-level input map
     * @param execute  continuation that runs the DAG (and any remaining workflow
     *                 decorators) and produces the final [EngineResult]
     * @return the final engine result, possibly enriched by this decorator
     */
    suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult
}

/**
 * Registry for [WorkflowDecorator] implementations -- thread-safe, backed by a
 * [ConcurrentHashMap].
 *
 * Spring auto-configuration collects all [WorkflowDecorator] beans in the
 * application context and calls [registerAll] at startup.
 */
class WorkflowDecoratorRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val decorators = ConcurrentHashMap<String, WorkflowDecorator>()

    /** Register a single workflow decorator, overwriting any previous entry. */
    fun register(decorator: WorkflowDecorator) {
        decorators[decorator.name()] = decorator
        log.debug { "Registered workflow decorator: ${decorator.name()}" }
    }

    /** Register a collection of workflow decorators in iteration order. */
    fun registerAll(decoratorList: Collection<WorkflowDecorator>) {
        decoratorList.forEach(::register)
    }

    /**
     * Look up a workflow decorator by name.
     *
     * @throws DecoratorNotFoundException if no decorator with `name` is registered
     */
    fun resolve(name: String): WorkflowDecorator =
        decorators[name] ?: throw DecoratorNotFoundException(name)

    /** Return `true` if a workflow decorator with `name` is currently registered. */
    fun contains(name: String): Boolean = decorators.containsKey(name)

    /** Return a snapshot list of currently registered workflow decorator names. */
    fun listNames(): List<String> = decorators.keys().toList()
}
