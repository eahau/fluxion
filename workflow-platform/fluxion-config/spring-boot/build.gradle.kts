plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java`
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(project(":fluxion-adapter-spi"))
    implementation(project(":fluxion-config:core"))
    implementation(project(":fluxion-core"))  // JsonUtil：AutoConfiguration 中声明式配置解析

    // 配置中心实现（compileOnly：由最终应用按需选择 Apollo / Nacos / HTTP）
    compileOnly(project(":fluxion-config:apollo"))
    compileOnly(project(":fluxion-config:nacos"))
    compileOnly(project(":fluxion-config:http"))

    // 注册中心实现（compileOnly：由最终应用按需选择 Nacos / HTTP）
    compileOnly(project(":fluxion-config:registry-http"))

    // 配置中心 SDK（compileOnly：由最终应用按需选择）
    compileOnly("com.ctrip.framework.apollo:apollo-client")
    compileOnly("com.ctrip.framework.apollo:apollo-openapi")
    compileOnly("com.alibaba.nacos:nacos-client")

    // Spring Boot / Spring Web（由最终 Spring 应用提供）
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.slf4j:slf4j-api")
}
