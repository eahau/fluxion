// fluxion-redis:spring-data - Spring Data Redis-based client adapter implementation.
// Implements RedisClientAdapter SPI delegating to Spring Data RedisTemplate. Exposes core SPI
// via api; concrete Spring Data Redis starter is provided by end-consuming application.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Redis core SPI (api - downstream modules need compile-time access to adapter types).
    api(project(":fluxion-redis:core"))

    // Spring Data Redis (compileOnly) - concrete spring-boot-starter-data-redis is expected on
    // classpath of end applications; this module only compiles against template SPI interfaces.
    compileOnly("org.springframework.data:spring-data-redis")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

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
