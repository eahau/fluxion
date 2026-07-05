plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-adapter-spi"))
    implementation(project(":fluxion-adapter-http:core"))

    // Spring MVC（DispatcherServlet / RequestMappingHandlerMapping / HandlerInterceptor）
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Jackson — 解析请求体
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

