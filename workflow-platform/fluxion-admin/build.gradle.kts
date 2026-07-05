plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.openapi.generator")
    // id("org.graalvm.buildtools.native")  // 暂时关闭，日常开发不需要 AOT
}

// 禁用 Maven exclusion 自动应用，避免与 bootJar 的 runtimeClasspath 解析冲突
the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().applyMavenExclusions(false)

// ============ OpenAPI Generator 配置 ============
// 从 openapi.yaml 生成 Kotlin DTO 和 API 接口
val openApiInputFile = file("${rootProject.projectDir}/../doc/openapi.yaml")
val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set(openApiInputFile.absolutePath)
    outputDir.set(openApiOutputDir.get().asFile.absolutePath)
    validateSpec.set(false)  // 跳过 spec 校验，避免 YAML 解析器 maxDepth 限制
    apiPackage.set("com.fluxion.admin.generated.api")
    modelPackage.set("com.fluxion.admin.generated.model")
    packageName.set("com.fluxion.admin.generated")
    configOptions.set(mapOf(
        "interfaceOnly" to "true",           // 只生成接口，不生成实现
        "skipDefaultInterface" to "false",   // 生成默认方法（返回 501 NOT_IMPLEMENTED）
        "useTags" to "true",                  // 按 tag 分组生成 API
        "dateLibrary" to "java8",
        "useSpringBoot3" to "true",
        "documentationProvider" to "none",    // 不生成 Swagger 文档注解
        "enumPropertyNaming" to "UPPERCASE",
        "serializableModel" to "true",
        "openApiNullable" to "false",         // 禁用 openapi-nullable
        "modelMutable" to "true"              // 生成 var 属性，保持 .apply {} 模式兼容
    ))
    // 生成 model + API
    globalProperties.set(mapOf(
        "models" to "",
        "apis" to ""
    ))
}

// ============ OpenAPI 后处理：为 required 字段补充 @field:NotNull ============
// kotlin-spring 生成器对 required 字段只生成 @JsonProperty(required=true)，
// 不自动添加 @NotNull。此处自动补全 JSR-303 非空校验注解。
tasks.named("openApiGenerate") {
    doLast {
        val modelDir = openApiOutputDir.get().asFile.resolve("src/main/kotlin")
            .resolve("com/fluxion/admin/generated/model")
        if (!modelDir.exists()) return@doLast
        modelDir.listFiles { f -> f.extension == "kt" }?.forEach { file ->
            var text = file.readText()
            // 先移除已有的 @field:NotNull（防止重复生成时叠加）
            text = text.replace(Regex("""\s*@field:NotNull\n"""), "\n")
            // 为 required = true 的属性注入 @field:NotNull
            text = text.replace(
                Regex("""(\s*)(@get:JsonProperty\("[^"]+", required = true\))"""),
                "$1@field:NotNull\n$1$2"
            )
            file.writeText(text)
        }
    }
}

// 将生成的代码添加到源码目录，并确保 openApiGenerate 先执行
tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

sourceSets {
    main {
        kotlin {
            srcDir(openApiOutputDir.map { it.dir("src/main/kotlin") })
        }
    }
}

dependencies {
    // ============ 项目模块依赖 ============
    implementation(project(":fluxion-acl-spi"))
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-core-spring-boot"))
    implementation(project(":fluxion-schema:core"))
    implementation(project(":fluxion-schema:spring-boot"))
    implementation(project(":fluxion-decorator-impl:spring-boot"))
    implementation(project(":fluxion-adapter-spi"))
    implementation(project(":fluxion-runtime:core"))
    implementation(project(":fluxion-adapter-http:core"))
    implementation(project(":fluxion-adapter-http:springmvc:spring-boot"))
    // 内置函数（spring-boot 已传递依赖 core）
    implementation(project(":fluxion-builtin-functions:spring-boot"))
    // 依赖注入能力域
    implementation(project(":fluxion-di:spring"))
    // Redis（spring-boot 已传递依赖 core）
    implementation(project(":fluxion-redis:spring-boot"))
    implementation(project(":fluxion-redis:lettuce"))
    // 脚本引擎（spring-boot 已传递依赖 core）
    implementation(project(":fluxion-script-engine:spring-boot"))
    // 配置中心
    implementation(project(":fluxion-config:spring-boot"))
    implementation(project(":fluxion-config:http"))          // HTTP 自举模式（workflow.config.type=http 时生效）
    implementation(project(":fluxion-config:nacos"))         // Nacos 配置中心（workflow.config.type=nacos 时生效）
    // 注册中心
    implementation(project(":fluxion-config:registry-http")) // HTTP 自举注册中心（type=http 时生效）
    // Nacos Client（nacos 模式运行时必需）
    runtimeOnly("com.alibaba.nacos:nacos-client")

    // ============ Spring Boot 核心 ============
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    runtimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")  // Log4j2 YAML 配置解析
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") {
        // 纯注解 JPA，无需 XML 映射，排除 JAXB 运行时（glassfish.jaxb）
        exclude(group = "org.glassfish.jaxb", module = "jaxb-runtime")
    }
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // ============ 可选：第三方认证集成（按需启用）============
    // LDAP - 企业内部统一账号
    // implementation("org.springframework.boot:spring-boot-starter-data-ldap")
    // implementation("org.springframework.security:spring-security-ldap")
    // OAuth2 - 第三方登录
    // implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // ============ 监控（默认启用：追踪 + metrics）============
    // Prometheus 监控
    // implementation("io.micrometer:micrometer-registry-prometheus")
    // OpenTelemetry 分布式追踪（Spring Boot 3.x + Jaeger 走 OTLP HTTP 协议，默认 http://localhost:4318/v1/traces）
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // ============ 工具库 ============
    // JSR-305（提供 javax.annotation.meta.When 等元注解，消除 kapt 阶段的编译警告）
    compileOnly("com.google.code.findbugs:jsr305")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // ============ 数据库 ============
    // Flyway 数据库迁移
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    // MySQL 驱动
    runtimeOnly("com.mysql:mysql-connector-j")

    // ============ JWT ============
    implementation("io.jsonwebtoken:jjwt-api")
    runtimeOnly("io.jsonwebtoken:jjwt-impl")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson")

    // ============ 测试 ============
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// 多个子模块共享 "core" artifact 名称（如 fluxion-schema:core / fluxion-adapter-http:core），
// Spring Boot fat JAR 打包时可能出现同名 JAR，使用 EXCLUDE 策略保留首个。
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// 支持 `-Pdebug` 开启远程调试，默认端口 5005
tasks.bootRun {
    val baseJvmArgs = mutableListOf<String>()
    // 禁用 DevTools Restart Classloader（避免重启后 fork 进程丢失 Web 容器/端口监听）
    baseJvmArgs += "-Dspring.devtools.restart.enabled=false"
    if (project.hasProperty("debug")) {
        baseJvmArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
    }
    if (baseJvmArgs.isNotEmpty()) {
        jvmArgs = baseJvmArgs
    }
}

// ============ GraalVM Native Image 配置（暂时关闭）============
// 同时兼容传统 JDK 和 GraalVM Native Image 两种构建模式：
//   - 传统 JDK：./gradlew bootJar → 产出 fat JAR
//   - Native Image：./gradlew nativeCompile → 产出原生二进制
// graalvmNative {
//     binaries {
//         named("main") {
//             imageName.set("fluxion-admin")
//             buildArgs.addAll(
//                 "-O2",
//                 "--no-fallback",
//                 "-H:+ReportExceptionStackTraces"
//             )
//         }
//     }
// }
