plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    api(project(":fluxion-core"))
    api(project(":fluxion-adapter-spi"))
    api(project(":fluxion-builtin-functions:core"))

    // Spring Boot 自动装配
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Spring 事务与 JDBC 数据源代理
    implementation("org.springframework:spring-tx")
    implementation("org.springframework:spring-jdbc")

    // HTTP 调用（OkHttp，自动装配默认 Client）
    implementation("com.squareup.okhttp3:okhttp")

    // Jackson / Kotlin reflect
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
