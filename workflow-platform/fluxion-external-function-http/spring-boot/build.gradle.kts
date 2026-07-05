plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-core-spring-boot"))
    implementation(project(":fluxion-external-function-http:core"))

    // 复用内置 HTTP 函数（仅编译期需要类，运行时由 fluxion-runtime 引入）
    compileOnly(project(":fluxion-builtin-functions:core"))

    // Spring Boot 自动装配
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":fluxion-builtin-functions:core"))
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
