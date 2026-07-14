package com.fluxion.builtin.db.sharding.impl

import com.fluxion.builtin.db.sharding.TableShardingStrategy

class DefaultTableShardingStrategy : TableShardingStrategy {
    override fun name(): String = "default"
    override fun resolveTableName(baseTableName: String, shardingKey: Any?): String = baseTableName
    override fun supports(shardingKeyType: Class<*>): Boolean = true
}