plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // 执行引擎核心抽象与实现
    api(project(":fluxion-core"))

    // 适配器 SPI：UnifiedRequest / WorkflowRouter / DefinitionProvider
    api(project(":fluxion-adapter-spi"))

    // Kotlin 协程（DagExecutor 为 suspend 函数）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    // JUnit 5 — Router 单元测试
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")

    // 测试 fixtures
    testImplementation(project(":fluxion-test"))
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
