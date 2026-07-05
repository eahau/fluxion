dependencies {
    // workflow-redis-core（SPI + RedisCommandFunction）
    // api: workflow-admin 等模块需要访问 RedisClientAdapter SPI
    api(project(":fluxion-redis:core"))

    // workflow-core（FunctionRegistry）
    implementation(project(":fluxion-core"))

    // 客户端适配器实现（compileOnly：由最终应用按需选择 Lettuce / Redisson）
    compileOnly(project(":fluxion-redis:lettuce"))
    compileOnly(project(":fluxion-redis:redisson"))

    // 客户端库（compileOnly：由最终应用按需选择）
    compileOnly("io.lettuce:lettuce-core")
    compileOnly("org.redisson:redisson")

    // Spring Boot AutoConfigure（@AutoConfiguration / @ConditionalOnBean）
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // SLF4J
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
