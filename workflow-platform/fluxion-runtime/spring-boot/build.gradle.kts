plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // Runtime 核心（零 Spring）
    api(project(":fluxion-runtime:core"))

    // 核心引擎 Spring Boot 装配
    implementation(project(":fluxion-core-spring-boot"))

    // 适配器 SPI 与配置中心 Spring Boot 装配
    api(project(":fluxion-adapter-spi"))
    runtimeOnly(project(":fluxion-config:spring-boot"))

    // Spring Boot 自动装配
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Jackson / Kotlin reflect
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SLF4J
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
