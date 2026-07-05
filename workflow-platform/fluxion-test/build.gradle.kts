plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // 依赖被测试的 SPI 模块
    api(project(":fluxion-core"))
    api(project(":fluxion-adapter-spi"))

    // 测试工具
    api("org.junit.jupiter:junit-jupiter:5.10.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

// fluxion-test 是测试 fixtures 聚合模块，本地打包供其他模块 testImplementation 使用，但不发布到 Maven。
// 注意：不能禁用 jar 任务，否则依赖本模块的项目在 clean 构建后测试运行时找不到 fixtures 类。
