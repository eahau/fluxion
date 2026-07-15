import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

// Root project plugin declarations. All plugins use `apply false` so versions are centrally managed
// here while actual plugin application happens per-subproject (or below in subprojects {} block).
// Plugin version literals must stay inline (plugins block does not support referencing external vals).
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

// ===== Centralized Version Catalog =====
// Shared version variables referenced by subprojects and dependency management BOM imports.
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
val springCloudVersion           = "2023.0.3"
val springCloudAlibabaVersion    = "2023.0.3.3"
val jjwtVersion                  = "0.12.5"
val javaxAnnotationApiVersion    = "1.3.2"
val jsr305Version                = "3.0.2"
val jetbrainsAnnotationsVersion  = "23.0.0"
val protobufBomVersion           = "3.25.3"
val avroVersion                  = "1.11.3"
val graalVmBuildToolsVersion     = "0.10.4"

// ===== Subproject Conventions =====
// Global plugin application and build configuration applied to EVERY included module.
subprojects {
    // Core JVM plugins: Kotlin + Java + Java-Library (for library modules; admin app excluded below).
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")
    if (name != "fluxion-admin") {
        apply(plugin = "java-library")
    }
    // Spring Dependency Management plugin enables Maven BOM imports and explicit version pinning.
    apply(plugin = "io.spring.dependency-management")
    // Maven Publishing plugin - publication is skipped for non-library modules later in this block.
    apply(plugin = "maven-publish")

    // Optimization: skip JavaCompile tasks for pure-Kotlin modules that contain no .java source files.
    // Detected per-build via afterEvaluate to allow generated sources (protobuf/openapi) to be evaluated.
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

    // Unique Maven group computation for nested subprojects to prevent GAV coordinate collisions.
    // Example: :fluxion-adapter-http:core  -> group = com.fluxion.fluxion-adapter-http
    //          :fluxion-log               -> group = com.fluxion
    val pathSegments = path.split(":").filter { it.isNotEmpty() }
    group = if (pathSegments.size > 1) {
        "com.fluxion.${pathSegments.dropLast(1).joinToString(".")}"
    } else {
        "com.fluxion"
    }
    // Version is overridable via RELEASE_VERSION env var (CI/CD releases); defaults to snapshot.
    version = System.getenv("RELEASE_VERSION") ?: "1.0.0-SNAPSHOT"

    // JVM source/target level - locked to JDK 21 for all modules (Spring Boot 3.x minimum requirement).
    java {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    // Fallback project-level repositories (settings-level PREFER_SETTINGS mirrors take priority).
    repositories {
        mavenCentral()
    }

    // Global dependency exclusion: replace Spring Boot default Logback logging with Log4j2.
    configurations.all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }

    // ===== Dependency Management (Spring BOM + explicit versions) =====
    configure<DependencyManagementExtension> {
        imports {
            // Spring Boot BOM manages Spring Framework, Jackson, Log4j2, Micrometer, etc.
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            // Spring Cloud BOM manages ServiceRegistry, DiscoveryClient, LoadBalancer, etc.
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
            // Protobuf BOM ensures consistent protobuf-java, protobuf-java-util versions.
            mavenBom("com.google.protobuf:protobuf-bom:$protobufBomVersion")
        }

        dependencies {
            // Spring Cloud Alibaba Nacos Discovery - explicitly pinned to avoid BOM conflicts
            dependency("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery:$springCloudAlibabaVersion")

            // Kotlin standard library, reflection, and coroutines with centrally pinned versions.
            dependency("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")
            dependency("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
            dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
            dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
            dependency("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion")
            dependency("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinCoroutinesVersion")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutinesVersion")

            // Google Guava - collection utilities, caching primitives, and immutable types.
            dependency("com.google.guava:guava:$guavaVersion")
            // networknt JSON Schema Validator - JSON Schema draft-07/draft-2019-09 validation engine.
            dependency("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
            // Apache Avro - binary serialization schema framework for Avro format support.
            dependency("org.apache.avro:avro:$avroVersion")
            // Jayway JsonPath - JSON document query via path expressions (read-only navigation).
            dependency("com.jayway.jsonpath:json-path:$jsonPathVersion")
            // Apache Commons JEXL3 - embedded expression evaluator (zero Spring transitive deps).
            dependency("org.apache.commons:commons-jexl3:$jexl3Version")
            // Commons Logging bridge - JEXL3 transitive dep; explicit for non-Spring test classpath.
            dependency("commons-logging:commons-logging:1.2")
            // OkHttp3 - HTTP client used by external HTTP function transport adapter.
            dependency("com.squareup.okhttp3:okhttp:$okhttpVersion")
            // Apache Groovy - dynamic scripting engine for fluxion-script-engine module.
            dependency("org.apache.groovy:groovy:$groovyVersion")
            // Redisson - advanced Redis client (distributed locks, collections, reactive API).
            dependency("org.redisson:redisson:$redissonVersion")
            // Apache Dubbo - RPC framework for external Dubbo function transport adapter.
            dependency("org.apache.dubbo:dubbo:$dubboVersion")
            dependency("org.apache.dubbo:dubbo-qos:$dubboVersion")
            // gRPC Java - high-performance RPC framework (stub, protobuf, netty transport, services).
            dependency("io.grpc:grpc-stub:$grpcVersion")
            dependency("io.grpc:grpc-protobuf:$grpcVersion")
            dependency("io.grpc:grpc-netty-shaded:$grpcVersion")
            dependency("io.grpc:grpc-services:$grpcVersion")
            // net.devh gRPC Spring Boot Starter - gRPC server auto-configuration and annotation support.
            dependency("net.devh:grpc-server-spring-boot-starter:$grpcSpringBootVersion")
            // Alibaba Nacos Config Client - config center + service discovery SDK (non-Spring Cloud).
            dependency("com.alibaba.nacos:nacos-client:$nacosClientVersion")
            // Ctrip Apollo Client - configuration center SDK and OpenAPI admin client.
            dependency("com.ctrip.framework.apollo:apollo-client:$apolloClientVersion")
            dependency("com.ctrip.framework.apollo:apollo-openapi:$apolloClientVersion")

            // JJWT (JSON Web Token) - JWT creation and validation (API + impl + Jackson serialization).
            dependency("io.jsonwebtoken:jjwt-api:$jjwtVersion")
            dependency("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
            dependency("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

            // JSR-250 / JSR-305 annotation APIs - javax.annotation and nullability/strictness markers.
            dependency("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
            dependency("com.google.code.findbugs:jsr305:$jsr305Version")
        }
    }

    // Force UTF-8 encoding for Java compilation across all platforms.
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // Kotlin JVM compilation conventions - Kotlin 2.x compilerOptions DSL (replaces deprecated kotlinOptions).
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            // Target bytecode level aligned with JDK 21 source compatibility.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                // Strict JSR-305 nullability interpretation (treats @Nullable/@NonNull as hard types).
                "-Xjsr305=strict",
                // Enable default methods in Kotlin interfaces (JVM 8+ DefaultMethods attribute).
                "-Xjvm-default=all"
            )
        }
    }

    // All tests use JUnit Platform (JUnit 5 Jupiter) engine exclusively.
    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Spring Boot bootJar duplicate handling - resolve conflicts from nested spring-boot modules.
    tasks.matching { it.name == "bootJar" }.configureEach {
        val jarTask = this as? org.springframework.boot.gradle.tasks.bundling.BootJar ?: return@configureEach
        jarTask.duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    }

    // ===== Spring Boot AOT Output Redirection =====
    // The processAot JavaExec task defaults its working directory to the project root, causing it to
    // generate bin/main/ bytecode/source mirrors at the project root level. Redirects both workingDir
    // and Spring AOT system properties so all AOT artifacts are consolidated under build/aot/.
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

    // ===== Maven Publishing Configuration =====
    // Library modules publish to GitHub Packages (and optionally a private Nexus/Artifactory).
    // Executable applications (fluxion-admin / fluxion-runtime) and test fixture modules skip publishing.
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
                // GitHub Packages publication target (credentials from GitHub Actions env vars).
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/your-org/fluxion-platform")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: ""
                        password = System.getenv("GITHUB_TOKEN") ?: ""
                    }
                }
                // Optional: private on-prem Nexus / Artifactory publication (credentials via env vars).
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
