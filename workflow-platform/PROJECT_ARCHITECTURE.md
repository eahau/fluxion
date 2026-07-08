# Fluxion Platform Architecture Overview

## Project Identity

- **Root project**: `fluxion-platform` (artifact group resolved from `settings.gradle.kts`)
- **Source language**: Kotlin 2.x (JVM target) with Kotlin DSL Gradle build scripts
- **Runtime framework**: Spring Boot 3.x (auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`)
- **Build tool**: Gradle 8.x with `settings.gradle.kts` dependency resolution management (`RepositoriesMode.PREFER_SETTINGS`)
- **Repository mirrors**: Aliyun Cloud public Maven mirrors for plugin + dependency resolution

## Overview

Fluxion is a workflow engine platform split into a **control plane** (Admin) and a **data plane** (Runtime / sidecar). Workflows are authored as DAGs (Directed Acyclic Graphs) of functions, validated by schema contracts, and exposed over multiple protocols (HTTP / RPC / MQ). Runtime components self-register with the Admin server over HTTP, enabling targeted config push, health tracking, and function deployment.

### Layering (bottom → top)

| Layer                 | Module(s)                                      | Responsibility                                                      |
|-----------------------|------------------------------------------------|---------------------------------------------------------------------|
| **SPI / Contract**    | `fluxion-adapter-spi`, `fluxion-acl-spi`       | Protocol adapter, config subscriber, registry, auth SPIs            |
| **Core / Engine**     | `fluxion-core`, `fluxion-engine`, `fluxion-log`| Base types, node model, DAG execution engine, lazy logging utils    |
| **Cross-cutting**     | `fluxion-decorator`, `fluxion-di`, `fluxion-debug`, `fluxion-script-engine` | Decorators, DI bridge, script eval, debug tooling               |
| **Capability Domains**| `fluxion-schema:*`, `fluxion-function:*`, `fluxion-config:*`, `fluxion-redis:*` | Schema/function/config/redis abstractions + backend plug-ins    |
| **Protocol Adapters** | `fluxion-adapter-http:*`, `fluxion-adapter-rpc:*`, `fluxion-adapter-mq:*` | HTTP (MVC/WebFlux), RPC (Dubbo/gRPC), MQ (Kafka)                |
| **Execution Plane**   | `fluxion-runtime:*`, `fluxion-runtime`         | Workflow runtime sidecar / launcher + Spring Boot entrypoint       |
| **Control Plane**     | `fluxion-admin`                                | Spring Boot Admin console: JPA store, OpenAPI REST, UI, registry    |
| **Test fixtures**     | `fluxion-test`, `fluxion-test:webflux`         | Shared in-memory SPI implementations, JUnit 5 extensions            |

---

## Module-by-Module Reference

### 1. SPI Contracts (pure interfaces, no runtime deps)

#### fluxion-adapter-spi
Defines the contracts every adapter / capability domain must implement.

| Interface/Type                      | Purpose                                                      |
|-------------------------------------|--------------------------------------------------------------|
| `WorkflowRouter`                    | Top-level entry point: route `UnifiedRequest` → DAG execute  |
| `UnifiedRequest` / `UnifiedResponse`| Protocol-agnostic request/response envelope (params, headers, rawBody, executionId) |
| `ProtocolAdapter`                   | Per-protocol adapter lifecycle                              |
| `KeyedConfigChangeListener`         | Callback when a keyed configuration entry changes            |
| `SchemaConfigSnapshot`              | Immutable snapshot of a JSON-Schema / Avro / Proto schema    |
| `FunctionConfigSnapshot`            | Immutable snapshot of a deployed function definition        |
| `InstanceRegistry` / `InstanceDiscovery` | Worker self-registration + Admin-side discovery        |
| `PublishTarget` / `TargetType`      | Targeting for config push (ALL, APP_GROUP, INSTANCES)        |
| Acl SPI sub-types                   | Permission, role, authentication hooks (moved to its own jar)|

#### fluxion-acl-spi
Separate SPI jar so Admin modules (only) can plug in custom authentication / authorization without the runtime data plane pulling in Spring Security.

---

### 2. Core & Engine

#### fluxion-log (foundation)
A single `LogExtensions.kt` file in package `org.slf4j` providing inline lazy lambda extensions:
```
log.debug { "..." }   log.debug(throwable) { "..." }
log.info  { "..." }   log.warn / log.error / log.trace similarly
```
Every `.kt` file in the project MUST:
1. Declare `private val log = LoggerFactory.getLogger(javaClass)` (or `::class.java` in companion)
2. Call ONLY the lazy `{ ... }` variants — never pass a plain `String` directly
3. Never use Lombok `@Slf4j` — it produces a Java static logger that doesn't interact with Kotlin inline lambdas.

#### fluxion-core:spring-boot
- Base `Node` / `Edge` / `WorkflowDef` / `WorkflowContext` abstractions
- `WorkflowId`, `ExecutionId`, `NodeId` value types with validation
- `JsonUtil` (Jackson `ObjectMapper` singleton for modules that don't depend on Spring Boot auto-configured ObjectMapper)
- `WorkflowExecutionResult`, `NodeExecutionResult`, error-code enums
- Spring Boot auto-configuration: registers ObjectMapper customizers + context scope beans

#### fluxion-engine
The DAG execution kernel. Execution model:

1. `WorkflowEngine.execute(workflowId, params)` entry point
2. Builds the topological order from `WorkflowDef.edges` using Kahn's algorithm with stable tie-breaking
3. Per-node dispatch: resolves the `FunctionInstance` via `FunctionRegistry` → invokes → records output
4. Dependency wait: `async { node }` jobs; a node waits until all upstream inputs are materialized in `WorkflowContext`
5. Error propagation: failure mode configurable per edge (fail-fast / continue / default value)
6. Idempotency: per-node hash of (workflowId, executionId, nodeId, inputDigest) → checked against a pluggable `IdempotencyStore` (defaults to in-memory; `fluxion-redis` provides the production-grade impl)
7. Decorator chain: every invocation passes through `NodeDecorator` SPI (see `fluxion-decorator`)

Key classes:
- `WorkflowEngine` — public facade
- `DagScheduler` — topo order + ready-set evolution as nodes complete
- `NodeExecutor` — function invocation + context write
- `ExecutionIdGenerator` — default Snowflake-ish epoch-counter generator; overridable via bean

---

### 3. Cross-Cutting Modules

#### fluxion-decorator + fluxion-decorator:spring-boot
`NodeDecorator` SPI: every node execution passes through a chain of decorators. Ship-with implementations:

| Decorator                  | Purpose                                                         |
|----------------------------|-----------------------------------------------------------------|
| `MetricsNodeDecorator`     | Micrometer `timer` / `counter` per node (function class tag)    |
| `TracingNodeDecorator`     | OpenTelemetry `Tracer` span `workflow.node` with attributes     |
| `CachingNodeDecorator`     | Plug into `fluxion-redis` or `javax.cache` via keyed cache SPI  |
| `RetryNodeDecorator`       | Retry on specific exceptions (simple backoff policy config)     |
| `AsyncDispatchDecorator`   | Optional: off-load heavy nodes to a dedicated executor thread pool to avoid saturating the coroutine scheduler |

Spring Boot auto-configuration applies all `NodeDecorator` beans in `@Order` via a single composite decorator.

#### fluxion-debug
- `FunctionInstanceProvider`: how the engine looks up the callable for a `FunctionRef` (name → class → instance)
- `DependencyResolver`: generic interface that the provider delegates to for constructor injection
- Debug hooks: `ExecutionTracer`, `BreakpointHandler`, script-defined step-through controls
- NOTE: despite the name, this is the DI-entry module; `fluxion-di:spring` is the concrete Spring bridge.

#### fluxion-di:spring
Implements `DependencyResolver` by delegating to Spring's `ApplicationContext.getBeanProvider` plus optional `AutowireCapableBeanFactory.createBean` for dynamically-constructed function classes.

#### fluxion-script-engine:core + spring-boot
Embedded Groovy evaluation with compilation caching (`ConcurrentHashMap<ScriptKey, Class<Script>>`). Used for:
- Dynamic expression evaluation in conditions (`condition.groovy` inside `WorkflowDef`)
- Ad-hoc admin-defined transforms (see `ScriptedFunctionProvider`)
- Mock expression evaluator (used together with `fluxion-mock`)

#### fluxion-mock
Mock engine enabling per-test or per-admin-rule function stubbing without a real upstream. Uses `AviatorEvaluator` for small one-liners like `"user.id" -> 1234` or Groovy for complex returns. Rule matching priority: exact workflowId/nodeId > workflowId glob > function-type glob.

---

### 4. Capability Domains

Each domain follows a consistent 3-layer sub-module pattern:
```
fluxion-<domain>:core           → SPI + in-memory default impl
fluxion-<domain>:<backend>      → concrete backend (Apollo / Nacos / Lettuce / JSON / etc.)
fluxion-<domain>:spring-boot    → auto-configuration that wires backend + core into beans
```

#### fluxion-schema:*
Schema validation abstraction over three formats:

| Sub-module             | Format         | Underlying library                           |
|------------------------|----------------|----------------------------------------------|
| `fluxion-schema:json`  | JSON Schema 2020-12 | `com.github.erosb:everit-json-schema` (or NetworkNT; check `build.gradle.kts`) |
| `fluxion-schema:protobuf` | Protocol Buffers descriptor | `protobuf-java` dynamic message + descriptor parser |
| `fluxion-schema:avro`  | Apache Avro    | `avro` generic record validation             |

**Conventions**:
- All validators expose a single `SchemaValidator.validate(payload): ValidationResult`
- Validation errors carry a uniform `(jsonPointer, code, message)` tuple regardless of format so Admin UI can render them consistently
- `spring-boot` sub-module registers a `SchemaValidatorFactory` bean that routes by schema media-type

#### fluxion-function:*
Function abstraction — "functions" are the leaf nodes the DAG scheduler actually calls.

| Sub-module                    | Purpose                                              |
|-------------------------------|------------------------------------------------------|
| `fluxion-function`            | Core SPI: `FunctionRef`, `FunctionInstance`, `FunctionRegistry`, argument mapper, return-type coercion |
| `fluxion-function:builtin`    | In-box functions: `echo`, `wait`, `httpCall` (via core HTTP client), `jsonPath`, `transform`, `choice`, `parallelForEach` |
| `fluxion-function:meta`       | Function metadata store — stores doc, parameter schema, tags for display in Admin |
| `fluxion-function:external:http` | Calls remote HTTP function definitions; uses Jackson + configurable connect/read timeouts |
| `fluxion-function:external:dubbo` | Apache Dubbo generic invocation; transport via `dubbo-spring-boot-starter` |
| `fluxion-function:external:grpc` | gRPC stub invocation; protobuf bindings generated in `:grpc` via protobuf-gradle-plugin |
| `fluxion-function:spring-boot`| Auto-configuration: scans for `@FluxionFunction` annotations, populates registry, plugs in external transport beans |

Annotation for discovery:
```kotlin
@FluxionFunction(name = "com.example.SendEmail", timeoutMs = 5_000)
class SendEmailFunction(...) : FunctionInstance { ... }
```

#### fluxion-config:*
Pluggable configuration center client.

| Sub-module               | Backend              | Key class                          | Pattern                    |
|--------------------------|----------------------|------------------------------------|----------------------------|
| `fluxion-config:core`    | In-memory (default)  | `AbstractKeyedConfigSubscriber`    | Poll- or push-based; uniform listener contract (`KeyedConfigChangeListener`) |
| `fluxion-config:apollo`  | Apollo namespace     | `ApolloKeyedConfigSubscriber`      | `Config.addChangeListener` push model |
| `fluxion-config:nacos`   | Nacos config server  | `NacosFunctionConfigSubscriber` / `NacosSchemaConfigSubscriber` | `ConfigService.addListener` push model, with `__index__` aggregate key for cheap bulk discovery |
| `fluxion-config:http`    | Admin HTTP bootstrap | `HttpSchemaConfigSubscriber`       | Periodic `GET /internal/workflow/schemas/list` polling fallback — ideal for bootstrapping before a config-center client is up |
| `fluxion-config:registry-http` | HTTP service register | `HttpInstanceRegistry`         | Worker self-register + 10s heartbeat daemon thread; DELETE on shutdown (best-effort) |
| `fluxion-config:spring-boot` | —              | `ConfigSubscribersAutoConfiguration` | ConditionalOnClass wiring: picks Apollo if `apollo-client` present, else Nacos, else HTTP |

**Push sequence (for Nacos/Apollo)**:
1. Bean start → subscribe to `workflow.schema.{namespace}` and `workflow.function.{namespace}` prefixes
2. Admin publishes → change event arrives → parse snapshot → diff vs. local
3. Fire `KeyedConfigChangeListener.changed(added, modified, removed)` → engine invalidates in-memory caches

#### fluxion-redis:*
Domain for: idempotency store, rate limiter backend, decorator caching.

| Sub-module               | Role                                     |
|--------------------------|------------------------------------------|
| `fluxion-redis:core`     | SPI: `RedisStringCommands`, `RedisHashCommands`, `RedisSetCommands`, `DistributedLock` |
| `fluxion-redis:lettuce`  | Lettuce-based commands impl (async preferred) |
| `fluxion-redis:redisson` | Redisson-based `RLock` → `DistributedLock` impl, plus commands via RBatch for pipelines |
| `fluxion-redis:spring-data` | Spring Data Redis (`StringRedisTemplate`) impl — most common default in Spring Boot apps |
| `fluxion-redis:spring-boot` | Auto-config: creates bean per SPI, prefers Spring Data if `spring-boot-starter-data-redis` is on classpath, else Redisson if present, else Lettuce |

---

### 5. Protocol Adapters

Responsibility: translate a protocol-native request into a `UnifiedRequest`, dispatch to `WorkflowRouter`, and translate back.

#### fluxion-adapter-http:*

| Sub-module                          | Stack                      | Key class                              |
|-------------------------------------|----------------------------|----------------------------------------|
| `fluxion-adapter-http:core`         | Shared types               | `HttpRequestProcessor`, `RouteMatch`, `ATTR_ROUTE_MATCH` / `HEADER_EXECUTION_ID` constants, param-merging utility (`mergeParams`) |
| `fluxion-adapter-http:springmvc`    | Servlet (Tomcat / Jetty)   | `WorkflowHandlerMapping` (AbstractHandlerMethodMapping sub-class; maps HTTP path templates → resolved workflows) + `WorkflowHandlerAdapter` |
| `fluxion-adapter-http:springmvc:nacos`  | Route config source    | Pulls route → workflow binding from Nacos; updates mapping dynamically (no app restart) |
| `fluxion-adapter-http:springmvc:apollo` | Route config source   | Same pattern, Apollo-based                                             |
| `fluxion-adapter-http:webflux`      | Reactive (RouterFunctions)| `WorkflowHandlerMapping` for WebFlux → puts `RouteMatch` into `ServerRequest.attributes[ATTR_ROUTE_MATCH]`, then `WebFluxWorkflowHandler.handle(ServerRequest): Mono<ServerResponse>` picks it up. Uses `kotlinx-coroutines-reactor.awaitSingle/awaitSingleOrNull` to bridge Reactor ↔ suspend. |

**Route binding semantics**: each config entry = `(method, pathPattern, workflowId, inputSchemaId)`.
`WorkflowRouter.executeSuspend` does the schema validation before DAG execution, so adapters are schema-unaware and share the validation pipeline.

#### fluxion-adapter-rpc:*

| Sub-module                   | Transport   | Details                                                     |
|------------------------------|-------------|-------------------------------------------------------------|
| `fluxion-adapter-rpc:dubbo`  | Apache Dubbo| Exposes generic service `GenericWorkflowService.invoke(serviceName, methodName, params)` → routes via `WorkflowRouter` |
| `fluxion-adapter-rpc:grpc`   | gRPC        | Protobuf service + generated stubs; one `WorkflowService/Execute` RPC. Codegen via `protobuf-gradle-plugin` protoc `grpc` plugin. |
| `fluxion-adapter-rpc:spring-boot` | —     | Auto-config: registers `@DubboService` bean or gRPC server bean based on classpath; in non-Spring deployments users wire manually. |

#### fluxion-adapter-mq:*

| Sub-module                   | Transport | Details                                                     |
|------------------------------|-----------|-------------------------------------------------------------|
| `fluxion-adapter-mq:kafka`   | Kafka     | Polls topic(s) `workflow.request.<group>` — each record has headers `x-workflow-id`, `x-execution-id` optional; deserializes via `MessageConverter` → dispatches → result written to reply topic configured per-message `x-reply-to` header |
| `fluxion-adapter-mq:spring-boot` | —    | Auto-config: creates `ConcurrentKafkaListenerContainerFactory` or reactor `KafkaReceiver` if on reactor classpath |

---

### 6. Runtime (Data Plane)

Three-layer module following the same `core → spring-boot → executable` convention:

| Sub-module              | Role                                                    |
|-------------------------|---------------------------------------------------------|
| `fluxion-runtime:core`  | Framework-agnostic `RuntimeLauncher` that: loads config subscriber → self-registers in `InstanceRegistry` → initializes all `ProtocolAdapter`s → serves until SIGTERM (keeps alive via virtual thread park or non-daemon scheduler) |
| `fluxion-runtime:spring-boot` | Auto-configuration `RuntimeAutoConfiguration` — imports all adapter auto-configs, picks the correct config backend bean, exposes `RuntimeLifecycle` as SmartLifecycle (auto start on ContextRefreshed) |
| `fluxion-runtime`       | **Deployable Spring Boot application** — `@SpringBootApplication public class RuntimeApplication` with a `runtime.sh` / Gradle `bootJar` artifact. Users are expected to compose their own `runtime` module if they need extra dependencies; this jar ships with the default combination. |

Typical Runtime startup sequence:
1. SpringApplication.run → bean phase → `RuntimeAutoConfiguration` triggers
2. Choose config backend (Apollo → Nacos → HTTP fallback)
3. `SchemaConfigSubscriber` and `FunctionConfigSubscriber` both `start()` → fetch initial snapshots + register push listeners
4. `InstanceRegistry.register(InstanceInfo)` → sends register POST + starts heartbeat
5. Every adapter `ProtocolAdapter.start()` — HTTP server binds port(s), Dubbo services export, gRPC server starts, Kafka consumer group joins.
6. Runtime ready. Admin UI shows the instance as "online" via `InstanceDiscovery.getAllInstances()`.

---

### 7. Admin (Control Plane)

`fluxion-admin` — single executable Spring Boot application.

Stack:
- Persistence: Spring Data JPA + Hibernate (`spring-boot-starter-data-jpa`); default H2, intended to swap to PostgreSQL / MySQL in production via `spring.datasource.*`
- OpenAPI: `springdoc-openapi-starter-webmvc-ui` + `openapi-generator` (Gradle task generates DTOs under `build/generated/openapi/src/main/kotlin/`)
- Security: Spring Security with form login + JWT token filter; pluggable via `fluxion-acl-spi` so enterprise customers can drop in a SSO implementation
- JPA entities (approximate): `WorkflowEntity`, `SchemaEntity`, `FunctionEntity`, `RouteBindingEntity`, `ExecutionRecordEntity`, `InstanceHeartbeatEntity`
- Internal HTTP APIs (used by Runtime workers):
  - `POST /internal/workflow/registry/register` + heartbeat + DELETE (service discovery)
  - `GET  /internal/workflow/schemas/list?modifiedAfter=…` (polling fallback for config backend)
  - `POST /internal/workflow/config/push` (targeted push → workers resolve to `PublishTarget`)
- Public REST APIs (used by Admin UI or external tools):
  - CRUD on workflows (with draft / published state & versioning)
  - Schema management + compatibility validator (`SchemaCompatibilityValidator` — see file at `fluxion-admin/src/main/kotlin/com/fluxion/admin/validator/SchemaCompatibilityValidator.kt`; it checks JSON Schema drafts for backward-compatible field additions using flags `allowAdditionalProperties`, type widening list, etc.)
  - Function registry + metadata
  - Execution query + cancel
  - App-group / role / user management
- UI: static resources under `src/main/resources/static/` (usually a React SPA built by an NPM build before `processResources`)

---

### 8. Test Fixtures

#### fluxion-test + fluxion-test:webflux
`testImplementation` dependency only. Provides:
- `InMemoryConfigSubscriber` — fires change events in a test's `beforeEach` without a real Apollo/Nacos
- `InMemoryInstanceRegistry` + `InMemoryInstanceDiscovery` — backed by ConcurrentHashMap; useful for multi-instance unit tests
- `InMemoryRedisCommands` — same map-backed approach for idempotency/cache tests
- `AbstractWorkflowIntegrationTest` base class that spins up a minimal Spring context with all in-memory beans
- `WebFluxTestSupport` — factory for mock `ServerRequest`s using `MockServerRequest` (avoids needing a full `WebTestClient` in unit tests)

---

## Gradle Build Conventions

- **Kotlin DSL only**: All build scripts are `.gradle.kts`. Comments across all scripts were rewritten to English as part of this pass (over 60 scripts).
- **Repositories**: `pluginManagement` + `dependencyResolutionManagement` in `settings.gradle.kts` use `PREFER_SETTINGS`, so subprojects must NOT declare their own repositories.
- **Spring Boot dependency management**: root or parent `build.gradle.kts` applies `io.spring.dependency-management` plugin so subprojects can omit versions for `spring-boot-starter-*` and managed artifacts.
- **Sub-module convention**: `<domain>:<backend>:spring-boot` always depends on `<domain>:<backend>` and `<domain>:core` transitively. Applications (`fluxion-admin`, `fluxion-runtime`) depend on `:spring-boot` stubs.
- **OpenAPI generation**: `fluxion-admin` has an `openApiGenerate` Gradle task (from `org.openapi.generator` plugin) producing generated Kotlin data classes into `build/generated/openapi/…` — those are on the main source set.
- **Protobuf generation**: `fluxion-adapter-rpc:grpc` + `fluxion-function:external:grpc` + `fluxion-schema:protobuf` each apply `com.google.protobuf` plugin with the protoc grpc-kotlin plugin. Output lives in `build/generated/source/proto/`.

---

## End-to-End Request Flow (Call Graph)

Incoming HTTP (WebFlux stack, for concreteness):

```
Client → Spring WebFlux DispatcherHandler
        → WorkflowHandlerMapping (subclass)
            → resolves path+method to RouteMatch
            → puts RouteMatch into ServerRequest.attributes[ATTR_ROUTE_MATCH]
            → returns handler: WebFluxWorkflowHandler::handle
        → WebFluxWorkflowHandler.handle(request): Mono<ServerResponse>
            → mono {
                 UnifiedRequest = ServerRequest.toUnifiedRequest(workflowId, pathVars)
                 WorkflowRouter.executeSuspend(unifiedRequest)   ← main engine entry
                    → SchemaValidator.validate(unifiedRequest.params, schema)
                    → WorkflowEngine.execute(workflowId, params, executionId)
                         → DagScheduler.topoOrder
                         → NodeExecutor per node (decorator chain wrapping FunctionInstance)
                         → collect outputs → WorkflowExecutionResult
                 ServerResponse.ok().header(X-Execution-Id, …).bodyValue(data).awaitSingle()
              }
```

Response headers:
- `X-Fluxion-Execution-Id` (`HttpRequestProcessor.HEADER_EXECUTION_ID`) — always returned; used for tracing and for later replay / cancellation requests.

---

## Extensibility Points (How to Add New Stuff)

| Want to add …           | Implement in module…                                    | Where registered                                  |
|-------------------------|---------------------------------------------------------|---------------------------------------------------|
| New schema format       | new `fluxion-schema:<fmt>`                              | `SchemaValidatorFactory` (Spring Boot META-INF)   |
| New function transport  | new `fluxion-function:external:<transport>`             | `FunctionRegistry.registerProvider(provider)`     |
| New config backend      | new `fluxion-config:<backend>` extends `AbstractKeyedConfigSubscriber` | ConfigSubscribersAutoConfiguration           |
| New protocol adapter    | new `fluxion-adapter-*:core` + `:spring-boot`           | `ProtocolAdapter.start()` / RuntimeLifecycle      |
| New cross-cutting concern around nodes | class `FooNodeDecorator : NodeDecorator` in its own jar | Any `@Bean FooNodeDecorator` with `@Order`    |
| Custom auth/ACL in Admin| impl `fluxion-acl-spi` interfaces                        | `@Configuration` that exposes `AclPermissionEvaluator` |

---

## Common Pitfalls (Lessons Learned from the Refactor)

1. **Logger import trap for lazy extensions**: Every `.kt` file that writes `log.info { "..." }` MUST have `import org.slf4j.*` in its import list, even if it already imports `Logger` / `LoggerFactory`. Without the wildcard import, the inline extension functions are invisible and the compiler reports `Function0<String> is not a String` — this happened on `HttpSchemaConfigSubscriber`, `NacosFunctionConfigSubscriber`, `ApolloKeyedConfigSubscriber` and was fixed by adding the missing import.

2. **KDoc nested-comment lexer quirk**: Kotlin's lexer treats `/*` and `*/` literally **even inside KDoc**, and block comments nest. A phrase like `` `/internal/workflow/registry/*` `` inside a `/** ... */` class header introduces a **second** nested block-comment open, which consumes the first intended closing `*/` of the header — leaving the outer `/**` open at EOF → `Unclosed comment` syntax error. **Rule: never write the literal two-character sequence slash-star inside KDoc**. Replace with `.../.../prefix` or `<code>` HTML tag if you must. This hit `HttpInstanceRegistry`; fix in [HttpInstanceRegistry.kt](file:///D:/Projects/workflow/workflow/workflow-platform/fluxion-config/registry-http/src/main/kotlin/com/fluxion/registry/http/HttpInstanceRegistry.kt).

3. **WebFlux coroutine bridge**: Inside `mono { }` (kotlinx-coroutines-reactor) returning `Mono<ServerResponse>`, the block itself yields `ServerResponse`. Therefore when you have a `Mono<ServerResponse>` from `ServerResponse.ok().bodyValue(…)` you need `.awaitSingle()` — which imports as `kotlinx.coroutines.reactor.awaitSingle`. If you import only `awaitSingleOrNull` you'll hit "Unresolved reference: awaitSingle"; if you forget `awaitSingle()` entirely you'll get `Mono<Mono<ServerResponse>>` and the compiler will complain `expected: ServerResponse, actual: Mono<ServerResponse>`. This affected [WebFluxWorkflowHandler.kt](file:///D:/Projects/workflow/workflow/workflow-platform/fluxion-adapter-http/webflux/src/main/kotlin/com/fluxion/adapter/http/webflux/WebFluxWorkflowHandler.kt).

4. **Coroutine-safe state**: Always use `@Volatile` for any nullable reference that a scheduled daemon thread (e.g., `HttpInstanceRegistry.registeredInstanceId`) and the main Spring lifecycle thread both touch; avoid `lateinit var` for such fields — they can race during `destroy()`.

5. **Minimal dependency footprint for tiny modules**: `fluxion-config:registry-http` uses JDK `java.net.http.HttpClient` directly and renders JSON via a raw triple-quoted string rather than pulling Jackson — because this module is on the critical classpath of the launcher JAR and we want the bootstrap footprint minimal. Resist the urge to add Jackson here.

---

## Build & Validation

- **Kotlin compilation check** (the canonical verification used during this refactor):
  ```
  ./gradlew.bat compileKotlin
  # Result: BUILD SUCCESSFUL (69 tasks)
  ```
- This confirms all ~200+ `.kt` source files across every module and every `.kts` build script compile cleanly after the comment rewrite.
