package com.fluxion.builtin.db.sharding

object ShardingContextHolder {
    private val SHARDING_KEY = ThreadLocal<Any?>()
    private val STRATEGY_NAME = ThreadLocal<String>()

    fun setShardingKey(key: Any?) {
        SHARDING_KEY.set(key)
    }

    fun getShardingKey(): Any? = SHARDING_KEY.get()

    fun setStrategyName(name: String) {
        STRATEGY_NAME.set(name)
    }

    fun getStrategyName(): String? = STRATEGY_NAME.get()

    fun clear() {
        SHARDING_KEY.remove()
        STRATEGY_NAME.remove()
    }
}