package com.fluxion.core.enums

/**
 * Classification of workflow nodes used by
 * [com.fluxion.core.engine.WorkflowEngine] to route execution to the correct
 * component (builtin registry, script engine, external gateway or control
 * executor).
 */
enum class NodeType {
    /** Built-in function shipped with Fluxion (registered via `builtin:*` refs). */
    BUILTIN,
    /** User-defined Kotlin/Java function registered via FunctionRegistry. */
    CUSTOM,
    /** Script-based function (Groovy / JS) loaded from `wf_function`. */
    SCRIPT,
    /** Remote call through the Function Gateway (gRPC / HTTP / Dubbo). */
    EXTERNAL,
    /** Loop control node — drives iterative execution over a collection. */
    LOOP,
    /** Wait (signal) node — suspends execution until an external signal arrives. */
    WAIT,
    /** Sub-workflow node — delegates to another [WorkflowDefinition] tree. */
    SUB_WORKFLOW
}
