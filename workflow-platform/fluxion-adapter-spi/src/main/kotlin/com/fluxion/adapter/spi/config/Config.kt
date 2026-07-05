package com.fluxion.adapter.spi.config

import com.fluxion.adapter.spi.registry.PublishTarget

// ═══════════════════════════════════════════════════════════════════════════
//  传输值对象 — 配置中心传输层 DTO，跨层共享
// ═══════════════════════════════════════════════════════════════════════════

/** 配置变更类型枚举 */
enum class ChangeType {
    /** 新发布（首次上线） */
    PUBLISH,
    /** 更新（已有定义的版本升级） */
    UPDATE,
    /** 移除（下线/废弃） */
    REMOVE
}

/**
 * 工作流定义快照 — 配置中心传输值对象
 */
data class WorkflowDefinitionSnapshot(
    val workflowId: String,
    val definitionJson: String?,
    val version: Int,
    val active: Boolean,
    val targetGroups: List<String>?
) {
    /** 判断是否面向全量（无群组限制） */
    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
        @JvmStatic
        fun removed(workflowId: String) = WorkflowDefinitionSnapshot(workflowId, null, 0, false, null)

        @JvmStatic
        fun of(workflowId: String, definitionJson: String, version: Int, active: Boolean) =
            WorkflowDefinitionSnapshot(workflowId, definitionJson, version, active, null)
    }
}

/**
 * 函数配置快照 — 配置中心传输值对象
 *
 * 用于把函数定义（尤其是 SCRIPT / EXTERNAL 类型）从 Admin 推送到 Worker 实例，
 * Worker 收到后注册到本地 FunctionRegistry，实现函数热更新。
 */
data class FunctionConfigSnapshot(
    val functionName: String,
    val functionType: String,
    /** 函数版本号，单调递增；0 表示未显式指定版本（向后兼容） */
    val version: Long = 0,
    val scriptBody: String? = null,
    val className: String? = null,
    val endpoint: String? = null,
    val paramSchema: String? = null,
    val outputSchema: String? = null,
    val description: String? = null,
    /** 透传函数统一配置 Map，供 EXTERNAL 等类型读取扩展字段 */
    val config: Map<String, Any>? = null,
    val enabled: Boolean = true,
    val targetGroups: List<String>? = null
) {
    /** 判断是否面向全量（无群组限制） */
    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
        @JvmStatic
        fun removed(functionName: String) = FunctionConfigSnapshot(
            functionName = functionName,
            functionType = "CUSTOM",
            scriptBody = null,
            className = null,
            endpoint = null,
            paramSchema = null,
            outputSchema = null,
            description = null,
            config = null,
            enabled = false,
            targetGroups = null
        )
    }
}

/**
 * Schema 配置快照 — 配置中心传输值对象
 *
 * 用于把 Schema 定义从 Admin 推送到 Worker 实例，
 * Worker 收到后注册到本地 SchemaRegistry，实现 Schema 热更新。
 *
 * @param schemaName   Schema 唯一名称
 * @param schemaFormat Schema 格式标识（json-schema / protobuf / avro）
 * @param schemaJson   Schema 原始内容（JSON 格式的 Schema 定义）
 * @param version      Schema 版本号，单调递增；0 表示未显式指定版本
 * @param description  描述信息
 * @param scope        作用域：PLATFORM / PRIVATE
 * @param appGroup     所属应用分组（scope=PRIVATE 时有值）
 * @param enabled      是否启用（false 表示 REMOVE 事件）
 * @param targetGroups 可选的目标 Worker 分组过滤
 */
data class SchemaConfigSnapshot(
    val schemaName: String,
    val schemaFormat: String = "json-schema",
    val schemaJson: String? = null,
    val version: Long = 0,
    val description: String? = null,
    val scope: String = "PLATFORM",
    val appGroup: String? = null,
    val enabled: Boolean = true,
    val targetGroups: List<String>? = null
) {
    /** 判断是否面向全量（无群组限制） */
    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
        @JvmStatic
        fun removed(schemaName: String) = SchemaConfigSnapshot(
            schemaName = schemaName,
            schemaJson = null,
            enabled = false
        )
    }
}

