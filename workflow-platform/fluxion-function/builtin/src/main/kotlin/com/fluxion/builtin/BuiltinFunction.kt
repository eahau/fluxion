package com.fluxion.builtin

/**
 * Marker interface for built-in workflow functions.
 *
 * Any [com.fluxion.core.function.WorkflowFunction] implementing this interface
 * is auto-discovered and registered into the global
 * [com.fluxion.core.function.FunctionRegistry] by
 * `com.fluxion.builtin.config.BuiltinFunctionAutoConfiguration` on startup.
 *
 * Intentionally NOT a subtype of WorkflowFunction: individual built-ins carry
 * distinct result-type signatures (e.g.
 * `FunctionResult<String>` vs `FunctionResult<Map<...>>`) and keeping this a
 * pure marker avoids widening those generic declarations.
 */
interface BuiltinFunction
