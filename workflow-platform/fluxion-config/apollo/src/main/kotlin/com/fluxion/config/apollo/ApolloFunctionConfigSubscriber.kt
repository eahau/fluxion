package com.fluxion.config.apollo

import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.config.core.SnapshotParser
import org.slf4j.LoggerFactory

/**
 * Apollo 实现 — 函数配置订阅器（Worker 侧）
 *
 * 保留为 thin subclass 的唯一原因：[mapKeyToSnapshot] 需要引用子类 log，
 * 而 log 在 super 构造调用时尚未初始化，无法作为 mapper lambda 传入。
 */
class ApolloFunctionConfigSubscriber :
    ApolloKeyedConfigSubscriber<FunctionConfigSnapshot>(
        namespace = "workflow-functions",
        removedMapper = { key -> FunctionConfigSnapshot.removed(key) }
    ) {

    private val fnLog = LoggerFactory.getLogger(javaClass)

    override fun mapKeyToSnapshot(key: String, content: String): FunctionConfigSnapshot? =
        SnapshotParser.parseFunctionSnapshot(content, key, fnLog)
}
