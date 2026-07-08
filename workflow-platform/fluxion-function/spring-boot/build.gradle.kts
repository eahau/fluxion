// fluxion-function:spring-boot - Spring Boot auto-configuration for function subsystem.
// Registers FunctionRegistry, FunctionInstanceProvider, FunctionMetaManager beans in the
// ApplicationContext, bridges config subscriber events from adapter-spi, and applies
// configuration-processor annotation processing.
// Key plugins: Kotlin JVM + Kotlin Spring plugin (@Configuration/@AutoConfiguration support).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Function core SPI: FunctionComponent / FunctionRegistry / WorkflowFunction / FunctionInstanceProvider.
    api(project(":fluxion-function"))
    // Debug module: dependency resolver implementations.
    implementation(project(":fluxion-debug"))
    // Function metadata SPI: FunctionMetaManager / FunctionMetaRegistry (merged into this starter).
    api(project(":fluxion-function:meta"))

    // Adapter SPI: FunctionConfigSubscriber / FunctionChangeListener (compileOnly;
    // only required when adapter-spi is present on consumer classpath).
    compileOnly(project(":fluxion-adapter-spi"))

    // Spring Boot AutoConfigure mechanism + metadata processor.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Unified logging.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
