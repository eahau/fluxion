plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // MQ SPI
    implementation(project(":fluxion-adapter-spi"))

    // Kafka 零 Spring 实现
    implementation(project(":fluxion-adapter-mq:kafka"))

    // Spring Boot 自动装配
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // 原生 Kafka 客户端（用于创建 Producer/Consumer Bean）
    implementation("org.apache.kafka:kafka-clients")

    // Jackson / Kotlin reflect
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
