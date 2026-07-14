//package com.fluxion.builtin.db.sharding
//
//import org.springframework.jdbc.datasource.ShardingKeyDataSourceAdapter
//import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
//import javax.sql.DataSource
//
//class ShardingDataSourceConfig(
//    private val delegate: DataSource
//) : AbstractRoutingDataSource() {
//
//    private lateinit var shardingAdapter: ShardingKeyDataSourceAdapter
//
//    init {
//        initialize()
//    }
//
//    private fun initialize() {
//        shardingAdapter = ShardingKeyDataSourceAdapter(delegate).apply {
//            setShardingKeyProvider(FluxionShardingKeyProvider(
//                shardingContext = { ShardingContextHolder.getShardingKey() },
//                builderFactory = { delegate.createConnectionBuilder() }
//            ))
//        }
//    }
//
//    override fun determineCurrentLookupKey(): Any {
//        return ShardingContextHolder.getStrategyName() ?: "default"
//    }
//}