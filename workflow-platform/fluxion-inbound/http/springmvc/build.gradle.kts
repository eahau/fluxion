// fluxion-inbound:http:springmvc - Spring MVC (Servlet stack) HTTP adapter implementation.
// Implements HttpRequestProcessor by registering dynamic @RequestMapping handlers at runtime
// via Spring RequestMappingHandlerMapping, and implements HandlerInterceptor for lifecycle hooks.
plugins {
    kotlin("jvm")
}

dependencies {
    // Core base types.
    implementation(project(":fluxion-core"))
    // HTTP adapter core: HttpRequestProcessor + RouteConfigStore + route definitions.
    implementation(project(":fluxion-inbound:http:core"))
    implementation(project(":fluxion-inbound:spi"))

    // Spring Boot Web Starter (Servlet stack): DispatcherServlet, HandlerMapping, HandlerInterceptor.
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Jackson Kotlin module + Kotlin reflect for HTTP request/response body parsing.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")

    // Reactive Streams API - required by Spring Framework for suspend function support in MVC.
    implementation("org.reactivestreams:reactive-streams")
    // Reactor Core - required by Spring Framework for internal reactive support.
    implementation("io.projectreactor:reactor-core")
    // Kotlin Coroutines Reactor bridge - required for suspend function support.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Spring Boot Test starter for integration testing against MockMvc.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

