dependencies {
    // workflow-core SPI（WorkflowFunction / FunctionResult / NodeInput 等）
    // api: lettuce/redisson 子模块需要访问 workflow-core 的 lazy 日志扩展函数
    api(project(":fluxion-core"))

    // SLF4J — 日志 API（无绑定，零框架）
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.networknt:json-schema-validator")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
