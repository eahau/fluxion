plugins {
    kotlin("jvm")
}

dependencies {
    // workflow-adapter-http-core（RouteConfigStore SPI + HttpRouteDefinition）
    implementation(project(":fluxion-adapter-http:core"))

    // Nacos Config Client（纯 SDK，零 Spring Cloud）
    implementation("com.alibaba.nacos:nacos-client")

    // Jackson — JSON 解析路由配置
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SLF4J
    implementation("org.slf4j:slf4j-api")
}

