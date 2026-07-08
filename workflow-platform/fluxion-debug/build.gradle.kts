// fluxion-debug - Debug support and Dependency Injection abstraction module.
// Contains FunctionInstanceProvider, DependencyResolver, and DI bridge interfaces enabling
// pluggable bean resolution strategies (Spring-backed implementation lives in fluxion-di:spring).
// Key plugins: Kotlin JVM + java-library.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Engine core: DAG execution types used by debug workflow inspectors.
    api(project(":fluxion-engine"))
    // Mock engine: referenced by debug-mode mock function resolution.
    api(project(":fluxion-mock"))
    // Function SPI: FunctionComponent lookup interfaces.
    api(project(":fluxion-function"))
    // Core base types: shared context and utility types.
    api(project(":fluxion-core"))

    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

sourceSets {
    main { kotlin { srcDirs("src/main/kotlin") } }
}

tasks.withType<Test> { useJUnitPlatform() }
