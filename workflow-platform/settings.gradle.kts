// Plugin repositories configuration - resolved in declaration order for plugin artifact downloads.
// Local Maven cache is checked first to reuse previously downloaded plugins, followed by
// Alibaba Cloud mirrors for faster downloads within mainland China networks.
pluginManagement {
    repositories {
        mavenLocal()
        maven("https://maven.aliyun.com/repository/gradle-plugin/")
        maven("https://maven.aliyun.com/repository/public/")
    }
}

// Dependency resolution management - repositories declared here are shared across ALL subprojects
// via RepositoriesMode.PREFER_SETTINGS (settings-level repos win over project-level repos).
// Local Maven cache followed by Alibaba Cloud public mirror for optimized dependency resolution.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
        maven("https://maven.aliyun.com/repository/public/")
    }
}

// Root project name for Gradle build identity and Maven artifact group resolution.
rootProject.name = "fluxion-platform"

include(
    // === fluxion-schema group ===
    // Data contract and schema validation abstraction layer supporting JSON Schema,
    // Protocol Buffers, and Apache Avro formats with Spring Boot auto-configuration.
    "fluxion-schema:core",
    "fluxion-schema:json",
    "fluxion-schema:protobuf",
    "fluxion-schema:protobuf:spring-boot",
    "fluxion-schema:avro",
    "fluxion-schema:avro:spring-boot",
    "fluxion-schema:spring-boot",

    // === fluxion-core group ===
    // Foundation module providing base types, node abstractions, workflow context,
    // utilities, and Spring Boot auto-configuration for core engine capabilities.
    "fluxion-core:spring-boot",

    // === fluxion-log ===
    // Unified Kotlin SLF4J lazy logging extension functions shared across all modules.
    "fluxion-log",

    // === fluxion-function group ===
    // Workflow function abstraction layer with built-in function implementations,
    // external transport adapters (HTTP/gRPC/Dubbo), metadata registry, and Spring Boot
    // auto-configuration for runtime function discovery and invocation.
    "fluxion-function",
    "fluxion-function:builtin",
    "fluxion-function:external",
    "fluxion-function:external:dubbo",
    "fluxion-function:external:dubbo:spring-boot",
    "fluxion-function:external:grpc",
    "fluxion-function:external:grpc:spring-boot",
    "fluxion-function:external:http",
    "fluxion-function:external:http:spring-boot",
    "fluxion-function:meta",
    "fluxion-function:spring-boot",

    // === fluxion-engine ===
    // DAG (Directed Acyclic Graph) workflow execution engine driving node scheduling,
    // dependency resolution, idempotency enforcement, and error handling.
    "fluxion-engine",

    // === fluxion-decorator group ===
    // Decorator SPI and cross-cutting concern implementations (metrics, tracing, caching,
    // async dispatch) with Spring Boot auto-configuration for decorator registration.
    "fluxion-decorator",
    "fluxion-decorator:spring-boot",

    // === fluxion-mock ===
    // Mock engine enabling workflow testing with simulated function responses using
    // AviatorScript expression evaluation without real external dependencies.
    "fluxion-mock",

    // === fluxion-debug ===
    // Debug and dependency injection support module providing FunctionInstanceProvider,
    // DependencyResolver, and DI abstractions for pluggable bean resolution strategies.
    "fluxion-debug",

    // === fluxion-adapter-spi ===
    // Adapter Service Provider Interface defining contracts for protocol adapters (HTTP/RPC/MQ),
    // configuration subscribers, schema change listeners, and capability domain SPIs.
    "fluxion-adapter-spi",

    // === fluxion-acl-spi ===
    // Access Control List SPI abstraction defining permission, role, and authentication
    // interfaces for pluggable security implementations in admin modules.
    "fluxion-acl-spi",

    // === fluxion-adapter-http group ===
    // HTTP protocol adapter suite supporting Spring MVC (Servlet stack) and WebFlux (Reactive stack)
    // with Nacos/Apollo-based dynamic route configuration storage and Spring Boot auto-configuration.
    "fluxion-adapter-http:core",
    "fluxion-adapter-http:springmvc",
    "fluxion-adapter-http:springmvc:nacos",
    "fluxion-adapter-http:springmvc:apollo",
    "fluxion-adapter-http:springmvc:spring-boot",

    // === fluxion-adapter-http:webflux ===
    // Reactive HTTP adapter implementation for Spring WebFlux (RouterFunction-based) supporting
    // non-blocking workflow invocation with Kotlin coroutines reactive bridge.
    "fluxion-adapter-http:webflux",

    // === fluxion-adapter-rpc group ===
    // RPC protocol adapter suite with Apache Dubbo and gRPC transport implementations,
    // including protobuf codegen tasks and Spring Boot auto-configuration for RPC exposure.
    "fluxion-adapter-rpc:dubbo",
    "fluxion-adapter-rpc:grpc",
    "fluxion-adapter-rpc:spring-boot",

    // === fluxion-adapter-mq group ===
    // Message Queue protocol adapter with Apache Kafka native client implementation
    // and Spring Boot auto-configuration for producer/consumer bean registration.
    "fluxion-adapter-mq:kafka",
    "fluxion-adapter-mq:spring-boot",

    // === fluxion-redis group ===
    // Redis capability domain with core command SPI, client adapter implementations
    // (Lettuce, Redisson, Spring Data Redis), and Spring Boot auto-configuration.
    "fluxion-redis:core",
    "fluxion-redis:spring-boot",
    "fluxion-redis:lettuce",
    "fluxion-redis:redisson",
    "fluxion-redis:spring-data",

    // === fluxion-script-engine group ===
    // Embedded script execution engine supporting Groovy dynamic evaluation with
    // compilation caching and Spring Boot auto-configuration for script registry beans.
    "fluxion-script-engine:core",
    "fluxion-script-engine:spring-boot",

    // === fluxion-config group ===
    // Configuration center capability domain with core SPI, multiple backend implementations
    // (Apollo, Nacos, HTTP bootstrap), Spring Boot auto-configuration, and HTTP service registry.
    "fluxion-config:core",
    "fluxion-config:apollo",
    "fluxion-config:nacos",
    "fluxion-config:http",
    "fluxion-config:spring-boot",

    // === fluxion-config:registry-http ===
    // HTTP-based service registry implementation for lightweight workflow service discovery
    // without heavyweight registry infrastructure dependencies.
    "fluxion-config:registry-http",

    // === fluxion-di group ===
    // Dependency injection capability domain providing Spring Framework-based integration
    // that bridges fluxion-debug DI abstractions to Spring ApplicationContext bean resolution.
    "fluxion-di:spring",

    // === fluxion-runtime group ===
    // Runtime execution plane (sidecar / application launcher) with framework-agnostic core,
    // Spring Boot auto-configuration assembly, and deployable Spring Boot application entry point.
    "fluxion-runtime:core",
    "fluxion-runtime:spring-boot",
    "fluxion-runtime",

    // === fluxion-admin ===
    // Admin management console application - Spring Boot executable with JPA persistence,
    // OpenAPI-generated REST API, Spring Security authentication, and UI serving capabilities.
    "fluxion-admin",

    // === fluxion-test group ===
    // Shared test fixtures and in-memory SPI implementations providing base test classes,
    // JUnit 5 extensions, and helper utilities consumed as testImplementation dependencies.
    "fluxion-test",
    "fluxion-test:webflux"
)
