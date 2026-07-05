plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":fluxion-core"))

    // Dubbo 核心（零 Spring）
    implementation("org.apache.dubbo:dubbo") {
        exclude(group = "io.netty", module = "netty-all")
    }

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.apache.dubbo:dubbo-qos")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
