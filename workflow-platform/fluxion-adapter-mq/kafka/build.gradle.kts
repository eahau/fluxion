plugins {
    kotlin("jvm")
}

dependencies {
    // MQ 发布 SPI
    implementation(project(":fluxion-adapter-spi"))

    // 原生 Kafka 客户端（零 Spring）
    implementation("org.apache.kafka:kafka-clients")

    // Jackson — 消息体解析
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
