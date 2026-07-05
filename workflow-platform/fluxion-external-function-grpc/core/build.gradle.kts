import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    id("com.google.protobuf")
}

dependencies {
    implementation(project(":fluxion-core"))

    // gRPC 核心（零 Spring）
    implementation("io.grpc:grpc-stub")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-netty-shaded")
    // gRPC 反射客户端协议
    implementation("io.grpc:grpc-services")

    implementation("com.google.protobuf:protobuf-java-util")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("javax.annotation:javax.annotation-api")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.3" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.64.0" }
    }
    generateProtoTasks {
        all().forEach { it.plugins { id("grpc") } }
    }
}

// 根脚本会禁用无 main Java 源文件的模块的 JavaCompile；本模块测试需要编译 protobuf 生成的 Java，故重新启用。
afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        enabled = true
    }
}
