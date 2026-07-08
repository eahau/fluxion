// fluxion-adapter-http:springmvc - Spring MVC (Servlet stack) HTTP adapter implementation.
// Implements HttpRequestProcessor by registering dynamic @RequestMapping handlers at runtime
// via Spring RequestMappingHandlerMapping, and implements HandlerInterceptor for lifecycle hooks.
plugins {
    kotlin("jvm")
}

dependencies {
    // Core base types.
    implementation(project(":fluxion-core"))
    // Adapter SPI (router / request abstractions).
    implementation(project(":fluxion-adapter-spi"))
    // HTTP adapter core: HttpRequestProcessor + RouteConfigStore + route definitions.
    implementation(project(":fluxion-adapter-http:core"))

    // Spring Boot Web Starter (Servlet stack): DispatcherServlet, HandlerMapping, HandlerInterceptor.
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Jackson Kotlin module + Kotlin reflect for HTTP request/response body parsing.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")

    // Spring Boot Test starter for integration testing against MockMvc.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
