package com.fluxion.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync

/**
 * 工作流平台管理服务启动类
 *
 * 包含：
 *   - workflow-admin REST API（工作流定义/函数/Schema 管理）
 *   - WorkflowEngine + DagExecutor（核心执行引擎）
 *   - WorkflowRouterImpl（协议路由）
 *   - 内置函数自动配置（DB/HTTP/MQ/JSON）
 *   - Redis 函数自动配置（Layer1/2/3）
 *   - 脚本引擎自动配置（Groovy/JS）
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
class WorkflowAdminApplication

fun main(args: Array<String>) {
    runApplication<WorkflowAdminApplication>(*args)
}
