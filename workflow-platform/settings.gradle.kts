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
        maven("https://packages.confluent.io/maven/")
        maven("https://repo1.maven.org/maven2/")
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
    // External schema registry implementations
    "fluxion-schema:registry:confluent",
    "fluxion-schema:registry:apicurio",
    "fluxion-schema:registry:aws-glue",
    "fluxion-schema:registry:azure",

    // === fluxion-core group ===
    // Foundation module providing base types, node abstractions, workflow context,
    // utilities, and Spring Boot auto-configuration for core engine capabilities.
    "fluxion-core:spring-boot",

    // === fluxion-log ===
    // Unified Kotlin SLF4J lazy logging extension functions shared across all modules.
    "fluxion-log",

    // === fluxion-cache ===
    // Configurable cache abstraction layer built on Caffeine with configuration change listening.
    "fluxion-cache",
    "fluxion-cache:spring-boot",

    // === fluxion-function group ===
    // Workflow function abstraction layer with built-in function implementations,
    // external transport adapters (HTTP/gRPC/Dubbo), metadata registry, and Spring Boot
    // auto-configuration for runtime function discovery and invocation.
    "fluxion-function",
    "fluxion-function:builtin",
    
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

    // === fluxion-acl-spi ===
    // Access Control List SPI abstraction defining permission, role, and authentication
    // interfaces for pluggable security implementations in admin modules.
    "fluxion-acl-spi",

    // === fluxion-inbound group ===
    // Inbound protocol adapters for HTTP, RPC (Dubbo/gRPC), and MQ (Kafka) with
    // Spring Boot auto-configuration for workflow exposure as services.
    "fluxion-inbound:http:core",
    "fluxion-inbound:http:springmvc",
    "fluxion-inbound:http:springmvc:nacos",
    "fluxion-inbound:http:springmvc:apollo",
    "fluxion-inbound:http:springmvc:spring-boot",
    "fluxion-inbound:http:webflux",
    "fluxion-inbound:http:spring-boot",
    "fluxion-inbound:rpc:dubbo",
    "fluxion-inbound:rpc:grpc",
    "fluxion-inbound:rpc:spring-boot",
    "fluxion-inbound:mq:kafka",
    "fluxion-inbound:mq:spring-boot",
    "fluxion-inbound:spi",

    // === fluxion-outbound group ===
    // Outbound transport adapters for HTTP, RPC (Dubbo/gRPC), and MQ with
    // Spring Boot auto-configuration for workflow external function calls.
    "fluxion-outbound",
    "fluxion-outbound:http",
    "fluxion-outbound:http:spring-boot",
    "fluxion-outbound:dubbo",
    "fluxion-outbound:dubbo:spring-boot",
    "fluxion-outbound:grpc",
    "fluxion-outbound:grpc:spring-boot",
    "fluxion-outbound:mq",
    "fluxion-outbound:mq:spring-boot",

    // === fluxion-redis group ===
    // Redis capability domain with core command SPI, client adapter implementations
    // (Lettuce, Redisson, Spring Data Redis), and Spring Boot auto-configuration.
    "fluxion-redis:core",
    "fluxion-redis:spring-boot",
    "fluxion-redis:lettuce",
    "fluxion-redis:redisson",
    "fluxion-redis:spring-data",

    // === fluxion-script group ===
    // Embedded script execution engine supporting Groovy dynamic evaluation with
    // compilation caching and Spring Boot auto-configuration for script registry beans.
    "fluxion-script:core",
    "fluxion-script:spring-boot",

    // === fluxion-config group ===
    // Configuration center capability domain with core SPI, multiple backend implementations
    // (Apollo, Nacos, HTTP bootstrap), and Spring Boot auto-configuration.
    "fluxion-config:core",
    "fluxion-config:apollo",
    "fluxion-config:nacos",
    "fluxion-config:http",
    "fluxion-config:spring-boot",

    // === fluxion-registry group ===
    // Service registry capability domain with core SPI, Spring Cloud integration,
    // and Spring Boot auto-configuration for instance registration.
    "fluxion-registry:core",
    "fluxion-registry:spring-boot",

    // === fluxion-discovery group ===
    // Service discovery capability domain with core SPI, Spring Cloud integration,
    // and Spring Boot auto-configuration for instance discovery.
    "fluxion-discovery:core",
    "fluxion-discovery:spring-boot",

    // === fluxion-di group ==="
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
    "fluxion-test:webflux",

    // === fluxion-starters group ===
    // Convenience aggregator starters that bundle fluxion modules with their external
    // dependencies, enabling users to pick a single dependency for each capability.
    "fluxion-starters:dubbo-spring-boot-starter",
    "fluxion-starters:grpc-spring-boot-starter",
    "fluxion-starters:http-spring-boot-starter",
    "fluxion-starters:mq-spring-boot-starter",
    "fluxion-starters:nacos-spring-boot-starter",
    "fluxion-starters:apollo-spring-boot-starter",
    "fluxion-starters:consul-spring-boot-starter",
    "fluxion-starters:eureka-spring-boot-starter"
)
