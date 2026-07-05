plugins {
    kotlin("jvm")
}

dependencies {
    // Shared HTTP core: RouteMatch, HttpRequestProcessor, AbstractRouteRegistry
    implementation(project(":fluxion-adapter-http:core"))

    // Spring WebFlux (RouterFunction, ServerRequest, ServerResponse)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Kotlin coroutines reactive bridge: mono { } / flux { }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Kotlin reflection: KFunction.javaMethod for type-safe MethodParameter
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SLF4J
    implementation("org.slf4j:slf4j-api")
}
