dependencies {
    // 仅依赖 workflow-core 中的基础类型（如需要），自身零框架依赖
    api(project(":fluxion-core"))

    // Adapter SPI: UnifiedRequest / WorkflowRouter — HttpRequestProcessor uses these
    api(project(":fluxion-adapter-spi"))

    // Jackson — 仅用于路由定义的序列化/反序列化契约
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // SLF4J
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
