package com.fluxion.config.core

import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.core.util.JsonUtil
import org.slf4j.*

/**
 * 配置快照通用解析工具
 *
 * 避免在 Apollo/Nacos/HTTP 各实现中重复写 objectMapper.readValue + try/catch。
 */
object SnapshotParser {

    /**
     * 解析函数配置快照。
     *
     * @param json 原始 JSON 字符串
     * @param functionName 函数名（仅用于日志）
     * @param log 日志记录器
     * @return 解析后的快照；失败返回 null
     */
    fun parseFunctionSnapshot(
        json: String,
        functionName: String,
        log: Logger
    ): FunctionConfigSnapshot? {
        return try {
            JsonUtil.deserialize(json, FunctionConfigSnapshot::class.java)
        } catch (e: Exception) {
            log.error(e) { "Failed to parse function snapshot: functionName=$functionName" }
            null
        }
    }

    /**
     * 解析函数索引集合（Nacos 索引专用）。
     */
    fun parseFunctionIndex(
        content: String,
        log: Logger
    ): Set<String> {
        return try {
            JsonUtil.deserializeSet(content, String::class.java)
        } catch (e: Exception) {
            log.error(e) { "Failed to parse function index" }
            emptySet()
        }
    }
}
