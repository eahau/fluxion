/**
 * Runtime selector that picks the active [FunctionMetaConfigCenter] among
 * the backends available on the classpath.
 *
 * In a typical deployment more than one config-centre bean may be present
 * (for example an operator includes both the Nacos and HTTP starters for
 * operational flexibility). The `fluxion.meta.config-center` application
 * property provides the `preferred` name; this selector resolves the match
 * or falls back to the first available centre so the app still starts.
 *
 * Logging the resolved [available] list on startup is useful when debugging
 * why an operator's preferred backend was not honoured – the selector itself
 * stays quiet so callers in the auto-configuration layer can log it once.
 */
package com.fluxion.functionmeta.api

/**
 * Resolves the active config centre from an ordered list of candidates and
 * an operator-supplied preferred name.
 *
 * @property centres all config-centre beans discoverable on the classpath
 * @property preferred operator-chosen [FunctionMetaConfigCenter.name]
 */
class FunctionMetaConfigCenterSelector(
    private val centers: List<FunctionMetaConfigCenter>,
    private val preferred: String
) {
    /**
     * The config centre that should actually be wired to downstream
     * consumers. Falls back to the first available entry when the
     * operator's preference cannot be satisfied.
     */
    val active: FunctionMetaConfigCenter?
        get() = centers.firstOrNull { it.name == preferred } ?: centers.firstOrNull()

    /** Human readable list of backend names, used for startup logging. */
    val available: List<String> get() = centers.map { it.name }
}
