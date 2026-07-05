package com.fluxion.script.config

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.FunctionChangeListener
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.adapter.spi.config.FunctionConfigSubscriber
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.external.ExternalFunctionConfigParser
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
import com.fluxion.core.value.FunctionMeta
import com.fluxion.script.function.ExternalWorkflowFunction
import com.fluxion.script.function.ScriptWorkflowFunction
import com.fluxion.script.groovy.GroovyScriptFunction
import org.slf4j.*

/**
 * Worker 侧函数配置应用器。
 *
 * 接收配置中心（HTTP / Apollo / Nacos）推送的函数快照，
 * 将 SCRIPT / EXTERNAL 函数注册到本地 FunctionRegistry，实现热更新。
 */
class FunctionConfigApplier(
    private val subscriber: FunctionConfigSubscriber,
    private val registry: FunctionRegistry,
    private val groovyEngine: GroovyScriptFunction? = null,
    private val appGroup: String? = null,
    private val transportRegistry: ExternalFunctionTransportRegistry = ExternalFunctionTransportRegistry()
) : FunctionChangeListener {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 加载全量快照并注册监听器（应用启动时调用） */
    fun init() {
        val snapshots = subscriber.loadAll()
        val externalConfigs = mutableListOf<com.fluxion.core.function.external.ExternalFunctionConfig>()
        for (snap in snapshots) {
            applySnapshot(snap, ChangeType.PUBLISH, externalConfigs)
        }
        transportRegistry.prepare(externalConfigs)
        log.info("FunctionConfigApplier initialized with ${snapshots.size} functions")
        subscriber.watch(this)
    }

    /** 响应配置变更事件（发布/更新/删除） */
    override fun onChange(functionName: String, snapshot: FunctionConfigSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.PUBLISH, ChangeType.UPDATE -> {
                val externalConfigs = mutableListOf<com.fluxion.core.function.external.ExternalFunctionConfig>()
                applySnapshot(snapshot, changeType, externalConfigs)
                transportRegistry.prepare(externalConfigs)
            }
            ChangeType.REMOVE -> removeFunction(functionName)
        }
    }

    /**
     * 将快照应用到本地注册中心。
     *
     * 处理流程：应用群过滤 → 启用状态检查 → 构建函数实例 → 注册（带版本号，支持热更新）。
     * 若 [externalConfigs] 不为空且当前快照为 EXTERNAL 类型，会把解析后的配置追加进去，
     * 供后续 [transportRegistry.prepare] 统一预建远程端点。
     */
    private fun applySnapshot(
        snapshot: FunctionConfigSnapshot,
        changeType: ChangeType,
        externalConfigs: MutableList<com.fluxion.core.function.external.ExternalFunctionConfig> = mutableListOf()
    ) {
        // 订阅端按应用群过滤
        if (appGroup != null && !snapshot.isGlobal()) {
            if (snapshot.targetGroups?.contains(appGroup) != true) {
                log.debug { "Skipping function ${snapshot.functionName} — targetGroups ${snapshot.targetGroups} does not include appGroup [$appGroup]" }
                return
            }
        }

        if (!snapshot.enabled) {
            removeFunction(snapshot.functionName)
            return
        }

        val functionName = normalizeName(snapshot.functionName, snapshot.functionType)
        val meta = FunctionMeta.builder(functionName)
            .description(snapshot.description ?: functionName)
            .apply {
                snapshot.paramSchema?.let { paramSchema(it) }
                snapshot.outputSchema?.let { outputSchema(it) }
            }
            .build()

        val function = when (snapshot.functionType.uppercase()) {
            "SCRIPT_GROOVY", "GROOVY" -> {
                val script = snapshot.scriptBody
                if (script.isNullOrBlank()) {
                    log.warn("Skipping script function [$functionName] — scriptBody is empty")
                    return
                }
                ScriptWorkflowFunction(
                    name = functionName,
                    scriptBody = script,
                    paramSchema = snapshot.paramSchema,
                    outputSchema = snapshot.outputSchema,
                    description = snapshot.description,
                    groovyEngine = groovyEngine
                )
            }
            "EXTERNAL" -> {
                val externalConfig = ExternalFunctionConfigParser.parse(snapshot.config)
                externalConfigs.add(externalConfig)
                ExternalWorkflowFunction(
                    name = functionName,
                    config = externalConfig,
                    transportRegistry = transportRegistry,
                    paramSchema = snapshot.paramSchema,
                    outputSchema = snapshot.outputSchema,
                    description = snapshot.description
                )
            }
            else -> {
                log.warn { "Unsupported function type [${snapshot.functionType}] for [$functionName], skipping" }
                return
            }
        }

        registry.register(functionName, snapshot.version, meta, function)
        log.info("Applied function [$functionName] type=${snapshot.functionType} version=${snapshot.version} change=$changeType")
    }

    /** 注销函数及其前缀变体（script:/external:） */
    private fun removeFunction(functionName: String) {
        registry.unregister(functionName)
        // 同时尝试按常见前缀注销
        registry.unregister("script:$functionName")
        registry.unregister("external:$functionName")
        log.info { "Removed function [$functionName] from registry" }
    }

    /** 根据函数类型添加标准前缀（script: / external:） */
    private fun normalizeName(functionName: String, functionType: String): String {
        return when (functionType.uppercase()) {
            "SCRIPT_GROOVY", "GROOVY" -> {
                if (functionName.startsWith("script:")) functionName else "script:$functionName"
            }
            "EXTERNAL" -> {
                if (functionName.startsWith("external:")) functionName else "external:$functionName"
            }
            else -> functionName
        }
    }
}
