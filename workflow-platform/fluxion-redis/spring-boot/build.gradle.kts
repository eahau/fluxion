// fluxion-redis:spring-boot - Spring Boot auto-configuration for Redis capability domain.
// Auto-registers RedisClientAdapter-based RedisCommandFunction bean conditionally on classpath,
// delegating actual client connections to Lettuce/Redisson/Spring-Data adapters (compileOnly).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Redis core SPI: RedisClientAdapter + RedisCommandFunction types (api - downstream consumers
    // get transitive compile access to adapter SPI for customization).
    api(project(":fluxion-redis:core"))

    // Core module (FunctionRegistry registration + lazy logger extension function access).
    implementation(project(":fluxion-core"))

    // Redis client adapter implementations (compileOnly): final applications select exactly one.
    compileOnly(project(":fluxion-redis:lettuce"))
    compileOnly(project(":fluxion-redis:redisson"))

    // Redis client libraries (compileOnly): provided by the end-application classpath.
    compileOnly("io.lettuce:lettuce-core")
    compileOnly("org.redisson:redisson")

    // Spring Boot AutoConfigure mechanism (@AutoConfiguration + @ConditionalOnBean + @ConditionalOnClass).
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
