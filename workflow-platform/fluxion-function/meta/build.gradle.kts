// fluxion-function:meta - Function metadata management SPI module.
// Provides FunctionMetaManager, FunctionMetaRegistry, and metadata snapshot types for
// function description, parameter schema, and dynamic function configuration change events.
plugins {
    kotlin("jvm")
}

dependencies {
    // Core module provides FunctionMeta base type definition package.
    implementation(project(":fluxion-core"))

    // Adapter SPI: FunctionConfigSubscriber / ChangeType / FunctionConfigSnapshot (compileOnly -
    // avoids forcing adapter-spi onto consumers that do not require config subscriber bridge).
    compileOnly(project(":fluxion-config:core"))

    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
