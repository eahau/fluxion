plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // ============ 项目模块依赖 ============
    // Runtime 核心（框架无关的执行引擎）
    implementation(project(":fluxion-runtime:core"))
    // Runtime Spring Boot 自动装配层
    implementation(project(":fluxion-runtime:spring-boot"))
    // 核心引擎 Spring Boot 装配
    implementation(project(":fluxion-core-spring-boot"))
    // 适配器 SPI
    implementation(project(":fluxion-adapter-spi"))
    // HTTP 适配器核心
    implementation(project(":fluxion-adapter-http:core"))
    // HTTP WebFlux 适配器
    implementation(project(":fluxion-adapter-http:webflux"))
    // 内置函数
    implementation(project(":fluxion-builtin-functions:spring-boot"))
    // 依赖注入
    implementation(project(":fluxion-di:spring"))
    // 脚本引擎
    implementation(project(":fluxion-script-engine:spring-boot"))
    // 横切能力
    implementation(project(":fluxion-decorator-impl:spring-boot"))
    // 配置中心
    implementation(project(":fluxion-config:spring-boot"))
    implementation(project(":fluxion-config:http"))          // HTTP 自举模式（type=http 时生效）
    implementation(project(":fluxion-config:nacos"))         // Nacos 配置中心（type=nacos 时生效）
    // 注册中心
    implementation(project(":fluxion-config:registry-http")) // HTTP 自举注册中心（type=http 时生效）
    // Nacos 路由配置存储（框架无关，复用 springmvc:nacos 中的 NacosRouteConfigStore 实现）
    implementation(project(":fluxion-adapter-http:springmvc:nacos"))
    // Nacos Client（nacos 模式运行时必需）
    implementation("com.alibaba.nacos:nacos-client")

    // ============ Spring Boot 核心 ============
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    runtimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")  // Log4j2 YAML 配置解析
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin Coroutines reactive bridge
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // ============ 测试 ============
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.bootRun {
    if (project.hasProperty("debug")) {
        jvmArgs = listOf(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5006"
        )
    }
}
