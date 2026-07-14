package com.fluxion.core.util

/**
 * Small set of casting / coercion helpers for loosely-typed payloads.
 *
 * The engine passes data between nodes as `Any?` maps; decorators and
 * built-in functions routinely need to pull typed values out of those
 * maps without littering call sites with `@Suppress("UNCHECKED_CAST")`.
 *
 * Variants:
 *  - `castOrNull` / `castOrDefault` — safe `as?` casts that return
 *    null / a default on type mismatch.
 *  - `uncheckedCast` / `uncheckedCastGeneric` — trust-the-caller
 *    variants for call-sites that have already validated the shape
 *    externally (e.g. a built-in function that knows its config map
 *    was produced by the admin console schema-validator).
 */

/**
 * Null-safe `as? T` cast.  Returns `null` when the receiver is `null`
 * or not assignable to `T`.  Works for Strings, Numbers, and flat
 * Map/List structures; nested generics (e.g. `Map<String, Int>`) are
 * NOT reified so callers should pair with [JsonUtil.convertValueOrNull]
 * when recursive coercion is required.
 */
inline fun <reified T> Any?.castOrNull(): T? = this as? T

/** Cast-or-default variant of [castOrNull]. */
inline fun <reified T> Any?.castOrDefault(default: T): T = (this as? T) ?: default

/**
 * Unchecked cast for call-sites that know the shape is correct.
 *
 * Use when: (1) the value came from `JsonUtil.convertValue` with the
 * exact same target, or (2) the admin console schema-validator has
 * already approved the payload shape.  Returns `null` on `null`
 * receiver (same as Kotlin's own `as?`).
 */
inline fun <reified T> Any?.uncheckedCast(): T? = this as? T

/**
 * Non-`reified` variant for generic contexts where the caller cannot
 * inline.  Same semantics as [uncheckedCast] but usable from Java or
 * non-inline helper functions.
 */
@Suppress("UNCHECKED_CAST")
fun <T> Any?.uncheckedCastGeneric(): T? = this as? T
