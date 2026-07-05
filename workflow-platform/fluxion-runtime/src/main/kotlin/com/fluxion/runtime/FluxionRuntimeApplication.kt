package com.fluxion.runtime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Fluxion 运行面（Data Plane / Worker）启动类。
 *
 * 职责：
 *   - 向 fluxion-admin（控制面）注册自身
 *   - 从配置中心拉取工作流定义与函数配置
 *   - 通过 HTTP / RPC / MQ 暴露工作流执行能力
 *   - 供 Java 8 业务系统作为 sidecar 远程调用
 */
@SpringBootApplication
class FluxionRuntimeApplication

fun main(args: Array<String>) {
    runApplication<FluxionRuntimeApplication>(*args)
}
