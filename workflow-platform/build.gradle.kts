import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

// 版本变量必须在 plugins 块之后、subprojects 之前声明，
// plugins 块内部不支持引用外部 val，需保留字面量。
// 如需统一管理插件版本，可迁移到 settings.gradle.kts 的 pluginManagement 块。
plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.spring") version "2.1.20" apply false
    kotlin("plugin.jpa") version "2.1.20" apply false
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("org.openapi.generator") version "7.12.0" apply false
    id("org.graalvm.buildtools.native") version "0.10.4" apply false
    java
}

val javaVersion                  = JavaVersion.VERSION_21
val kotlinVersion                = "2.1.20"
val kotlinCoroutinesVersion      = "1.9.0"
val springBootVersion            = "3.2.5"
val springDependencyMgmtVersion  = "1.1.5"
val guavaVersion                 = "33.2.1-jre"
val jsonSchemaValidatorVersion   = "1.4.0"
val jsonPathVersion              = "2.9.0"
val jexl3Version                 = "3.3"
val okhttpVersion                = "4.12.0"
val groovyVersion                = "4.0.21"
val redissonVersion              = "3.30.0"
val dubboVersion                 = "3.2.13"
val grpcVersion                  = "1.64.0"
val grpcSpringBootVersion        = "3.0.0.RELEASE"
val protobufPluginVersion        = "0.9.4"
val nacosClientVersion           = "2.3.3"
val apolloClientVersion          = "2.2.0"
val jjwtVersion                  = "0.12.5"
val javaxAnnotationApiVersion    = "1.3.2"
val jsr305Version                = "3.0.2"
val jetbrainsAnnotationsVersion  = "23.0.0"
val protobufBomVersion           = "3.25.3"
val avroVersion                  = "1.11.3"
val graalVmBuildToolsVersion     = "0.10.4"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")
    if (name != "fluxion-admin") {
        apply(plugin = "java-library")
    }
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "maven-publish")

    // 纯 Kotlin 模块没有 Java 源文件，禁用空的 compileJava 任务以节省构建时间。
    afterEvaluate {
        val hasJavaSources = sourceSets["main"].java.srcDirs.any { dir ->
            fileTree(dir).matching { include("**/*.java") }.files.isNotEmpty()
        }
        if (!hasJavaSources) {
            tasks.withType<JavaCompile>().configureEach {
                enabled = false
            }
        }
    }

    // 为嵌套子项目生成唯一 group，避免多个 "core"/"spring-boot" 模块
    // 共享 com.fluxion:core:x.x.x 坐标导致 Gradle 冲突合并。
    // 例如 :fluxion-adapter-http:core → group = com.fluxion.fluxion-adapter-http
    val pathSegments = path.split(":").filter { it.isNotEmpty() }
    group = if (pathSegments.size > 1) {
        "com.fluxion.${pathSegments.dropLast(1).joinToString(".")}"
    } else {
        "com.fluxion"
    }
    version = System.getenv("RELEASE_VERSION") ?: "1.0.0-SNAPSHOT"

    java {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    repositories {
        mavenCentral()
    }

    // 全局排除 spring-boot-starter-logging（默认日志实现 logback），改用 Log4j2
    configurations.all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }

    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            mavenBom("com.google.protobuf:protobuf-bom:$protobufBomVersion")
        }

        dependencies {
            // Kotlin
            dependency("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")
            dependency("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
            dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
            dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
            dependency("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion")
            dependency("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinCoroutinesVersion")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutinesVersion")

            // Guava
            dependency("com.google.guava:guava:$guavaVersion")
            // networknt json-schema-validator
            dependency("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
            // Apache Avro
            dependency("org.apache.avro:avro:$avroVersion")
            // JsonPath
            dependency("com.jayway.jsonpath:json-path:$jsonPathVersion")
            // Apache Commons JEXL3（ExpressionEvaluator，零 Spring 依赖）
            dependency("org.apache.commons:commons-jexl3:$jexl3Version")
            // commons-logging：JEXL3 的传递依赖，仅在非 Spring 测试运行时显式提供
            dependency("commons-logging:commons-logging:1.2")
            // OkHttp3
            dependency("com.squareup.okhttp3:okhttp:$okhttpVersion")
            // Groovy
            dependency("org.apache.groovy:groovy:$groovyVersion")
            // Redisson
            dependency("org.redisson:redisson:$redissonVersion")
            // Dubbo
            dependency("org.apache.dubbo:dubbo:$dubboVersion")
            dependency("org.apache.dubbo:dubbo-qos:$dubboVersion")
            // gRPC
            dependency("io.grpc:grpc-stub:$grpcVersion")
            dependency("io.grpc:grpc-protobuf:$grpcVersion")
            dependency("io.grpc:grpc-netty-shaded:$grpcVersion")
            dependency("io.grpc:grpc-services:$grpcVersion")
            dependency("net.devh:grpc-server-spring-boot-starter:$grpcSpringBootVersion")
            // Nacos Config SDK
            dependency("com.alibaba.nacos:nacos-client:$nacosClientVersion")
            // Apollo Client
            dependency("com.ctrip.framework.apollo:apollo-client:$apolloClientVersion")
            dependency("com.ctrip.framework.apollo:apollo-openapi:$apolloClientVersion")

            // JJWT
            dependency("io.jsonwebtoken:jjwt-api:$jjwtVersion")
            dependency("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
            dependency("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

            // JSR-305 / Java annotation API
            dependency("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
            dependency("com.google.code.findbugs:jsr305:$jsr305Version")
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // Kotlin 2.x 新 API：集中管理所有子模块的 Kotlin 编译选项，
    // 替代各子模块里废弃的 kotlinOptions { } 块
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-Xjvm-default=all"
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // ============ Spring Boot AOT 输出重定向 ============
    // processAot 是一个 JavaExec 任务，其工作目录默认为项目根目录。
    // AOT 处理器在工作目录下生成 bin/main/ 结构存放字节码和源码镜像，
    // 因此必须同时重定向 workingDir 和系统属性，将所有产物收敛到 build/aot/。
    tasks.matching { it.name == "processAot" }.configureEach {
        val aotDir = layout.buildDirectory.dir("aot")
        outputs.dir(aotDir)
        doFirst {
            val dir = aotDir.get().asFile
            dir.mkdirs()
            val exec = this@configureEach as? JavaExec ?: return@doFirst
            exec.workingDir(dir)
            exec.systemProperty("spring.aot.classes.directory", dir.resolve("classes").absolutePath)
            exec.systemProperty("spring.aot.sources.directory", dir.resolve("sources").absolutePath)
        }
    }

    // ============ Maven 发布配置 ============
    // 仅库模块发布 JAR；应用模块（fluxion-admin / fluxion-runtime / fluxion-test-webflux）
    // 及测试 fixtures 模块（fluxion-test）跳过发布
    val nonPublishableModules = setOf("fluxion-admin", "fluxion-runtime", "fluxion-test-webflux", "fluxion-test")
    if (name !in nonPublishableModules) {
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name.set(project.name)
                        description.set("Fluxion Workflow Platform - ${project.name}")
                        url.set("https://github.com/your-org/fluxion-platform")
                        licenses {
                            license {
                                name.set("Apache License 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }
                    }
                }
            }
            repositories {
                // GitHub Packages
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/your-org/fluxion-platform")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: ""
                        password = System.getenv("GITHUB_TOKEN") ?: ""
                    }
                }
                // 可选：私有 Nexus / Artifactory
                // maven {
                //     name = "Private"
                //     url = uri(System.getenv("MAVEN_REPO_URL") ?: "")
                //     credentials {
                //         username = System.getenv("MAVEN_REPO_USER") ?: ""
                //         password = System.getenv("MAVEN_REPO_PASSWORD") ?: ""
                //     }
                // }
            }
        }
    }
}
