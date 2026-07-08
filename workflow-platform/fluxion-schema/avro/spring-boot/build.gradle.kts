// fluxion-schema:avro:spring-boot - Spring Boot auto-configuration for Avro schema module.
// Registers Avro SchemaValidator bean and configuration properties for Avro-specific settings
// (schema registry URL, compatibility mode) via spring-boot-configuration-processor.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Avro schema module (api exposure).
    api(project(":fluxion-schema:avro"))

    // Spring Boot AutoConfigure (@AutoConfiguration + @ConditionalOnClass + @ConfigurationProperties).
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Spring Boot Configuration Processor for IDE metadata hints generation.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
