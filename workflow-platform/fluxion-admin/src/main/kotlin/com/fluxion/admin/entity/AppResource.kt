package com.fluxion.admin.entity

import com.fluxion.admin.crypto.EncryptedJsonMapConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Registered infrastructure-resource connection config (DB / REDIS / KAFKA /
 * ROCKETMQ / DUBBO_REGISTRY / NAMING).
 *
 * Sensitive fields (`password`, `saslJaas`, `token`, `privateKey`, ...) inside
 * `configJson` are transparently encrypted/decrypted by [EncryptedJsonMapConverter]
 * using Jasypt PBE aligned with the standard `jasypt-spring-boot` property
 * encoder — the raw database value contains JSON whose sensitive keys have
 * `ENC(...)` wrapped strings and the `_encrypted` marker flag set to `true`.
 *
 * A single resource can be bound to multiple apps via [AppResourceBinding];
 * each binding may assign an `alias_in_app` so the same physical DB shows up
 * as `"default"` in one app and `"analytics"` in another.
 *
 * Source table: `app_resource` (Flyway V15).
 *
 * Collaborates with: AppResourceBinding, SandboxManager (sandbox creation
 * reads base resource config), Worker runtime (pulls decrypted configs to
 * build DataSource / RedisClient beans).
 */
@Entity
@Table(
    name = "app_resource",
    uniqueConstraints = [UniqueConstraint(name = "uk_resource_name", columnNames = ["resource_name"])],
    indexes = [Index(name = "idx_type", columnList = "resource_type")]
)
class AppResource : BaseEntity() {

    /** Resource family — DB / REDIS / KAFKA / ROCKETMQ / DUBBO_REGISTRY / NAMING. */
    @Column(name = "resource_type", nullable = false, length = 16)
    var resourceType: String = ""

    /** Globally-unique logical name, e.g. "order-db", "user-redis", "prod-kafka". */
    @Column(name = "resource_name", nullable = false, length = 64)
    var resourceName: String = ""

    /** Optional driver/impl hint: "mysql8" / "postgres15" / "lettuce" / "redisson". */
    @Column(name = "driver", length = 64)
    var driver: String? = null

    /**
     * Connection config map. Sensitive values are stored as `ENC(cipher)`;
     * the JPA converter strips the wrapper on read and wraps known keys on
     * write so business code always sees the plaintext value.
     *
     * Typical layouts:
     *  - DB:    `{ url, username, password, maxPoolSize, connectionTimeoutMs }`
     *  - REDIS: `{ host, port, password, database, timeoutMs, mode }`
     *  - KAFKA: `{ bootstrapServers, saslMechanism, saslJaas, securityProtocol }`
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "JSON")
    @Convert(converter = EncryptedJsonMapConverter::class)
    var configJson: MutableMap<String, Any?> = mutableMapOf()
}
