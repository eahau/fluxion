// 全局插件仓库（关键：拉 Gradle src 源码走这里）
pluginManagement {
    repositories {
        // 优先复用本地Maven缓存 .m2/repository
        mavenLocal()
        // 国内镜像放最前面，优先下载
        maven("https://maven.aliyun.com/repository/gradle-plugin/")
        maven("https://maven.aliyun.com/repository/public/")
    }
}

// 依赖仓库统一国内源
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // 优先复用本地Maven缓存 .m2/repository
        mavenLocal()
        maven("https://maven.aliyun.com/repository/public/")
    }
}

rootProject.name = "fluxion-platform"

include(
    // workflow-schema 数据契约基座（JSON Schema / Protobuf / Avro）
    "fluxion-schema:core",
    "fluxion-schema:json",
    "fluxion-schema:protobuf",
    "fluxion-schema:protobuf:spring-boot",
    "fluxion-schema:avro",
    "fluxion-schema:avro:spring-boot",
    "fluxion-schema:spring-boot",
    "fluxion-core",
    "fluxion-core-spring-boot",
    // workflow-decorator-impl 子模块群
    "fluxion-decorator-impl:core",
    "fluxion-decorator-impl:spring-boot",
    "fluxion-adapter-spi",
    // workflow-acl-spi（ACL 能力域）
    "fluxion-acl-spi",
    // workflow-adapter-http 子模块群（HTTP 能力域）
    "fluxion-adapter-http:core",
    "fluxion-adapter-http:springmvc",
    "fluxion-adapter-http:springmvc:nacos",
    "fluxion-adapter-http:springmvc:apollo",
    "fluxion-adapter-http:springmvc:spring-boot",
    // workflow-adapter-http-webflux（WebFlux HTTP 适配器）
    "fluxion-adapter-http:webflux",
    // workflow-adapter-rpc 子模块群（RPC 能力域）
    "fluxion-adapter-rpc:dubbo",
    "fluxion-adapter-rpc:grpc",
    "fluxion-adapter-rpc:spring-boot",
    // workflow-adapter-mq 子模块群（MQ 能力域）
    "fluxion-adapter-mq:kafka",
    "fluxion-adapter-mq:spring-boot",
    // workflow-builtin-functions 子模块群（内置函数能力域）
    "fluxion-builtin-functions:core",
    "fluxion-builtin-functions:spring-boot",
    // workflow-redis 子模块群（嵌套在 workflow-redis 目录下）
    "fluxion-redis:core",
    "fluxion-redis:spring-boot",
    "fluxion-redis:lettuce",
    "fluxion-redis:redisson",
    "fluxion-redis:spring-data",
    // workflow-script-engine 子模块群（脚本引擎能力域）
    "fluxion-script-engine:core",
    "fluxion-script-engine:spring-boot",
    // workflow-external-function 子模块群（外部函数 outbound 能力域）
    "fluxion-external-function-dubbo:core",
    "fluxion-external-function-dubbo:spring-boot",
    "fluxion-external-function-grpc:core",
    "fluxion-external-function-grpc:spring-boot",
    "fluxion-external-function-http:core",
    "fluxion-external-function-http:spring-boot",
    // workflow-config 子模块群（配置中心能力域）
    "fluxion-config:core",
    "fluxion-config:apollo",
    "fluxion-config:nacos",
    "fluxion-config:http",
    "fluxion-config:spring-boot",
    // workflow-registry 子模块群（注册中心能力域）
    "fluxion-config:registry-http",
    // workflow-di 子模块群（依赖注入能力域）
    "fluxion-di:spring",
    // workflow-runtime 子模块群（运行面 / sidecar）
    "fluxion-runtime:core",
    "fluxion-runtime:spring-boot",
    "fluxion-runtime",
    "fluxion-admin",
    // 测试 fixtures 与内存实现共享模块
    "fluxion-test",
    "fluxion-test:webflux"
)