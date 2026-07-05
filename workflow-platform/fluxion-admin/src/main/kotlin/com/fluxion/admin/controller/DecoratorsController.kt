package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.DecoratorsApi
import com.fluxion.admin.generated.model.DecoratorDefinition
import com.fluxion.core.decorator.DecoratorRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * 装饰器参数 Schema REST API
 *
 * 返回前端画布可用的节点装饰器及其参数 JSON Schema。
 * 列表由后端 DecoratorRegistry 实际注册情况驱动，只返回当前可用的装饰器。
 */
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
                label = "Micrometer 指标"
                group = "可观测"
                description = "记录节点执行次数、耗时等指标"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            },
            DecoratorDefinition(name = "trace:span").apply {
                label = "OpenTelemetry Span"
                group = "可观测"
                description = "为节点创建独立 Span"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to mapOf(
                        "spanName" to mapOf("type" to "string")
                    )
                )
            },
            DecoratorDefinition(name = "ratelimit:slidingWindow").apply {
                label = "滑动窗口限流"
                group = "治理"
                description = "基于滑动窗口对节点执行进行限流，支持本地内存与 Redis 分布式存储"
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
                label = "本地缓存"
                group = "缓存"
                description = "使用本地 Caffeine 缓存节点执行结果"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to mapOf(
                        "cacheKeyExpression" to mapOf("type" to "string", "default" to "#input"),
                        "ttlSeconds" to mapOf("type" to "integer", "minimum" to 1, "default" to 60)
                    )
                )
            },
            DecoratorDefinition(name = "async:ioPool").apply {
                label = "异步-IO线程池"
                group = "异步"
                description = "在 IO 线程池中异步执行节点"
                paramSchema = mutableMapOf<String, Any>()
            },
            DecoratorDefinition(name = "logging:default").apply {
                label = "默认日志"
                group = "可观测"
                description = "记录节点执行输入、输出与耗时"
                paramSchema = mutableMapOf<String, Any>(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            }
        )
    }
}
