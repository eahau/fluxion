package com.fluxion.admin.controller

import com.fluxion.config.core.InstanceInfo
import com.fluxion.config.core.InstanceRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Admin internal HTTP endpoints for Worker self-registration (instance registry).
 *
 * Workers running with `workflow.config.type=http` use these endpoints to:
 *   1. POST   /internal/workflow/registry/register       — register this worker with admin
 *   2. POST   /internal/workflow/registry/heartbeat/{id} — touch heartbeat (every 10 s)
 *   3. DELETE /internal/workflow/registry/instances/{id} — deregister on clean shutdown
 *
 * Moved here (admin controller package) from fluxion-config:spring-boot because the
 * auto-configured @Bean declaration in HttpConfigAutoConfiguration was not being picked
 * up by Spring MVC's RequestMappingHandlerMapping when the admin's component-scan
 * base package is `com.fluxion.admin` (the Controller class lives under `com.fluxion.config.http`).
 */
@RestController
@RequestMapping("/internal/workflow/registry")
class InternalRegistryController(
    private val registry: InstanceRegistry
) {

    @PostMapping("/register")
    fun register(@RequestBody instance: InstanceInfo): ResponseEntity<Void> {
        registry.register(instance)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/heartbeat/{instanceId}")
    fun heartbeat(@PathVariable instanceId: String): ResponseEntity<Void> {
        registry.heartbeat(instanceId)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/instances/{instanceId}")
    fun deregister(@PathVariable instanceId: String): ResponseEntity<Void> {
        registry.deregister(instanceId)
        return ResponseEntity.ok().build()
    }
}
