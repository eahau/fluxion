package com.fluxion.admin.service

import com.fluxion.adapter.spi.config.FunctionConfigPublisher
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.admin.entity.WfFunction
import com.fluxion.admin.generated.model.FunctionStatus
import com.fluxion.admin.mapper.JsonMapperHelper
import com.fluxion.admin.repository.WfFunctionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.*
import org.springframework.stereotype.Service

/**
 * 函数定义管理 Service
 *
 * 职责：
 *   1. CRUD 函数定义（wf_function 表）
 *   2. 发布/下线函数（更新 status 状态与 publishTarget）
 *   3. 通过 FunctionConfigPublisher 将函数配置推送到 Worker 实例
 */
@Service
class WfFunctionService(
    private val repository: WfFunctionRepository,
    private val jsonMapperHelper: JsonMapperHelper,
    private val functionConfigPublisher: FunctionConfigPublisher? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 发布函数（status = ACTIVE）并推送到 Worker
     */
    fun publish(
        functionName: String,
        publishTarget: com.fluxion.admin.generated.model.PublishTarget? = null
    ): WfFunction {
        val entity = repository.findByFunctionName(functionName)
            .orElseThrow { IllegalArgumentException("Function not found: $functionName") }
        entity.status = FunctionStatus.ACTIVE
        publishTarget?.let {
            val config = (jsonMapperHelper.functionToConfig(entity) ?: emptyMap()).toMutableMap()
            config["publishTarget"] = it
            entity.config = jsonMapperHelper.functionConfigToJsonString(config)
        }
        val saved = repository.save(entity)
        publishToWorkers(saved)
        log.info { "Published function [$functionName]" }
        return saved
    }

    /**
     * 下线函数（status = INACTIVE）并从 Worker 移除
     */
    fun deprecate(functionName: String): WfFunction {
        val entity = repository.findByFunctionName(functionName)
            .orElseThrow { IllegalArgumentException("Function not found: $functionName") }
        entity.status = FunctionStatus.INACTIVE
        val saved = repository.save(entity)
        unpublishFromWorkers(functionName)
        log.info { "Deprecated function [$functionName]" }
        return saved
    }

    /**
     * 保存并推送函数配置
     */
    fun save(entity: WfFunction): WfFunction {
        val saved = repository.save(entity)
        if (saved.status == FunctionStatus.ACTIVE) {
            publishToWorkers(saved)
        }
        return saved
    }

    /**
     * 删除函数并从 Worker 移除
     */
    fun delete(functionName: String) {
        val entity = repository.findByFunctionName(functionName)
            .orElseThrow { IllegalArgumentException("Function not found: $functionName") }
        repository.delete(entity)
        unpublishFromWorkers(functionName)
        log.info { "Deleted function [$functionName]" }
    }

    /**
     * 全量拉取所有已启用函数快照（供 Worker HTTP 拉取）
     * 默认返回 PLATFORM + 所有 PRIVATE（本地开发场景）
     */
    fun loadAllEnabledSnapshots(): List<FunctionConfigSnapshot> {
        return repository.findAll()
            .filter { it.status == FunctionStatus.ACTIVE }
            .filterNot { it.functionType.equals("BUILTIN", ignoreCase = true) }
            .map { toSnapshot(it) }
    }

    /**
     * 拉取指定 appGroup 可见的函数快照（PLATFORM + 该 appGroup 的 PRIVATE）
     * 供配置下发时使用
     */
    fun loadEnabledSnapshots(appGroup: String): List<FunctionConfigSnapshot> {
        val platformFns = repository.findByScope("PLATFORM")
            .filter { it.status == FunctionStatus.ACTIVE && !it.functionType.equals("BUILTIN", ignoreCase = true) }
        val privateFns = repository.findByScopeAndAppGroup("PRIVATE", appGroup)
            .filter { it.status == FunctionStatus.ACTIVE }
        return (platformFns + privateFns).map { toSnapshot(it) }
    }

    /**
     * 获取单个函数快照
     */
    fun getSnapshot(functionName: String): FunctionConfigSnapshot? {
        val entity = repository.findByFunctionName(functionName).orElse(null) ?: return null
        return toSnapshot(entity)
    }

    private fun publishToWorkers(entity: WfFunction) {
        if (entity.functionType.equals("BUILTIN", ignoreCase = true)) return
        val publisher = functionConfigPublisher ?: return
        asyncScope.launch {
            val snapshot = toSnapshot(entity)
            val config = jsonMapperHelper.functionToConfig(entity) ?: emptyMap()
            val generatedTarget = jsonMapperHelper.functionConfigToPublishTargetJson(config)
                ?.let { jsonMapperHelper.stringToPublishTarget(it) }
            val target = jsonMapperHelper.toSpiPublishTarget(generatedTarget)
            try {
                publisher.publish(snapshot, target)
            } catch (e: Exception) {
                log.error(e) { "Failed to publish function [${entity.functionName}] to workers" }
            }
        }
    }

    private fun unpublishFromWorkers(functionName: String) {
        if (functionName.startsWith("builtin:")) return
        val publisher = functionConfigPublisher ?: return
        asyncScope.launch {
            try {
                publisher.unpublish(functionName)
            } catch (e: Exception) {
                log.error(e) { "Failed to unpublish function [$functionName] from workers" }
            }
        }
    }

    private fun toSnapshot(entity: WfFunction): FunctionConfigSnapshot {
        val config = jsonMapperHelper.functionToConfig(entity) ?: emptyMap()
        val targetGroups = jsonMapperHelper.functionConfigToPublishTargetJson(config)
            ?.let { jsonMapperHelper.stringToPublishTarget(it) }?.groups
        return FunctionConfigSnapshot(
            functionName = entity.functionName,
            functionType = entity.functionType,
            scriptBody = jsonMapperHelper.functionConfigToScriptBody(config),
            className = jsonMapperHelper.functionConfigToClassName(config),
            endpoint = null,
            paramSchema = jsonMapperHelper.functionConfigToParamSchema(config),
            outputSchema = jsonMapperHelper.functionConfigToOutputSchema(config),
            description = jsonMapperHelper.functionConfigToDescription(config),
            config = config,
            enabled = entity.status == FunctionStatus.ACTIVE,
            targetGroups = targetGroups
        )
    }
}
