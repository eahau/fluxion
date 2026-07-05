plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-adapter-spi"))

    // Dubbo 核心（零 Spring）
    implementation("org.apache.dubbo:dubbo") {
        // Dubbo 内部依赖 netty，但本模块代码 0 个 netty import，
        // 排除 netty-all（28 个子模块聚合 jar），大幅精简编译 classpath
        exclude(group = "io.netty", module = "netty-all")
    }

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-api")
}

