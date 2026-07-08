// fluxion-di:spring - Spring Framework-based dependency integration bridge module.
// Bridges fluxion-debug DI abstractions (FunctionInstanceProvider, DependencyResolver) to
// Spring ApplicationContext bean factory for Spring Boot-based runtime environments.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Core base types (api - downstream modules get transitive compile access to core types).
    api(project(":fluxion-core"))
    // Debug/DI abstractions (api - exposes FunctionInstanceProvider/DependencyResolver transitively).
    api(project(":fluxion-debug"))
    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // Spring Boot AutoConfigure mechanism + @Configuration class support.
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Boot Test starter for DI integration tests against Spring TestContext.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