/**
 * 幂等缓存配置快照 — 配置中心传输值对象
 *
 * 支持应用级默认配置 + 工作流级覆盖的二维策略：
 * - 应用级字段（[enabled] / [ttlHours] / [maxSize] / [spec]）作为全局默认值
 * - [workflows] 按工作流 ID 提供精细覆盖，未指定字段继承应用级默认值
 *
 * @param enabled    是否启用幂等缓存（应用默认）
 * @param ttlHours   缓存条目 TTL（小时，应用默认）
 * @param maxSize    最大缓存条目数（应用默认）
 * @param spec       可选的 Caffeine 高级配置表达式（应用默认）
 * @param workflows  工作流级覆盖配置，key 为 workflowId
 */
data class IdempotencyConfig(
    val enabled: Boolean = true,
    val ttlHours: Long = 24,
    val maxSize: Long = 1_000,
    val spec: String? = null,
    val workflows: Map<String, WorkflowIdempotencyConfig> = emptyMap()
)

/**
 * 工作流级幂等缓存策略覆盖。
 *
 * 所有字段均可空，null 表示继承应用级默认值。
 */
data class WorkflowIdempotencyConfig(
    val enabled: Boolean? = null,
    val ttlHours: Long? = null,
    val maxSize: Long? = null,
    val spec: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════
//  回调监听器 — 配置变更回调契约
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 通用配置变更监听器（单值）
 *
 * 泛型回调接口，适用于任何单值配置（如 IdempotencyConfig）。
 * 上层业务通过此回调接收解析后的配置对象。
 */
fun interface ConfigChangeListener<T> {
    fun onChange(config: T)
}

/**
 * 带键配置变更监听器（多键）
 *
 * 适用于多键配置（如工作流定义、函数配置），每次变更携带 key + changeType。
 */
fun interface KeyedConfigChangeListener<T> {
    fun onChange(key: String, config: T, changeType: ChangeType)
}

/** 工作流定义变更监听器 */
typealias DefinitionChangeListener = KeyedConfigChangeListener<WorkflowDefinitionSnapshot>

/** 函数配置变更监听器 */
typealias FunctionChangeListener = KeyedConfigChangeListener<FunctionConfigSnapshot>

/** Schema 配置变更监听器 */
typealias SchemaChangeListener = KeyedConfigChangeListener<SchemaConfigSnapshot>

// ═══════════════════════════════════════════════════════════════════════════
//  获取层（Fetch）— 从配置中心拉取配置数据
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 单值配置获取 SPI
 *
 * 负责从配置中心拉取当前配置值（一次性加载）。
 */
interface ConfigFetcher<T> {
    /** 从配置中心加载当前配置 */
    fun load(): T
}

/**
 * 多键配置获取 SPI
 *
 * 负责从配置中心按 key 拉取配置项（全量加载 + 按需查询）。
 */
interface KeyedConfigFetcher<T> {
    /** 加载当前所有配置项 */
    fun loadAll(): List<T>
    /** 按 key 获取单个配置项 */
    fun get(key: String): T?
}

// ═══════════════════════════════════════════════════════════════════════════
//  监听层（Watch）— 监听配置变更并通知上层应用
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 单值配置监听 SPI
 *
 * 负责订阅配置变更事件，配置变更时推送 [T] 给已注册的监听器。
 */
interface ConfigWatcher<T> {
    /** 注册监听器，配置变更时推送 [T] */
    fun watch(listener: ConfigChangeListener<T>)
}

/**
 * 多键配置监听 SPI
 *
 * 负责订阅配置变更事件，配置变更时推送 key + 快照 + 变更类型给已注册的监听器。
 */
interface KeyedConfigWatcher<T> {
    /** 注册监听器，配置变更时推送 key + 快照 + 变更类型 */
    fun watch(listener: KeyedConfigChangeListener<T>)
}

// ═══════════════════════════════════════════════════════════════════════════
//  订阅器 = 获取 + 监听（组合）— 配置中心适配器的完整契约
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 单值配置订阅器 SPI = [ConfigFetcher] + [ConfigWatcher]
 *
 * 各配置中心实现（Apollo/Nacos/HTTP）同时提供加载与监听能力。
 */
interface ConfigSubscriber<T> : ConfigFetcher<T>, ConfigWatcher<T>

/**
 * 多键配置订阅器 SPI = [KeyedConfigFetcher] + [KeyedConfigWatcher]
 *
 * 各配置中心实现（Apollo/Nacos/HTTP）同时提供按 key 加载与变更监听能力。
 */
interface KeyedConfigSubscriber<T> : KeyedConfigFetcher<T>, KeyedConfigWatcher<T>

/** 工作流定义配置订阅器 — 业务实例侧 */
typealias DefinitionConfigSubscriber = KeyedConfigSubscriber<WorkflowDefinitionSnapshot>

/** 函数配置订阅器 — Worker 侧 */
typealias FunctionConfigSubscriber = KeyedConfigSubscriber<FunctionConfigSnapshot>

/** Schema 配置订阅器 — Worker 侧 */
typealias SchemaConfigSubscriber = KeyedConfigSubscriber<SchemaConfigSnapshot>

/** 幂等配置动态订阅器 */
typealias IdempotencyConfigSubscriber = ConfigSubscriber<IdempotencyConfig>

// ═══════════════════════════════════════════════════════════════════════════
//  发布层（Publish）— 管理端向配置中心推送配置变更
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 通用多键配置发布器 SPI
 *
 * 各后端实现（Nacos/Apollo/HTTP）提供统一的 publish/unpublish 能力。
 * 新增配置类型只需在 AutoConfiguration 中实例化本接口的后端实现，
 * 无需再定义专门的发布器接口。
 *
 * ```kotlin
 * // 示例：注册一个新的 keyed 配置发布器（Nacos）
 * @Bean
 * fun myConfigPublisher(cs: ConfigService) =
 *     NacosKeyedConfigPublisher(cs, "WORKFLOW", "my.config.", "my.config.__index__")
 * ```
 */
interface KeyedConfigPublisher<T> {
    /** 发布或更新指定 key 的配置 */
    fun publish(key: String, snapshot: T)

    /** 移除指定 key 的配置 */
    fun unpublish(key: String)
}

/**
 * 通用单值配置发布器 SPI
 *
 * 适用于整体发布/覆盖的单值配置（如限流规则、功能开关等）。
 */
interface ConfigPublisher<T> {
    /** 发布或覆盖当前配置 */
    fun publish(config: T)
}

/** 工作流定义配置发布器 SPI — Admin 侧 */
interface DefinitionConfigPublisher {
    fun publish(workflowId: String, definitionJson: String, version: Int)

    fun publish(workflowId: String, definitionJson: String, version: Int, target: PublishTarget) {
        publish(workflowId, definitionJson, version)
    }

    fun unpublish(workflowId: String)
}

/** 函数配置发布器 SPI — Admin 侧 */
interface FunctionConfigPublisher {
    fun publish(snapshot: FunctionConfigSnapshot)

    fun publish(snapshot: FunctionConfigSnapshot, target: PublishTarget) {
        publish(snapshot)
    }

    fun unpublish(functionName: String)
}

/** Schema 配置发布器 SPI — Admin 侧 */
interface SchemaConfigPublisher {
    fun publish(snapshot: SchemaConfigSnapshot)

    fun publish(snapshot: SchemaConfigSnapshot, target: PublishTarget) {
        publish(snapshot)
    }

    fun unpublish(schemaName: String)
}
