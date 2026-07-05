plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":fluxion-schema:core"))

    // Apache Avro（Schema 解析与数据校验）
    implementation("org.apache.avro:avro")

    // Jackson（复用 core 的 JsonUtil）
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Logging
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation(project(":fluxion-schema:json"))
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
