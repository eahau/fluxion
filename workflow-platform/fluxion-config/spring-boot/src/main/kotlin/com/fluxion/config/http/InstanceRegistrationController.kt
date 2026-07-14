package com.fluxion.config.http

import com.fluxion.config.core.InstanceInfo
import com.fluxion.config.core.InstanceRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP registration endpoint — Admin side.
 */
@RestController
@RequestMapping("/internal/workflow/registry")
class InstanceRegistrationController(
    private val registry: InstanceRegistry
) {

    @PostMapping("/register")
    fun register(@RequestBody instance: InstanceInfo): ResponseEntity<Void> {
        registry.register(instance)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/heartbeat")
    fun heartbeat(@RequestParam instanceId: String): ResponseEntity<Void> {
        registry.heartbeat(instanceId)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/deregister")
    fun deregister(@RequestParam instanceId: String): ResponseEntity<Void> {
        registry.deregister(instanceId)
        return ResponseEntity.ok().build()
    }
}
