package com.fluxion.builtin.db.sharding.impl

import com.fluxion.builtin.db.sharding.TableShardingStrategy

class HashTableShardingStrategy(
    private val shardingCount: Int = 8,
    private val paddingLength: Int = 2
) : TableShardingStrategy {

    override fun name(): String = "hash"

    override fun resolveTableName(baseTableName: String, shardingKey: Any?): String {
        if (shardingKey == null) return baseTableName
        val hash = Math.abs(shardingKey.hashCode())
        val index = hash % shardingCount
        val suffix = index.toString().padStart(paddingLength, '0')
        return "${baseTableName}_$suffix"
    }

    override fun supports(shardingKeyType: Class<*>): Boolean = true
}