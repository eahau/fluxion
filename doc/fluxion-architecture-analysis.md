# Fluxion Workflow Platform — Architecture Analysis & Code Understanding

> Generated: 2025-07-08 | Language: English

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Module Deep Dive](#3-module-deep-dive)
4. [Core Design Concepts](#4-core-design-concepts)
5. [Data Flow & Execution Model](#5-data-flow--execution-model)
6. [API & Protocol Layer](#6-api--protocol-layer)
7. [Key Abstractions & Interfaces](#7-key-abstractions--interfaces)

---

## 1. Project Overview

| Attribute | Value |
|---|---|
| **Project** | Fluxion Workflow Platform |
| **Type** | Multi-module Kotlin/Gradle Monorepo |
| **Build** | Gradle 8.x + Kotlin DSL (`*.kts`) |
| **Language** | Kotlin 2.1.20 |
| **JVM Target** | Java 21 (Virtual Thread support) |
| **Framework** | Spring Boot 3.2.5 |
| **Coroutines** | Kotlin Coroutines 1.9.0 |
| **Total Modules** | 60+ submodules |
| **Source Files** | 500+ `.kt` files, 62 `.kts` build scripts |

**Purpose:** An enterprise-grade workflow orchestration platform using **Control Plane / Data Plane separation**:
- **fluxion-admin** = Control Plane (management UI, workflow definition CRUD, function registry)
- **fluxion-runtime** = Data Plane (Sidecar/Worker that pulls config and executes workflows)

---

## 2. High-Level Architecture

```
                         ┌──────────────────────────┐
                         │     fluxion-admin          │
                         │    (Spring Boot App)       │
                         │  - REST API (MVC/WebFlux)  │
                         │  - Workflow Definition CRUD │
                         │  - Function / Schema Mgmt   │
                         │  - Users / Roles / ACL      │
                         │  - Marketplace              │
                         └──────────┬─────────────────┘
                                    │ Publish definitions,
                                    │ functions, schemas
                                    ▼
           ┌─────────────────────────────────────────────────┐
           │              fluxion-config                      │
           │    Apollo / Nacos / HTTP Push                   │  Configuration Center
           │    (subscription, publishing, instance registry)  │
           └──────────────────────┬──────────────────────────┘
                                  │ Pull configuration
                                  ▼
           ┌─────────────────────────────────────────────────┐
           │              fluxion-runtime                     │  Data Plane
           │         (Spring Boot Sidecar / Worker)           │
           │    - Register with Admin                         │
           │    - Pull workflow defs & function configs        │
           │    - Expose HTTP / RPC / MQ endpoints            │
           └───────────────┬─────────────────────────────────┘
                           │
           ┌───────────────┴─────────────────────────────────┐
           │                fluxion-engine                     │  Execution Engine
           │   WorkflowEngine → DagExecutor                    │
           │   (Coroutines + Virtual Threads, DAG Topology)    │
           │   SagaExecutor, LoopExecutor, WaitExecutor       │
           └───────────────┬─────────────────────────────────┘
                           │
      ┌────────────────────┼────────────────────┐
      ▼                    ▼                     ▼
  fluxion-            fluxion-              fluxion-
  function            decorator             schema
  (Function           (Decorator Chain:     (Schema
   Registry)           Lock/RateLimit/       Validation)
                       Tracing/Async)
```

### Separation of Concerns

| Plane | Module | Responsibility |
|---|---|---|
| **Control** | `fluxion-admin` | Web UI backend, REST APIs, definition/function/schema CRUD, user/role management, marketplace |
| **Data** | `fluxion-runtime` | Sidecar that registers with admin, pulls config, exposes protocol endpoints, executes workflows |
| **Shared** | `fluxion-core` | Core abstractions, models, enums, utilities — zero framework dependency in `base` submodule |
| **Shared** | `fluxion-engine` | DAG-based workflow execution engine — independent module |
| **Shared** | `fluxion-function` | Function abstraction: registry, resolver, builtin/external functions |
| **Shared** | `fluxion-schema` | Data contract foundation: JSON Schema / Protobuf / Avro |
| **Shared** | `fluxion-decorator` | AOP-style workflow enhancement chain (lock, rate-limit, tracing, async) |
| **Adapter** | `fluxion-adapter-http` | HTTP protocol: SpringMVC + WebFlux |
| **Adapter** | `fluxion-adapter-rpc` | RPC protocol: Dubbo + gRPC |
| **Adapter** | `fluxion-adapter-mq` | MQ protocol: Kafka |
| **Infra** | `fluxion-config` | Config center: Apollo / Nacos / HTTP Push |
| **Infra** | `fluxion-redis` | Redis: Lettuce / Redisson / Spring Data |
| **Infra** | `fluxion-script-engine` | Script execution: Groovy / JavaScript |

---

## 3. Module Deep Dive

### 3.1 `fluxion-core` — Core Abstractions

The foundation of the entire platform. The `base` submodule has zero framework dependencies.

**Key Files:**

| File | Purpose |
|---|---|
| `WorkflowDefinition.kt` | Workflow definition model: ID, version, DAG structure, parameter schemas |
| `WorkflowNode.kt` | Node definition: ID, type, function ref, inputs, dependsOn, next, conditionalNexts |
| `WorkflowInstance.kt` | Runtime instance: execution ID, status, progress, context |
| `NodeType.kt` | Enum: `FUNCTION`, `CONDITION`, `LOOP`, `WAIT`, `SUB_WORKFLOW`, `SAGA` |
| `NodeStatus.kt` | Enum: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `SKIPPED`, `TIMEOUT` |
| `ErrorStrategy.kt` | Enum: `FAIL`, `RETRY`, `COMPENSATE`, `IGNORE` |
| `Protocol.kt` | Enum: `HTTP`, `GRPC`, `DUBBO`, `KAFKA` |
| `DagTopology.kt` | DAG topology sort and cycle detection |
| `ImmutableExecutionState.kt` | Immutable execution state — each node execution produces a new snapshot |
| `JsonUtil.kt` | Jackson-based JSON utility (global ObjectMapper config) |

**Spring Boot submodule:**
- `FluxionCoreAutoConfiguration.kt` — Main auto-configuration entry point
- Native Image hints for GraalVM compatibility

### 3.2 `fluxion-engine` — DAG Execution Engine (Core)

The heart of the platform. Orchestrates workflow execution using DAG topology + Kotlin coroutines.

**Key Files:**

| File | Size | Purpose |
|---|---|---|
| `WorkflowEngine.kt` | 25KB | Main engine: coroutine-based execution, Virtual Thread timeout control, node lifecycle management |
| `DagExecutor.kt` | 15KB | DAG topology executor: node orchestration, dependency scheduling, parallel execution |
| `RuleEvaluator.kt` | 18KB | Conditional rule evaluation engine (JSONPath, expression language) |
| `ExpressionEvaluator.kt` | 8.8KB | Expression evaluation using JEXL3 |
| `SagaExecutor.kt` | — | Saga distributed transaction executor with compensation |
| `LoopExecutor.kt` | — | Loop control node (for-each, while) |
| `SubWorkflowExecutor.kt` | — | Sub-workflow invocation |
| `WaitExecutor.kt` | — | Wait/async callback node |
| `IdempotencyStore.kt` | — | Idempotency storage (Caffeine cache) |
| `DeadLetterQueue.kt` | — | Dead letter queue for failed executions |
| `RetryScheduler.kt` | — | Retry scheduling with exponential backoff |

**Execution Flow:**
1. `WorkflowEngine.execute()` receives a workflow definition and input
2. `DagExecutor` performs topological sort on the node graph
3. Nodes are executed in order respecting `dependsOn` dependencies
4. Each node is wrapped in a coroutine with Virtual Thread timeout control
5. Decorator chain is applied (lock, rate-limit, tracing)
6. Function resolution → schema validation → function invocation
7. Result is mapped to output schema and stored in execution state
8. Conditional `next` routing determines the next node(s)

### 3.3 `fluxion-function` — Function System

Functions are the execution units of workflow nodes. The function system is designed around a registry pattern with versioning support.

**Key Files:**

| File | Purpose |
|---|---|
| `WorkflowFunction.kt` | Core function interface: `execute(input, context)` |
| `FunctionRegistry.kt` | Global function registry (thread-safe, version-aware) |
| `FunctionResolver.kt` | Resolves functions by name + version from registry |
| `FunctionVersion.kt` | Semantic version wrapper for functions |
| `VersionedFunction.kt` | Function with version metadata |
| `FunctionRegistrar.kt` | SPI for registering functions |
| `FunctionComponent.kt` | Annotation-based function component discovery |

**Builtin Functions (`fluxion-function/builtin/`):**

| Category | Functions |
|---|---|
| HTTP | `HttpCallFunction` — execute HTTP requests |
| Database | `DbExecuteFunction`, `DbQueryFunction` — SQL execution with dialect support |
| MQ | `MqPublishFunction` — publish messages to Kafka |
| JSON | `JsonTransformFunction`, `JsonPathFunction` — JSON processing |
| Flow Control | `ConditionFunction`, `LoopFunction`, `WaitFunction` |
| Cache | Cache get/set/delete functions with Caffeine backend |
| Validation | Schema validation, type checking |

**External Functions:**
- `DubboFunctionInvoker` — Invoke remote Dubbo services
- `GrpcFunctionInvoker` — Invoke remote gRPC services
- `HttpFunctionInvoker` — Invoke remote HTTP services
- `FunctionMetaRegistry` — Function metadata management

### 3.4 `fluxion-schema` — Data Contract Foundation

Supports three schema formats with unified abstraction:

| Format | Submodule | Capabilities |
|---|---|---|
| **JSON Schema** | `json/` | Parse, validate, field extraction |
| **Protobuf** | `protobuf/` | Parse, validate, serialize/deserialize |
| **Avro** | `avro/` | Parse, validate, serialize/deserialize |

**Core Abstractions:**
- `SchemaManager` — Global schema registry
- `SchemaRegistry` — Schema storage and retrieval
- `SchemaValidator` — Validation interface (format-agnostic)
- `SchemaDataProvider` — Provides schema data to the engine
- `SchemaConfigApplier` — Applies schema configurations from config center

### 3.5 `fluxion-decorator` — Decorator Chain

AOP-style interceptors that enhance workflow execution without modifying core logic.

**Decorator Types:**

| Decorator | Purpose |
|---|---|
| `WorkflowLockDecorator` | Distributed lock for workflow-level exclusivity |
| `RateLimitDecorator` | Rate limiting with sliding window algorithm |
| `OtelContextTaskInterceptor` | OpenTelemetry context propagation for distributed tracing |
| `AsyncDecorator` | Asynchronous execution support |

**Key Files:**
- `DecoratorRegistry.kt` — Manages decorator chain registration
- `DistributedLockDecorator.kt` — Redisson-based distributed lock implementation
- `SlidingWindowState.kt` — Sliding window rate limiter state
- `LocalRateLimitStore.kt` — Local rate limit counter storage

### 3.6 Protocol Adapters

#### `fluxion-adapter-http`
- **Core:** Route registration abstraction (`HttpRouteDefinition`, `RouteConfigStore`)
- **SpringMVC:** `MvcRouteRegistry`, `MvcWorkflowHandler` — traditional Servlet-based HTTP
- **WebFlux:** Reactive route registration — non-blocking HTTP
- **Config Stores:** Nacos and Apollo implementations for route storage

#### `fluxion-adapter-rpc`
- **Dubbo:** `DubboWorkflowService` — Expose workflows as Dubbo services
- **gRPC:** `GrpcWorkflowService` — Expose workflows as gRPC services with metadata interceptor

#### `fluxion-adapter-mq`
- **Kafka:** `KafkaPublisher` + `KafkaConsumer` — Publish/subscribe workflow triggers via Kafka

### 3.7 `fluxion-config` — Configuration Center

Pluggable configuration center supporting three backends:

| Backend | Class | Features |
|---|---|---|
| **Apollo** | `ApolloConfigSubscriber` | Ctrip Apollo config center |
| **Nacos** | `NacosConfigSubscriber` | Alibaba Nacos config center |
| **HTTP Push** | `HttpPushConfigSubscriber` | Simple HTTP push-based config |

**Configuration Types:**
- Workflow definitions
- Function configurations
- Schema definitions
- Instance registrations

### 3.8 `fluxion-admin` — Management Backend

The Control Plane application with these layers:

| Layer | Contents |
|---|---|
| **Controller** | REST API endpoints (MVC + WebFlux) |
| **Service** | Business logic: definition management, function management, schema management |
| **Repository** | JPA repositories for persistence |
| **Entity** | JPA entities: `WfDefinition`, `WfFunction`, `WfSchema`, `WfUser`, `WfRole` |
| **Security** | Authentication/authorization with ACL integration |
| **DTO** | Request/Response DTOs |
| **Config** | Spring configuration, security config, CORS config |

### 3.9 `fluxion-runtime` — Runtime Sidecar

The Data Plane worker. Registered with admin, pulls workflow definitions and function configs, and exposes protocol endpoints for triggering workflows.

---

## 4. Core Design Concepts

### 4.1 Immutable Execution State

Every node execution produces a new `ImmutableExecutionState` snapshot. This ensures:
- Thread safety in concurrent execution
- Audit trail of state transitions
- Easy rollback/replay capability

### 4.2 Hybrid DAG + Linear Orchestration

Nodes can be connected in two ways:
- **DAG mode:** `dependsOn` — parallel execution when dependencies are resolved
- **Linear/conditional mode:** `next` + `conditionalNexts` — sequential with branching

### 4.3 Decorator Chain Pattern

`WorkflowEngine` → `DecoratorChain` → `Function` 

Decorators are applied at node level or workflow level and execute before/after the actual function:
1. Distributed Lock → ensures single execution
2. Rate Limit → sliding window protection
3. Tracing → OpenTelemetry spans
4. Function → actual business logic

### 4.4 Multi-Protocol Architecture

The same workflow can be exposed through multiple protocols:
- HTTP REST (SpringMVC synchronous or WebFlux reactive)
- RPC (Dubbo for internal microservices, gRPC for cross-language)
- MQ (Kafka for async event-driven triggers)

### 4.5 Function Versioning

Functions support semantic versioning via `FunctionVersion`. The `FunctionRegistry` maintains version awareness, allowing:
- Multiple versions of a function to coexist
- Graceful function upgrades
- Version-specific schema validation

### 4.6 Saga Pattern for Distributed Transactions

`SagaExecutor` implements the Saga pattern:
- Forward execution of steps
- On failure, automatic compensation via compensating functions
- Ensures eventual consistency across distributed services

---

## 5. Data Flow & Execution Model

```
Trigger (HTTP/RPC/MQ)
    │
    ▼
WorkflowEngine.execute()
    │
    ├─ Load workflow definition (from registry/cache)
    ├─ Validate input schema
    ├─ Create ImmutableExecutionState
    │
    ▼
DagExecutor.execute()
    │
    ├─ Topological sort of nodes
    │─ For each layer (parallel-ready nodes):
    │   ├─ Apply decorator chain
    │   ├─ Resolve function (registry lookup by name + version)
    │   ├─ Validate function input against schema
    │   ├─ Execute function (with timeout, retry, circuit breaker)
    │   ├─ Map output to output schema
    │   └─ Update execution state
    │
    ▼
Return result
    │
    ├─ Conditional next routing
    ├─ Saga compensation (if needed)
    └─ Dead letter queue (on unrecoverable failure)
```

---

## 6. API & Protocol Layer

### REST API Patterns (fluxion-admin)

```
POST   /api/v1/workflows/definitions       — Create definition
GET    /api/v1/workflows/definitions/{id}   — Get definition
PUT    /api/v1/workflows/definitions/{id}   — Update definition
DELETE /api/v1/workflows/definitions/{id}   — Delete definition
POST   /api/v1/workflows/definitions/{id}/publish — Publish to runtime

POST   /api/v1/workflows/functions          — Register function
GET    /api/v1/workflows/functions/{name}    — Get function
POST   /api/v1/workflows/schemas            — Register schema
POST   /api/v1/workflows/execute            — Execute workflow
GET    /api/v1/workflows/instances/{id}      — Get execution status
```

### Dynamic Route Registration (Runtime)

At runtime, the platform dynamically registers HTTP/RPC endpoints based on workflow definitions:
1. Runtime pulls definitions from config center
2. Route registration SPI creates dynamic endpoints
3. Each endpoint is backed by the workflow engine

---

## 7. Key Abstractions & Interfaces

### Core Interfaces

```kotlin
// Function execution contract
interface WorkflowFunction {
    suspend fun execute(input: Map<String, Any?>, context: ExecutionContext): FunctionResult
}

// Schema validation contract
interface SchemaValidator {
    fun validate(data: Any, schema: Schema): ValidationResult
}

// Decorator contract
interface WorkflowDecorator {
    suspend fun decorate(node: WorkflowNode, context: ExecutionContext, next: suspend () -> FunctionResult): FunctionResult
}

// Config subscriber contract
interface ConfigSubscriber<T> {
    fun subscribe(key: String, callback: (T) -> Unit)
}

// Lock provider SPI
interface DistributedLockProvider {
    suspend fun <T> withLock(key: String, timeout: Duration, block: suspend () -> T): T
}
```

### Key Enums

```kotlin
enum class NodeType {
    FUNCTION,      // Standard function execution
    CONDITION,     // Conditional branching
    LOOP,          // Loop control (for-each, while)
    WAIT,          // Async wait/callback
    SUB_WORKFLOW,  // Sub-workflow invocation
    SAGA           // Saga transaction step
}

enum class NodeStatus {
    PENDING,    // Not yet started
    RUNNING,    // Currently executing
    SUCCESS,    // Completed successfully
    FAILED,     // Execution failed
    SKIPPED,    // Skipped (condition not met)
    TIMEOUT,    // Execution timed out
    COMPENSATING // Saga compensation in progress
}

enum class ErrorStrategy {
    FAIL,        // Fail immediately
    RETRY,       // Retry with backoff
    COMPENSATE,  // Trigger Saga compensation
    IGNORE       // Ignore and continue
}
```

---

## Appendix: Module Dependency Graph

```
fluxion-admin ──────┐
                    ├──► fluxion-core (base + spring-boot)
                    ├──► fluxion-engine
                    ├──► fluxion-function
                    ├──► fluxion-schema
                    ├──► fluxion-config
                    ├──► fluxion-adapter-http
                    ├──► fluxion-acl-spi
                    ├──► fluxion-di
                    └──► fluxion-decorator

fluxion-runtime ────┐
                    ├──► fluxion-core
                    ├──► fluxion-engine
                    ├──► fluxion-function
                    ├──► fluxion-schema
                    ├──► fluxion-config
                    ├──► fluxion-adapter-http
                    ├──► fluxion-adapter-rpc
                    ├──► fluxion-adapter-mq
                    └──► fluxion-decorator
```

---

*This document was generated during the comment translation process to capture the complete architecture understanding of the Fluxion Workflow Platform.*
