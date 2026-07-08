// fluxion-redis:lettuce - Lettuce-based Redis client adapter implementation.
// Implements RedisClientAdapter SPI using Lettuce (Netty-based async Redis client), version
// managed transitively by Spring Boot BOM (6.x line). Zero Spring dependencies.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Redis core SPI: RedisClientAdapter interface + RedisRawCommand types.
    implementation(project(":fluxion-redis:core"))

    // Lettuce Core - async/thread-safe Redis client (BOM version from Spring Boot dep management).
    implementation("io.lettuce:lettuce-core")

    // SLF4J logging facade.
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
