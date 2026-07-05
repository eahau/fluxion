package com.fluxion.test.webflux

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * WebFlux 功能验证测试应用
 *
 * 用于验证 fluxion-runtime 在 WebFlux 环境下的动态路由能力：
 *   - WebFlux HTTP 适配器（fluxion-adapter-http:webflux）
 *   - 纯 HandlerMapping 直接查询策略（无 RouterFunction 中间层）
 *   - 工作流执行引擎与 WebFlux 响应式桥接
 *
 * 此模块不包含数据库、Security、JPA 等重型依赖，仅做轻量级功能验证。
 */
@SpringBootApplication
class FluxionTestWebFluxApplication

fun main(args: Array<String>) {
    runApplication<FluxionTestWebFluxApplication>(*args)
}
