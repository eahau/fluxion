// fluxion-redis:redisson - Redisson-based Redis client adapter implementation.
// Implements RedisClientAdapter SPI using Redisson (advanced Redis client providing distributed
// locks, collections, reactive API). Version pinned by root build.gradle.kts dependency management.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Redis core SPI: RedisClientAdapter interface + RedisRawCommand types.
    implementation(project(":fluxion-redis:core"))

    // Redisson client library (version managed centrally in root build.gradle.kts depMgmt section).
    implementation("org.redisson:redisson")

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
