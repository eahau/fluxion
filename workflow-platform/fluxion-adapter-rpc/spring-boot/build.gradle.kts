plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-adapter-spi"))
    compileOnly(project(":fluxion-schema:core"))

    // RPC 实现（compileOnly：由最终应用按需选择 Dubbo / gRPC）
    compileOnly(project(":fluxion-adapter-rpc:dubbo"))
    compileOnly(project(":fluxion-adapter-rpc:grpc"))

    // Dubbo 编译依赖（compileOnly project 不传递 implementation，需显式声明）
    compileOnly("org.apache.dubbo:dubbo") {
        exclude(group = "io.netty", module = "netty-all")
    }

    // gRPC 注解 + 核心类型（排除传输层与自动装配，仅保留编译必需）
    compileOnly("net.devh:grpc-server-spring-boot-starter") {
        exclude(group = "io.grpc")
        exclude(group = "net.devh", module = "grpc-server-spring-boot-autoconfigure")
    }
    compileOnly("io.grpc:grpc-stub")
    compileOnly("io.grpc:grpc-protobuf")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-api")
}
