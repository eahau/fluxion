// fluxion-di - Framework-agnostic Dependency Injection SPI core module.
// Holds the canonical DI contract interfaces that the engine uses to delegate function
// instance construction and arbitrary bean resolution to a host application container
// (Spring, Guice, Koin, ...) without coupling the engine to any specific framework.
//
// NOTE: This module was previously embedded inside fluxion-debug; it has been extracted
// into its own capability-domain module because DI is a distinct concern from debugging,
// tracing, or execution inspection.
//
// Key types:
//   - DependencyResolver:        resolve named/typed beans from the host DI container.
//   - FunctionInstanceProvider:  SPI for building WorkflowFunction instances through DI.
//   - FunctionInstanceProviderRegistry: global registry consulted when building functions.
//
// Framework-specific integration lives in sibling submodules (e.g. :spring provides the
// ApplicationContext-backed implementation registered via Spring Boot auto-configuration).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // FunctionInstanceProvider / FunctionInstanceProviderRegistry both reference
    // WorkflowFunction, which lives in the function SPI module (package
    // com.fluxion.core.function even though the module coordinate is :fluxion-function).
    api(project(":fluxion-function"))

    // Unified logging extensions (kept as implementation -- SPI interfaces do not leak
    // logger types, but future registry operations may emit warning-level diagnostics).
    implementation(project(":fluxion-log"))
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
