package com.fluxion.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.CaffeineSpec
import com.github.benmanes.caffeine.cache.Policy
import com.github.benmanes.caffeine.cache.stats.CacheStats
import org.slf4j.LoggerFactory
import org.slf4j.info
import org.slf4j.warn
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

object FluxionCacheFactory {

    private val log = LoggerFactory.getLogger(javaClass)
    private val cacheRefMap = ConcurrentHashMap<String, AtomicReference<Cache<*, *>>>()
    private val configRef = AtomicReference<Map<String, CaffeineSpec>>(emptyMap())

    private var defaultSpec: CaffeineSpec = CaffeineSpec.parse("maximumSize=1000,expireAfterWrite=30m")
    private var initialized = false

    fun init(
        configProvider: (() -> Map<String, String>)? = null,
        configWatcher: ((Map<String, String>) -> Unit) -> Unit = {},
        defaultSpec: String = "maximumSize=1000,expireAfterWrite=30m"
    ) {
        if (initialized) {
            log.warn { "FluxionCacheFactory already initialized" }
            return
        }
        this.defaultSpec = CaffeineSpec.parse(defaultSpec)

        val initialConfig = configProvider?.invoke()?.mapValues { CaffeineSpec.parse(it.value) } ?: emptyMap()
        configRef.set(initialConfig)
        log.info { "Loaded ${initialConfig.size} cache configurations" }

        configWatcher { newConfig ->
            val parsed = newConfig.mapValues { CaffeineSpec.parse(it.value) }
            val oldConfig = configRef.getAndSet(parsed)
            updateCaches(oldConfig, parsed)
        }

        initialized = true
        log.info { "FluxionCacheFactory initialized with default spec: $defaultSpec" }
    }

    @Suppress("UNCHECKED_CAST")
    fun <K, V> get(name: String): Cache<K, V> =
        cacheRefMap.computeIfAbsent(name) {
            val spec = configRef.get()[name] ?: defaultSpec
            log.info { "Creating cache [$name] with spec: $spec" }
            AtomicReference(Caffeine.from(spec).build<K, V>())
        }.let { FluxionCacheProxy(it as AtomicReference<Cache<K, V>>) }

    fun invalidateAll() {
        cacheRefMap.values.forEach { it.get().invalidateAll() }
        log.info { "All ${cacheRefMap.size} caches invalidated" }
    }

    fun shutdown() {
        cacheRefMap.values.forEach { it.get().invalidateAll() }
        cacheRefMap.clear()
        configRef.set(emptyMap())
        initialized = false
        log.info { "FluxionCacheFactory shutdown" }
    }

    private fun updateCaches(oldConfig: Map<String, CaffeineSpec>, newConfig: Map<String, CaffeineSpec>) {
        newConfig.forEach { (name, newSpec) ->
            val oldSpec = oldConfig[name]
            if (newSpec != oldSpec) {
                cacheRefMap[name]?.let { ref ->
                    val existing = ref.get()
                    val newCache = Caffeine.from(newSpec).build<Any, Any>()
                    newCache.putAll(existing.asMap())
                    ref.set(newCache)
                    log.info { "Updated cache [$name] spec to: $newSpec" }
                }
            }
        }
        val currentNames = cacheRefMap.keys.toSet()
        (currentNames - newConfig.keys).forEach { name ->
            val ref = cacheRefMap.remove(name)
            ref?.get()?.invalidateAll()
            log.info { "Removed cache [$name] (no longer in config)" }
        }
    }

    private class FluxionCacheProxy<K, V>(private val ref: AtomicReference<Cache<K, V>>) : Cache<K, V> {
        private val cache get() = ref.get()

        override fun get(key: K, mappingFunction: Function<in K, out V>): V = cache.get(key, mappingFunction)

        override fun getIfPresent(key: K): V? = cache.getIfPresent(key)
        override fun getAllPresent(keys: MutableIterable<K>): MutableMap<K, V> = cache.getAllPresent(keys)

        override fun getAll(
            keys: MutableIterable<K>?,
            mappingFunction: Function<in MutableSet<out K>, out MutableMap<out K, out V>>?
        ): MutableMap<K, V>? = cache.getAll(keys, mappingFunction)

        override fun put(key: K, value: V) = cache.put(key, value)
        override fun putAll(map: MutableMap<out K, out V>) = cache.putAll(map)
        override fun invalidate(key: K) = cache.invalidate(key)
        override fun invalidateAll() = cache.invalidateAll()
        override fun invalidateAll(keys: MutableIterable<K>) = cache.invalidateAll(keys)
        override fun asMap(): ConcurrentMap<K, V> = cache.asMap()
        override fun estimatedSize(): Long = cache.estimatedSize()
        override fun stats(): CacheStats = cache.stats()
        override fun policy(): Policy<K, V> = cache.policy()
        override fun cleanUp() = cache.cleanUp()
    }
}