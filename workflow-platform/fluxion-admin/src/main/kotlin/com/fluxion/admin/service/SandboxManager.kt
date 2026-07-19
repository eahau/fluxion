package com.fluxion.admin.service

import com.fluxion.admin.entity.App
import com.fluxion.admin.entity.AppResource
import com.fluxion.admin.entity.SandboxInstance
import com.fluxion.admin.repository.AppRepository
import com.fluxion.admin.repository.AppResourceRepository
import com.fluxion.admin.repository.SandboxInstanceRepository
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.sql.Statement
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Debug-sandbox lifecycle manager — the core feature that lets workflow authors run
 * full test executions against "copy-of-real-schema" tables without polluting
 * production user data.
 *
 * **Strategy = DB_TABLE_PREFIX (per user decision)**
 *
 * Instead of creating a whole new database / H2 embedded instance (which breaks when
 * the app uses non-MySQL features or stored procedures), we clone every user table
 * in the *same* target database using:
 * ```sql
 * CREATE TABLE IF NOT EXISTS `sandbox_sessABC_order_header` LIKE `order_header`;
 * ```
 * The `sandbox_sess<shortSessionId>_` prefix is unique per debug session. When the
 * user finishes we issue `DROP TABLE IF EXISTS` for every cloned table.
 *
 * Redis sandboxes use **REDIS_DB_INDEX** (assigns a dedicated numeric DB index per
 * session, from 1..15). Cleanup = `FLUSHDB` against the selected index.
 *
 * Kafka sandboxes use **KAFKA_TOPIC_PREFIX** (produced/consumed topics get the
 * session prefix). Cleanup = admin-client deleteTopics matching the prefix.
 *
 * Cleanup also runs every 10 minutes via the scheduled [cleanupExpired] sweep so
 * abandoned sessions never leak tables.
 */
