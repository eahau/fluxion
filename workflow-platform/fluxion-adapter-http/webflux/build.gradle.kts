// fluxion-adapter-http:webflux - Spring WebFlux (Reactive stack) HTTP adapter implementation.
// Implements reactive HttpRequestProcessor using RouterFunction dynamic registration and
// non-blocking suspend-based workflow invocation with Kotlin coroutines-reactor bridge.
plugins {
    kotlin("jvm")
}

dependencies {
    // Shared HTTP adapter core: RouteMatch, HttpRequestProcessor, AbstractRouteRegistry.
    implementation(project(":fluxion-adapter-http:core"))

    // Spring Boot WebFlux Starter (Reactive stack): RouterFunction, ServerRequest/Response, WebFilter.
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Kotlin coroutines-to-Reactor bridge: mono { } / flux { } suspend coroutine builder support.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Kotlin reflection: KFunction.javaMethod lookup for type-safe reactive MethodParameter creation.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging facade.
    implementation("org.slf4j:slf4j-api")
}
