package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.DecoratorsApi
import com.fluxion.admin.generated.model.DecoratorDefinition
import com.fluxion.decorator.decorator.DecoratorRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class DecoratorsController(
    private val decoratorRegistry: DecoratorRegistry
) : DecoratorsApi {

    override fun listDecorators(): ResponseEntity<List<DecoratorDefinition>> {
        val registeredNames = decoratorRegistry.listNames().toSet()
        val available = BUILTIN_DECORATORS.filter { registeredNames.contains(it.name) }
        return ResponseEntity.ok(available)
    }

    companion object {
        private val BUILTIN_DECORATORS = listOf(
            DecoratorDefinition(name = "metrics:micrometer").apply {
                label = "Micrometer metrics"
                group = "Observability"
                description = "Record node execution count, latency and other metrics"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            },
            DecoratorDefinition(name = "trace:span").apply {
                label = "OpenTelemetry Span"
                group = "Observability"
                description = "Create a standalone tracing span for each node execution"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to mapOf(
                        "spanName" to mapOf("type" to "string")
                    )
                )
            },
            DecoratorDefinition(name = "ratelimit:slidingWindow").apply {
                label = "Sliding window rate limit"
                group = "Governance"
                description = "Limit node execution rate based on sliding window, supports local memory or Redis distributed storage"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to mapOf(
                        "upLimited" to mapOf("type" to "integer", "minimum" to 1, "default" to 6),
                        "cdSeconds" to mapOf("type" to "integer", "minimum" to 1, "default" to 10),
                        "recoveryPerCd" to mapOf("type" to "integer", "minimum" to 1, "default" to 6),
                        "rateLimitKey" to mapOf("type" to "string"),
                        "keyPrefix" to mapOf("type" to "string", "default" to "fluxion:ratelimit:")
                    )
                )
            },
            DecoratorDefinition(name = "cache:local").apply {
                label = "Local cache"
                group = "Cache"
                description = "Cache node execution results in-process using Caffeine"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to mapOf(
                        "cacheKeyExpression" to mapOf("type" to "string", "default" to "#input"),
                        "ttlSeconds" to mapOf("type" to "integer", "minimum" to 1, "default" to 60)
                    )
                )
            },
            DecoratorDefinition(name = "async:ioPool").apply {
                label = "Async - IO thread pool"
                group = "Async"
                description = "Execute node asynchronously in the IO thread pool"
                paramSchema = mutableMapOf<String, Any>()
            },
            DecoratorDefinition(name = "logging:default").apply {
                label = "Default logging"
                group = "Observability"
                description = "Record node execution input, output and elapsed time"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            }
        )
    }
}
