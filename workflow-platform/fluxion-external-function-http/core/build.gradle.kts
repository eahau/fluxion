plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":fluxion-core"))

    // 复用内置 HTTP 函数及其 HttpClientAdapter SPI
    implementation(project(":fluxion-builtin-functions:core"))

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
