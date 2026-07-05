plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-schema:core"))
    implementation(project(":fluxion-schema:json"))
    implementation(project(":fluxion-adapter-spi"))

    // HTTP 调用（OkHttp，零 Spring）
    implementation("com.squareup.okhttp3:okhttp")

    // JSON 处理
    implementation("com.jayway.jsonpath:json-path")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Caffeine 本地缓存（CacheStore 默认实现）
    implementation("com.github.ben-manes.caffeine:caffeine")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
