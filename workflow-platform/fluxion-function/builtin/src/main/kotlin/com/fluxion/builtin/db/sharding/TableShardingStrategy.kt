package com.fluxion.builtin.db.sharding

interface TableShardingStrategy {
    fun name(): String
    fun resolveTableName(baseTableName: String, shardingKey: Any?): String
    fun supports(shardingKeyType: Class<*>): Boolean
}