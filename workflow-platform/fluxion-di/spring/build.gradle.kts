plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // DI SPI：使用 api 传递暴露，便于引用方直接使用 FunctionInstanceProvider / DependencyResolver 类型
    api(project(":fluxion-core"))

    // Spring Boot 自动装配
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // SLF4J
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
