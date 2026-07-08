package com.fluxion.runtime.core.provider

import com.fluxion.core.model.WorkflowDefinition

/**
 * Read-only view of published workflow definitions — minimal abstraction
 * consumed by adapter-layer routers to locate a definition before execution.
 *
 * Design goal: this SPI lives in `fluxion-runtime-core` and only depends on
 * `fluxion-core` types. Concrete implementations are supplied by the
 * integration layer:
 * - Worker (data plane): [ConfigBackedDefinitionProvider] backed by Apollo/Nacos
 * - Admin (control plane): direct DB-backed implementation
 * - Tests/standalone: in-memory map
 *
 * Two lookup directions are intentionally supported:
 * - [get]: fetch a parsed definition by its primary key `workflowId`
 * - [findByBinding]: reverse index from protocol+bindKey to `workflowId`,
 *   enabling routing decisions without loading full DAGs.
 */
interface DefinitionProvider {

    /**
     * Fetch a fully-parsed workflow definition by its `workflowId`.
     *
     * @param key workflowId
     * @return Parsed definition, or null if the id is unknown / unpublished
     */
    fun get(key: String): WorkflowDefinition?

    /**
     * Reverse-index lookup: find the `workflowId` bound to a given
     * protocol-type / bind-key pair.
     *
     * @param protocol Upper-case protocol family ("HTTP", "KAFKA", "DUBBO", ...)
     * @param bindKey  Protocol-specific route key (HTTP path, Kafka topic, Dubbo serviceKey)
     * @return workflowId that matches the binding, or null if nothing is bound
     */
    fun findByBinding(protocol: String, bindKey: String): String?
}
