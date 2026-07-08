// fluxion-schema:protobuf:spring-boot - Spring Boot auto-configuration for Protobuf schema module.
// Registers Protobuf SchemaValidator bean, enables protobuf type conversion support, and
// applies spring-boot-configuration-processor for metadata generation of @ConfigurationProperties classes.
// Key plugins: Kotlin JVM + Kotlin Spring plugin (@Configuration class support).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Protobuf schema module: Schema + validator SPI (api transitive exposure).
    api(project(":fluxion-schema:protobuf"))

    // Spring Boot AutoConfigure - @AutoConfiguration, @ConditionalOnClass, @ConfigurationProperties.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Spring Boot Configuration Processor - generates META-INF configuration metadata for IDE hints.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
