// fluxion-mock - Mock engine for workflow testing and development.
// Provides MockEngine that executes workflow DAGs against declared mock responses using
// AviatorScript expression evaluation, enabling deterministic testing without real dependencies.
// Key plugins: Kotlin JVM + java-library.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Function abstraction (MockFunctionComponent implements FunctionComponent SPI).
    api(project(":fluxion-function"))
    // Core base types - NodeInput/FunctionResult shared with engine.
    api(project(":fluxion-core"))

    // Unified logging extensions.
    implementation(project(":fluxion-log"))
    // AviatorScript expression engine for dynamic mock response evaluation (zero Spring deps).
    implementation("com.googlecode.aviator:aviator:5.4.3")

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
