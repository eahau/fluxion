plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":fluxion-schema:core"))

    // Jackson（JSON 处理）
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // networknt JSON Schema Validator
    implementation("com.networknt:json-schema-validator")

    // Logging（SLF4J API，无绑定）
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
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
