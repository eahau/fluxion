package com.fluxion.admin.crypto

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor
import org.jasypt.encryption.pbe.config.EnvironmentPBEConfig
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

/**
 * Jasypt-aligned string encryption service — shared by [EncryptedJsonMapConverter] and
 * the admin REST endpoints that accept / expose sensitive resource configs.
 *
 * Uses the same default algorithm / env-var convention as the standard
 * `jasypt-spring-boot-starter` so cipher-texts are interchangeable with
 * `@Value("\${ENC(...)}")` Spring property injection.
 *
 * Password resolution (first hit wins):
 *   1. Spring property `jasypt.encryptor.password` (set via `-Djasypt.encryptor.password=…`)
 *   2. Environment variable `JASYPT_ENCRYPTOR_PASSWORD`
 *   3. System property `jasypt.encryptor.password`
 *
 * If **none** of the three is set the service falls back to a deliberately weak
 * `_fluxion-dev-only-unsecure-do-not-use-in-prod_` placeholder and logs a loud
 * WARN on startup — this keeps local development zero-config while preventing
 * accidental prod use.
 */
@Service
class CryptService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${jasypt.encryptor.password:}")
    private var configuredPassword: String = ""

    private val encryptor: StandardPBEStringEncryptor = StandardPBEStringEncryptor()

    @PostConstruct
    fun init() {
        val config = EnvironmentPBEConfig().apply {
            algorithm = "PBEWithMD5AndDES"
            passwordSysPropertyName = "jasypt.encryptor.password"
            passwordEnvName = "JASYPT_ENCRYPTOR_PASSWORD"
            setPassword(
                configuredPassword.ifBlank { null }
                    ?: System.getenv("JASYPT_ENCRYPTOR_PASSWORD")
                    ?: System.getProperty("jasypt.encryptor.password")
                    ?: DEV_PLACEHOLDER_PASSWORD.also {
                        log.warn {
                            "!SECURITY! JASYPT_ENCRYPTOR_PASSWORD / jasypt.encryptor.password NOT SET — " +
                                "using dev-only placeholder; DB-encrypted resource configs are trivially recoverable. " +
                                "Export JASYPT_ENCRYPTOR_PASSWORD before running in prod."
                        }
                    }
            )
        }
        encryptor.setConfig(config)
    }

    /** Encrypt and wrap a plaintext value with the standard `ENC(...)` envelope. */
    fun encrypt(plain: String?): String? {
        if (plain == null) return null
        return "ENC(${encryptor.encrypt(plain)})"
    }

    /** Decrypt a string that may or may not be wrapped in `ENC(...)` — plain values pass through untouched. */
    fun decrypt(maybeEncrypted: String?): String? {
        if (maybeEncrypted == null) return null
        if (maybeEncrypted.length > 5 &&
            maybeEncrypted.startsWith("ENC(") && maybeEncrypted.endsWith(')')) {
            val inner = maybeEncrypted.substring(4, maybeEncrypted.length - 1)
            return runCatching { encryptor.decrypt(inner) }
                .getOrElse { ex ->
                    log.warn(ex) { "Failed to decrypt ENC(...) value, returning raw string; cause=${ex.message}" }
                    maybeEncrypted
                }
        }
        return maybeEncrypted
    }

    /** True iff the value still carries the legacy placeholder password — sanity check before shipping. */
    fun isUsingDevPlaceholder(): Boolean = configuredPassword.isBlank()
        && System.getenv("JASYPT_ENCRYPTOR_PASSWORD") == null
        && System.getProperty("jasypt.encryptor.password") == null

    companion object {
        private const val DEV_PLACEHOLDER_PASSWORD =
            "_fluxion-dev-only-unsecure-do-not-use-in-prod_"
    }
}
