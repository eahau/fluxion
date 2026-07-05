plugins {
    kotlin("jvm")
}

dependencies {
    // Kotlin Coroutines — TaskInterceptor.wrap(CoroutineDispatcher) 在公共 API 中暴露
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // fluxion-schema 数据契约基座（JSON Schema / Protobuf / Avro 抽象层）
    api(project(":fluxion-schema:core"))
    api(project(":fluxion-schema:json"))

    // Caffeine（CaffeineIdempotencyStore 内存幂等缓存，零 Spring 依赖）
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Jackson（序列化，仅作为纯库使用，无 Spring 依赖）
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // AviatorScript（Expression/MockEngine 的表达式引擎，零 Spring 依赖）
    // 替代 JEXL3：JIT 编译为 JVM 字节码，复杂表达式性能 5-20x；缓存编译结果；
    // 内置 string.length / seq.map / math.abs 等 50+ 方法，无需额外注入工具对象。
    implementation("com.googlecode.aviator:aviator:5.4.3")

    // Logging（SLF4J 纯 API，无绑定）
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")  // Log4j2 SLF4J 绑定（测试用）
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