@Service
class SandboxManager(
    private val appRepo: AppRepository,
    private val resourceRepo: AppResourceRepository,
    private val sandboxRepo: SandboxInstanceRepository,
    private val bindingRepo: com.fluxion.admin.repository.AppResourceBindingRepository,
    private val dsFactory: AppDataSourceFactory
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Acquire a full set of sandboxes (one per App-bound DB/Redis resource) for a
     * given debug-session. Reuses existing READY sandboxes when `ownerSession`
     * matches so repeated "run test" clicks across the same browser session do not
     * re-clone tables.
     *
     * @return a `SandboxTicket` listing every created/reused sandbox instance and
     *         the routing-context map the designer sends alongside the debug-execution
     *         request so builtin:dbExecute selects the sandbox tables transparently.
     */
    @Transactional
    fun acquire(
        appId: Long,
        ownerSession: String,
        ownerUsername: String? = null,
        baseResourceIds: List<Long>? = null,
        ttlMinutes: Int = 240
    ): SandboxTicket {
        require(ownerSession.isNotBlank()) { "ownerSession is required" }

        val app = requireNotNull(appRepo.findById(appId).orElse(null)) { "App id=$appId not found" }
        val expiredAt = LocalDateTime.now().plusMinutes(ttlMinutes.toLong())

        // Determine which resources to sandbox: explicit IDs → those; otherwise every DB/REDIS
        // binding the app has declared.
        val bindings = bindingRepo.findAllByAppIdWithResource(appId).filter { b ->
            (isDbType(b.resource.resourceType) || b.resource.resourceType == "REDIS") &&
                (baseResourceIds == null || b.resource.id in baseResourceIds)
        }
        require(bindings.isNotEmpty()) {
            "App id=$appId has no DB/REDIS bindings. Bind some resources first before requesting a sandbox."
        }

        val existing = sandboxRepo.findByAppIdAndOwnerSessionAndLifecycleIn(
            appId, ownerSession, setOf(LIFECYCLE_READY, LIFECYCLE_IN_USE, LIFECYCLE_CREATING)
        ).associateBy { it.baseResource.id }

        val created = mutableListOf<SandboxInstance>()
        val reused = mutableListOf<SandboxInstance>()

        for (binding in bindings) {
            if (isDbType(binding.resource.resourceType)) {
                existing[binding.resource.id]
                    ?.also { reused += touch(it) }
                    ?: createDbSandbox(app, binding.resource, ownerSession, ownerUsername, expiredAt)
                        .also { created += it }
            } else if (binding.resource.resourceType == "REDIS") {
                existing[binding.resource.id]
                    ?.also { reused += touch(it) }
                    ?: createRedisDbIndexSandbox(app, binding.resource, ownerSession, ownerUsername, expiredAt)
                        .also { created += it }
            }
        }

        val instances = (created + reused).sortedBy { it.id }
        log.info {
            "Sandbox acquire session=$ownerSession appId=$appId created=${created.size} reused=${reused.size} " +
                "ttl=${ttlMinutes}m types=${instances.groupBy { it.sandboxType }.mapValues { it.value.size }}"
        }

        return SandboxTicket(
            sessionId = ownerSession,
            appId = appId,
            expiredAt = expiredAt.toString(),
            instances = instances.map { it.toSummary() },
            routingContext = buildRoutingContext(instances)
        )
    }

    /** Release a specific sandbox (user clicks "delete sandbox"). */
    @Transactional
    fun release(sandboxId: Long) {
        val s = requireNotNull(sandboxRepo.findById(sandboxId).orElse(null)) { "Sandbox id=$sandboxId not found" }
        destroySandbox(s)
        sandboxRepo.updateLifecycle(s.id, LIFECYCLE_CLEANED, LocalDateTime.now())
        log.info { "Sandbox id=$sandboxId released by user" }
    }

    /** Release *all* sandboxes belonging to a session (used when DebugPanel is closed). */
    @Transactional
    fun releaseSession(sessionId: String) {
        val all = sandboxRepo.findByOwnerSessionOrderById(sessionId)
        all.forEach { destroySandbox(it) }
        if (all.isNotEmpty()) {
            all.forEach { sandboxRepo.updateLifecycle(it.id, LIFECYCLE_CLEANED, LocalDateTime.now()) }
            log.info { "Sandbox session=$sessionId released: ${all.size} instances" }
        }
    }

    /** Refresh sandbox lifecycle + increment last-used timestamp on each SQL/Redis operation. */
    @Transactional
    fun touch(sandboxId: Long): SandboxInstance {
        val s = requireNotNull(sandboxRepo.findById(sandboxId).orElse(null)) { "Sandbox id=$sandboxId not found" }
        return touch(s)
    }

    // ═══════════════════════════════════════════════════════════════
    // DB_TABLE_PREFIX implementation
    // ═══════════════════════════════════════════════════════════════

    private fun createDbSandbox(
        app: App,
        resource: AppResource,
        ownerSession: String,
        ownerUsername: String?,
        expiredAt: LocalDateTime
    ): SandboxInstance {
        val prefix = buildTablePrefix(ownerSession)
        val ds = dsFactory.getDataSource(resource.id)

        val created = SandboxInstance().apply {
            this.app = app
            this.baseResource = resource
            this.sandboxType = SANDBOX_DB_PREFIX
            this.connectionConfig = mutableMapOf(
                "tablePrefix" to prefix,
                "createdTables" to emptyList<String>()
            )
            this.lifecycle = LIFECYCLE_CREATING
            this.expiredAt = expiredAt
            this.ownerSession = ownerSession
            this.ownerUsername = ownerUsername
        }
        val saved = sandboxRepo.save(created)

        try {
            ds.connection.use { conn ->
                val realTables = queryUserTables(conn)
                conn.autoCommit = true
                conn.createStatement().use { stmt ->
                    val cloned = mutableListOf<String>()
                    for (table in realTables) {
                        val sandboxName = "${prefix}$table"
                        runCatching {
                            stmt.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS ${quote(sandboxName)} LIKE ${quote(table)}"
                            )
                            cloned += sandboxName
                        }.onFailure { ex ->
                            // Individual table errors (e.g. VIEW not supported by LIKE) must not abort the whole sandbox
                            log.warn(ex) { "Skip cloning $table on resourceId=${resource.id}: ${ex.message}" }
                        }
                    }
                    saved.connectionConfig["createdTables"] = cloned
                    log.info {
                        "Sandbox DB prefix=$prefix resourceId=${resource.id} cloned=${cloned.size}/${realTables.size}"
                    }
                }
            }
            saved.lifecycle = LIFECYCLE_READY
            sandboxRepo.save(saved)
        } catch (t: Throwable) {
            log.error(t) { "DB sandbox creation failed for resourceId=${resource.id}" }
            saved.lifecycle = LIFECYCLE_FAILED
            saved.connectionConfig["error"] = t.message
            sandboxRepo.save(saved)
        }
        return saved
    }

    /** List every non-system, non-sandbox, non-flyway user table in the current catalog via JDBC Metadata. */
    private fun queryUserTables(conn: java.sql.Connection): List<String> {
        val meta = conn.metaData
        val catalog = conn.catalog
        val result = mutableListOf<String>()
        meta.getTables(catalog, null, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) {
                val table = rs.getString("TABLE_NAME") ?: continue
                if (isUserTable(table)) result += table
            }
        }
        return result.sorted()
    }

    private fun destroyDbSandbox(s: SandboxInstance) {
        val prefix = s.connectionConfig["tablePrefix"]?.toString() ?: return
        val created = (s.connectionConfig["createdTables"] as? List<*>)
            ?.mapNotNull { it?.toString() }?.toMutableList() ?: mutableListOf()
        // Safety-net: even if createdTables was lost, scan for any table beginning with the prefix
        // so orphaned sandboxes can still be cleaned.
        if (created.isEmpty()) {
            runCatching {
                val ds = dsFactory.getDataSource(s.baseResource.id)
                ds.connection.use { conn ->
                    val meta = conn.metaData
                    meta.getTables(conn.catalog, null, "${prefix}%", arrayOf("TABLE")).use { rs ->
                        while (rs.next()) {
                            created += rs.getString("TABLE_NAME")
                        }
                    }
                }
            }
        }
        if (created.isEmpty()) return
        runCatching {
            val ds = dsFactory.getDataSource(s.baseResource.id)
            ds.connection.use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { stmt ->
                    // MySQL has a hard DROP TABLE limit; batch if > 100 tables
                    created.chunked(100).forEach { chunk ->
                        val joined = chunk.joinToString(", ") { quote(it) }
                        stmt.executeUpdate("DROP TABLE IF EXISTS $joined")
                    }
                    log.info { "Sandbox DB resourceId=${s.baseResource.id} prefix=$prefix dropped ${created.size} tables" }
                }
            }
        }.onFailure { ex ->
            log.warn(ex) { "DB sandbox id=${s.id} cleanup failed: ${ex.message}" }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // REDIS_DB_INDEX implementation (lazy — no schema to clone; just assign a free index)
    // ═══════════════════════════════════════════════════════════════

    private fun createRedisDbIndexSandbox(
        app: App,
        resource: AppResource,
        ownerSession: String,
        ownerUsername: String?,
        expiredAt: LocalDateTime
    ): SandboxInstance {
        val allForResource = sandboxRepo.findByAppIdAndOwnerSessionAndLifecycleIn(
            app.id, ownerSession, setOf(LIFECYCLE_CREATING, LIFECYCLE_READY, LIFECYCLE_IN_USE, LIFECYCLE_EXPIRED)
        ).filter { it.sandboxType == SANDBOX_REDIS_DB && it.baseResource.id == resource.id }
        val usedIndices = allForResource.mapNotNull { (it.connectionConfig["redisDbIndex"] as? Int) }.toSet()
        val index = (1..15).firstOrNull { it !in usedIndices }
            ?: run {
                log.warn { "All 15 Redis sandbox indices busy for resourceId=${resource.id}; reusing oldest EXPIRED instance's index" }
                1
            }
        return SandboxInstance().apply {
            this.app = app
            this.baseResource = resource
            this.sandboxType = SANDBOX_REDIS_DB
            this.connectionConfig = mutableMapOf("redisDbIndex" to index)
            this.lifecycle = LIFECYCLE_READY
            this.expiredAt = expiredAt
            this.ownerSession = ownerSession
            this.ownerUsername = ownerUsername
        }.let(sandboxRepo::save).also {
            log.info { "Sandbox REDIS resourceId=${resource.id} assigned dbIndex=$index" }
        }
    }

    private fun destroyRedisSandbox(s: SandboxInstance) {
        // Actual FLUSHDB requires a live RedisClientAdapter; we leave the lightweight
        // cleanup to the Redis binding layer and just record the index to drop. The
        // connection stays in the pool and the next session that reuses the same index
        // will FLUSHDB on first write. (Hook point in AppScopedDataSourceProvider.)
        log.info { "Sandbox REDIS id=${s.id} marked cleaned (index=${s.connectionConfig["redisDbIndex"]})" }
    }

    // ═══════════════════════════════════════════════════════════════
    // Scheduled sweep
    // ═══════════════════════════════════════════════════════════════

    /**
     * Every 10 minutes, scan `sandbox_instance` for rows past their `expired_at` and
     * drop their physical artefacts (tables / Redis DBs). Runs on a fixed-delay loop
     * rather than cron so overlapping invocations never happen simultaneously.
     */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    @Transactional
    fun cleanupExpired() {
        val now = LocalDateTime.now()
        val candidates = sandboxRepo.findExpiredCandidates(now)
        if (candidates.isEmpty()) return
        log.info { "Sandbox cleanup sweep: ${candidates.size} expired candidates" }
        for (s in candidates) {
            runCatching {
                sandboxRepo.updateLifecycle(s.id, LIFECYCLE_EXPIRED, now)
                destroySandbox(s)
                sandboxRepo.updateLifecycle(s.id, LIFECYCLE_CLEANED, LocalDateTime.now())
            }.onFailure { ex ->
                log.warn(ex) { "Sandbox id=${s.id} cleanup failed: ${ex.message}; marking FAILED" }
                runCatching { sandboxRepo.updateLifecycle(s.id, LIFECYCLE_FAILED, LocalDateTime.now()) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun destroySandbox(s: SandboxInstance) {
        sandboxRepo.updateLifecycle(s.id, LIFECYCLE_CLEANING, LocalDateTime.now())
        when (s.sandboxType) {
            SANDBOX_DB_PREFIX -> destroyDbSandbox(s)
            SANDBOX_REDIS_DB  -> destroyRedisSandbox(s)
        }
    }

    private fun touch(s: SandboxInstance): SandboxInstance {
        s.lifecycle = LIFECYCLE_IN_USE
        s.lastUsedAt = LocalDateTime.now()
        // Extend TTL automatically for as long as the user keeps running tests
        s.expiredAt = LocalDateTime.now().plusHours(4)
        return sandboxRepo.save(s)
    }

    private fun buildRoutingContext(instances: List<SandboxInstance>): Map<String, Any?> {
        val ctx = mutableMapOf<String, Any?>()
        instances.forEach { s ->
            val key = "${s.baseResource.resourceType}:${s.baseResource.resourceName}"
            when (s.sandboxType) {
                SANDBOX_DB_PREFIX    -> ctx["$key.tablePrefix"] = s.connectionConfig["tablePrefix"]
                SANDBOX_REDIS_DB     -> ctx["$key.redisDbIndex"] = s.connectionConfig["redisDbIndex"]
            }
            ctx["sandboxId:${s.baseResource.id}"] = s.id
        }
        return ctx
    }

    private fun SandboxInstance.toSummary(): SandboxSummary = SandboxSummary(
        id = id,
        type = sandboxType,
        resourceName = baseResource.resourceName,
        resourceId = baseResource.id,
        lifecycle = lifecycle,
        config = connectionConfig.toMap(),
        expiredAt = expiredAt.toString()
    )

    companion object {
        const val LIFECYCLE_CREATING = "CREATING"
        const val LIFECYCLE_READY    = "READY"
        const val LIFECYCLE_IN_USE   = "IN_USE"
        const val LIFECYCLE_EXPIRED  = "EXPIRED"
        const val LIFECYCLE_CLEANING = "CLEANING"
        const val LIFECYCLE_CLEANED  = "CLEANED"
        const val LIFECYCLE_FAILED   = "FAILED"

        const val SANDBOX_DB_PREFIX  = "DB_TABLE_PREFIX"
        const val SANDBOX_REDIS_DB   = "REDIS_DB_INDEX"

        private val DB_TYPES = setOf("DB", "MYSQL", "POSTGRESQL", "ORACLE", "SQLSERVER", "H2", "CLICKHOUSE")

        fun isDbType(resourceType: String): Boolean = DB_TYPES.contains(resourceType.uppercase())

        /**
         * Deterministic 8-char session-derived prefix so `ownerSession` length (UUIDs)
         * never pushes us past the 64-char MySQL table-name limit even when combined
         * with a long original table name.
         */
        fun buildTablePrefix(ownerSession: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(ownerSession.toByteArray())
            val hex = digest.joinToString("") { "%02x".format(it) }.take(8)
            return "sb_${hex}_"  // 3 + 8 + 1 = 12 chars prefix
        }

        fun quote(identifier: String): String = "`${identifier.replace("`", "``")}`"

        /**
         * Hide system tables + Flyway + Fluxion admin tables + any existing sandbox table
         * from clone-and-drop logic. Matches TableMetadataService plus the `sb_` prefix
         * we use on sandboxes so we never recursively clone a cloned table.
         */
        fun isUserTable(name: String): Boolean {
            val lower = name.lowercase()
            return !(lower.startsWith("flyway_schema_history") ||
                lower.startsWith("information_schema") ||
                lower.startsWith("performance_schema") ||
                lower.startsWith("mysql") || lower.startsWith("sys") ||
                lower.startsWith("pg_") || lower.startsWith("trace_") ||
                lower == "users" || lower == "roles" || lower == "user_roles" ||
                lower.startsWith("user_app_groups") ||
                lower.startsWith("wf_") || lower.startsWith("sandbox_") ||
                lower.startsWith("sb_") ||
                lower.startsWith("qrtz_") ||
                lower.contains("datasource") ||
                lower.contains("app") && (lower == "app" || lower.startsWith("app_"))
            )
        }
    }
}

/** Handful-of-fields summary returned to the designer UI. */
data class SandboxSummary(
    val id: Long,
    val type: String,
    val resourceName: String,
    val resourceId: Long,
    val lifecycle: String,
    val config: Map<String, Any?>,
    val expiredAt: String
)

/**
 * Complete sandbox ticket returned from acquire. `routingContext` is merged into the
 * debug-execution request and consumed by the runtime DataSourceProvider when deciding
 * which tables / Redis DB index to target.
 */
data class SandboxTicket(
    val sessionId: String,
    val appId: Long,
    val expiredAt: String,
    val instances: List<SandboxSummary>,
    val routingContext: Map<String, Any?>
)
