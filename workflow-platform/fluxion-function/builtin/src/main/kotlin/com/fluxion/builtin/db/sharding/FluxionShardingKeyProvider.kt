//package com.fluxion.builtin.db.sharding
//
//import org.springframework.jdbc.datasource.ShardingKeyProvider
//import java.sql.ConnectionBuilder
//import java.sql.ShardingKey
//import java.sql.SQLException
//
//class FluxionShardingKeyProvider(
//    private val shardingContext: () -> Any?,
//    private val builderFactory: () -> ConnectionBuilder?
//) : ShardingKeyProvider {
//
//    override fun getShardingKey(): ShardingKey? {
//        val key = shardingContext() ?: return null
//        return try {
//            val builder = builderFactory() ?: return null
//            builder.shardingKeyBuilder()
//                .subkey(key, java.sql.Types.VARCHAR)
//                .build()
//        } catch (e: SQLException) {
//            null
//        }
//    }
//
//    override fun getSuperShardingKey(): ShardingKey? = null
//}