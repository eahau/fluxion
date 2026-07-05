plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    // id("org.graalvm.buildtools.native")  // 暂时关闭，日常开发不需要 AOT
}

dependencies {
    // Runtime Spring Boot 装配层
    implementation(project(":fluxion-runtime:spring-boot"))

    // 协议适配器：HTTP（必选）、RPC / MQ（按需启用）
    implementation(project(":fluxion-adapter-http:springmvc:spring-boot"))
    implementation(project(":fluxion-adapter-rpc:spring-boot"))
    implementation(project(":fluxion-adapter-mq:spring-boot"))

    // 函数能力域
    implementation(project(":fluxion-builtin-functions:spring-boot"))
    implementation(project(":fluxion-script-engine:spring-boot"))
    implementation(project(":fluxion-external-function-dubbo:spring-boot"))
    implementation(project(":fluxion-external-function-grpc:spring-boot"))
    implementation(project(":fluxion-external-function-http:spring-boot"))

    // 横切能力
    implementation(project(":fluxion-decorator-impl:spring-boot"))

    // 依赖注入
    implementation(project(":fluxion-di:spring"))

    // 配置中心（HTTP 模式）
    implementation(project(":fluxion-config:spring-boot"))
    implementation(project(":fluxion-config:http"))
    // 注册中心（HTTP 自举模式）
    implementation(project(":fluxion-config:registry-http"))

    // Spring Boot 基础
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    runtimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")  // Log4j2 YAML 配置解析
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 可观测性（可选）
    // implementation("io.micrometer:micrometer-registry-prometheus")

    // 测试
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// 支持 `-Pdebug` 开启远程调试，默认端口 5005
tasks.bootRun {
    if (project.hasProperty("debug")) {
        jvmArgs = listOf(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
        )
    }
}

// ============ GraalVM Native Image 配置（暂时关闭）============
// 同时兼容传统 JDK 和 GraalVM Native Image 两种构建模式：
//   - 传统 JDK：./gradlew bootJar → 产出 fat JAR
//   - Native Image：./gradlew nativeCompile → 产出原生二进制
// graalvmNative {
//     binaries {
//         named("main") {
//             imageName.set("fluxion-runtime")
//             buildArgs.addAll(
//                 "-O2",
//                 "--no-fallback",
//                 "-H:+ReportExceptionStackTraces"
//             )
//         }
//     }
// }
