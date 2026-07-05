plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":fluxion-schema:core"))

    // Protobuf Java（DescriptorProtos 解析）
    implementation("com.google.protobuf:protobuf-java")
    implementation("com.google.protobuf:protobuf-java-util")

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
