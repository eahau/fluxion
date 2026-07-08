// fluxion-redis:core - Redis capability domain core SPI module.
// Defines RedisClientAdapter SPI, RedisCommandFunction (built-in workflow function),
// and shared Redis abstractions. Backed by pluggable client adapters (Lettuce / Redisson / Spring Data).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Core SPI modules (api): Lazy logger functions + RedisClientAdapter impl submodules need these types.
    api(project(":fluxion-core"))
    api(project(":fluxion-function"))
    api(project(":fluxion-decorator"))

    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // JSON schema validator used in tests for Redis response schema assertions.
    testImplementation("com.networknt:json-schema-validator")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
