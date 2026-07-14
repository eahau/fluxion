package com.fluxion.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Fluxion Admin Spring Boot application entry point.
 *
 * Module responsibilities:
 *   - CRUD and validation for the core workflow domain: `wf_*` (definitions, schemas,
 *     functions, app-groups) and `sec_*` (users, roles, perms) tables.
 *   - Exposes the generated OpenAPI REST surface (`src/main/kotlin-generated`) and a
 *     `DebugService` for step-by-step workflow inspection.
 *   - Wires the workflow engine (`WfEngine`, `Router`, `Locks`) with admin-specific
 *     integrations: database-backed registries, JWT + ACL, HTTP-route publishing.
 *   - Loads Fluxion Redis, Redisson, and Spring-Data-Redis autoconfigurations for
 *     distributed lock/idempotency backends and optional queue persistence.
 */
@SpringBootApplication
@EnableAsync
@EnableCaching
class WorkflowAdminApplication

fun main(args: Array<String>) {
    runApplication<WorkflowAdminApplication>(*args)
}
