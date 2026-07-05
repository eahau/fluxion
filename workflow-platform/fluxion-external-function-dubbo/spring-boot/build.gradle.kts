plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-external-function-dubbo:core"))

    // Spring Boot 自动装配
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // 用于 @ConditionalOnClass 判断，不传递 Dubbo 依赖
    compileOnly("org.apache.dubbo:dubbo") {
        exclude(group = "io.netty", module = "netty-all")
    }

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
