package com.fluxion.admin.service

import com.fluxion.admin.entity.AppResource
import com.fluxion.admin.repository.AppResourceRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * On-demand Hikari DataSource factory for user-registered [AppResource] records.
 *
 * The builtin [com.fluxion.builtin.db.DataSourceProvider] only exposes DataSources that
 * the admin Spring context wired at startup; this factory lets the Admin console *also*
 * connect to the *real* application databases (the ones the user registered in the
 * Resource Center) so features like:
 *   - sandbox table cloning (CREATE TABLE ... LIKE)
 *   - schema browser (pick a real-app table and scaffold a JSON Schema)
 *   - query previews (LIMIT 50 SELECT)
 * work against the actual target databases.
 *
 * DataSources are cached by resource id so repeated sandbox acquisitions do not pay the
 * connection-construction cost. The cache is evicted automatically when the resource's
 * `updatedAt` timestamp changes or on explicit [evict] calls.
 */
@Service
class AppDataSourceFactory(
    private val resourceRepo: AppResourceRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val cache = ConcurrentHashMap<Long, CachedDataSource>()

    /**
     * Get (or build) the [DataSource] for a DB-type [AppResource] identified by `resourceId`.
     * Throws `IllegalArgumentException` if the resource does not exist or is not `resourceType=DB`.
     */
    fun getDataSource(resourceId: Long): DataSource {
        val resource = requireNotNull(resourceRepo.findById(resourceId).orElse(null)) {
            "AppResource id=$resourceId not found"
        }
        require(resource.resourceType.equals("DB", ignoreCase = true)) {
            "AppResource id=$resourceId type=${resource.resourceType} is not DB"
        }
        val cached = cache.compute(resource.id) { _, prev ->
            when {
                prev == null -> CachedDataSource(build(resource), resource.updatedAt)
                prev.updatedAt != resource.updatedAt -> {
                    runCatching { prev.ds.close() }
                    CachedDataSource(build(resource), resource.updatedAt)
                }
                else -> prev
            }
        }
        return cached!!.ds
    }

    /** Evict a DataSource from the cache (called on resource update / deletion). */
    fun evict(resourceId: Long) {
        cache.remove(resourceId)?.let { c ->
            runCatching { c.ds.close() }
            log.info { "Evicted cached DataSource for resourceId=$resourceId" }
        }
    }

    // ── internals ─────────────────────────────────────────────────

    private fun build(resource: AppResource): HikariDataSource {
        val cfg = resource.configJson
        val url = requireNotNull(cfg["url"]?.toString()?.takeIf { it.isNotBlank() }) {
            "AppResource id=${resource.id} missing `url` key in configJson"
        }
        val username = cfg["username"]?.toString()
        val password = cfg["password"]?.toString()
        val driver = resource.driver
            ?: cfg["driverClassName"]?.toString()
            ?: cfg["driver_class_name"]?.toString()
            ?: inferDriverFromJdbcUrl(url)

        val hikariConfig = HikariConfig().apply {
            this.poolName = "fluxion-app-${resource.id}-${resource.resourceName}"
            this.jdbcUrl = url
            this.driverClassName = driver
            this.username = username
            this.password = password
            this.maximumPoolSize = (cfg["maxPoolSize"] as? Int) ?: 4
            this.minimumIdle = 0
            this.connectionTimeout = (cfg["connectionTimeoutMs"] as? Long) ?: 5_000L
            this.idleTimeout = 60_000L
            this.isReadOnly = false
        }
        log.info {
            "Building HikariCP datasource pool=${hikariConfig.poolName} driver=$driver url=${url.take(80)} maxPool=${hikariConfig.maximumPoolSize}"
        }
        return HikariDataSource(hikariConfig)
    }

    private fun inferDriverFromJdbcUrl(url: String): String = when {
        url.startsWith("jdbc:mysql:")     -> "com.mysql.cj.jdbc.Driver"
        url.startsWith("jdbc:mariadb:")   -> "org.mariadb.jdbc.Driver"
        url.startsWith("jdbc:postgresql:")-> "org.postgresql.Driver"
        url.startsWith("jdbc:h2:")        -> "org.h2.Driver"
        url.startsWith("jdbc:oracle:")    -> "oracle.jdbc.OracleDriver"
        url.startsWith("jdbc:sqlserver:") -> "com.microsoft.sqlserver.jdbc.SQLServerDriver"
        else                              -> "com.mysql.cj.jdbc.Driver" // safest default
    }

    private data class CachedDataSource(
        val ds: HikariDataSource,
        val updatedAt: java.time.LocalDateTime
    )
}
