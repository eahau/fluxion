// fluxion-log - Unified Kotlin SLF4J lazy logging extension module.
// Provides top-level `logger()` extension property returning a cached Kotlin logger delegate
// wrapping SLF4J Logger. Consumed via api exposure from fluxion-core and other shared modules.
// Key plugins: Kotlin JVM + java-library (SLF4J API exposed as transitive api dependency).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // SLF4J API - facade-only binding. Actual logging implementation (Log4j2) is provided by
    // consuming applications (Spring Boot starters) or test runtime classpath entries.
    api("org.slf4j:slf4j-api")
}

// Explicit source set registration for build clarity.
sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
