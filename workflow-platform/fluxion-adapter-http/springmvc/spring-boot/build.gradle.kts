plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // SPI & 核心抽象
    implementation(project(":fluxion-adapter-spi"))
    implementation(project(":fluxion-adapter-http:core"))

    // Spring MVC 适配器核心（handler / registry）
    implementation(project(":fluxion-adapter-http:springmvc"))

    // Apollo / Nacos 路由存储实现：按需选择，compileOnly
    compileOnly(project(":fluxion-adapter-http:springmvc:apollo"))
    compileOnly(project(":fluxion-adapter-http:springmvc:nacos"))

    // Spring Boot 自动装配 + Spring MVC
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Apollo / Nacos Client（compileOnly，由最终应用或 starter 提供具体实现）
    compileOnly("com.ctrip.framework.apollo:apollo-client")
    compileOnly("com.alibaba.nacos:nacos-client")

    // Jackson / Kotlin reflect
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
