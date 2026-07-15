# Fluxion · 函数式工作流编排平台

> **Fluxion** — 「意图之流」（Intent Flux + `-ion`）。AI 原生、函数式的工作流编排与执行平台：开发者以 DAG 表达业务意图，AI 自动生成可执行工作流（JSON Schema + 函数配置），经**零停机发布**后，Worker 节点通过配置中心热加载并动态注册协议路由，实时生效。

---

## 快速链接

| 主题 | 文档 / 资源 |
|------|------------|
| 后端模块架构详解 | [PROJECT_ARCHITECTURE.md](workflow-platform/PROJECT_ARCHITECTURE.md) |
| 核心设计哲学 | [fluxion-design.md](workflow-platform/docs/fluxion-design.md) |
| OpenAPI 契约（前后端共享） | [doc/openapi.yaml](doc/openapi.yaml) |
| 数据库 DDL | [workflow-ddl.sql](workflow-platform/docs/workflow-ddl.sql) |
| 前端架构设计 | [admin-frontend-design.md](workflow-admin-ui/docs/admin-frontend-design.md) |
| 部署配置 | [workflow-platform/deploy/](workflow-platform/deploy) |
| Schema 兼容性策略 | [schema-compatibility-guide.md](doc/schema-compatibility-guide.md) |

---

## 目录

- [1. 项目简介](#1-项目简介)
- [2. 架构图示](#2-架构图示)
  - [2.1 模块依赖关系图](#21-模块依赖关系图)
  - [2.2 端到端执行流程](#22-端到端执行流程)
  - [2.3 管理后台内部架构](#23-管理后台内部架构)
  - [2.4 平台整体分层示意](#24-平台整体分层示意)
- [3. 设计哲学与核心特性](#3-设计哲学与核心特性)
- [4. 整体架构分层](#4-整体架构分层)
- [5. 技术栈](#5-技术栈)
- [6. 仓库结构](#6-仓库结构)
- [7. 核心概念](#7-核心概念)
- [8. 协议与适配器](#8-协议与适配器)
- [9. 控制面自举（Meta Workflows）](#9-控制面自举meta-workflows)
- [10. 函数发布机制](#10-函数发布机制)
- [11. Schema 兼容性策略](#11-schema-兼容性策略)
- [12. 前端 SDK（类型安全）](#12-前端-sdk类型安全)
- [13. 快速开始](#13-快速开始)
- [14. 开发规范与测试](#14-开发规范与测试)
- [15. 文档导航](#15-文档导航)

---

## 1. 项目简介

Fluxion 是一套**函数式工作流编排引擎 + 配置后台**的完整解决方案，严格分离「控制面（Control Plane）」与「运行面（Data Plane）」：

| 角色 | 模块 | 职责 |
|------|------|------|
| **控制面** | `fluxion-admin`（Spring Boot 3）+ `workflow-admin-ui`（React） | 工作流定义、Schema 管理、函数注册、版本发布、在线调试、监控审计、元工作流 |
| **运行面** | `fluxion-runtime`（Worker / Sidecar） | 自注册、热加载工作流/函数/Schema，绑定 HTTP/RPC/MQ 协议端口并执行 DAG |
| **基础设施**| 基础设施 | MySQL 8 · Redis · 配置中心（Apollo/Nacos/HTTP）· 注册中心（Nacos/Consul/Eureka/Zookeeper） | 持久化、幂等/限流、分布式锁、配置推送与服务发现 |

平台核心能力：

- **可视化 DAG 编排**：React Flow 画布拖拽，支持并行 DAG、条件分支、子工作流、循环、Saga 补偿事务
- **函数是一等公民**：业务原子逻辑封装为 `WorkflowFunction<O>`，引擎零硬编码业务；内置 `builtin:*`、`SCRIPT`（Groovy/AviatorScript）、`EXTERNAL`（HTTP/Dubbo/gRPC）三类函数
- **多协议接入**：HTTP（SpringMVC / WebFlux）、Dubbo、gRPC、Kafka；所有入口统一翻译为 `UnifiedRequest`
- **零停机发布 + 灰度**：Admin 写入定义 → ConfigPublisher → 配置中心 → Worker 监听 diff；旧版本进入 `RETIRING`，在途调用归零后清理；支持 `APP_GROUP` / `INSTANCES` 级灰度
- **在线调试**：执行历史 Pin + Replay、单步、断点、Mock 函数响应（AviatorScript 表达式）
- **企业级可观测性**：Micrometer → Prometheus、OpenTelemetry Trace → Jaeger/SkyWalking、结构化日志 → ELK/Loki
- **自举（Self-Hosting）**：管理后台自身的「函数发布/工作流发布/下线」由 **META 元工作流**驱动

---

## 2. 架构图示

以下 SVG 静态图可在 GitHub / IDE 内直接渲染，来源与代码实际结构对齐（settings.gradle.kts 69 个模块逐项校验）。

### 2.1 模块依赖关系图（Module Dependency）

六层分层架构：**L0 SPI/基础 → L1 核心引擎 → L2 横切组件 → L3 能力域 → L4 协议适配器 → L5 应用层**。应用层仅作为「最终消费者」装配所有适配器，不被任何模块反向依赖。

![模块依赖关系图](doc/diagrams/module-dependency.svg)

> 图例说明：紫色=SPI→核心，绿色=核心→横切/引擎内进，蓝色=能力域装配，灰色=适配器，橙色=配置推送，黑色=前端 OpenAPI 契约调用。

### 2.2 端到端执行流程（Process Flow）

三泳道视角：**LANE A 控制面配置发布 → LANE B 运行面单次执行（Linear / DAG / Saga 三引擎） → LANE C 基础设施支撑**。

![端到端执行流程图](doc/diagrams/process-flow.svg)

> 单请求主路径：外部协议 → 适配层（统一请求） → WorkflowRouter（Caffeine L1 取 WfDef 快照） → InputSchema 校验 → ImmutableState.start → 引擎三选一 → Node 循环（buildInput → resolve+装饰器链 → fn.apply → ErrorStrategy → 写快照 → resolveNext） → OutputSchema 校验 → EngineResult → 适配层反向翻译 → 响应。

### 2.3 管理后台内部架构

![管理后台内部架构](doc/diagrams/admin-backend.svg)

### 2.4 平台整体分层示意

![平台整体分层示意](doc/diagrams/architecture-layers.svg)

---

## 3. 设计哲学与核心特性

### 3.1 工作流 = 受控的调用栈

将函数式编程模型映射为工作流执行：

| CPU 调用栈 | Fluxion 工作流 |
|-----------|---------------|
| Stack Frame（栈帧） | `WorkflowNode`（节点） |
| return value（返回值） | `NodeOutput` — 下一节点的直接入参 |
| 函数形参（局部，不可变） | `NodeInput.directInput`（只读） |
| 调用方传递的参数 | `rawInput`（工作流原始入参） |
| 已结束帧的返回值（只读） | `ImmutableExecutionState.nodeOutputs`（append-only） |
| TLS（线程本地存储） | `executionId`、`traceId` 等执行元信息 |
| Program Counter | `currentNode` / `readySet` 指针 |

**核心约束（四大原则）**

1. **Function as First-Class Citizen**：能独立编程、独立测试的逻辑都表达为 `WorkflowFunction<O>`，支持 `andThen` 函数组合
2. **Explicit Data Flow**：节点间依赖图可静态分析，无隐式耦合；节点只能看到「直接入参 + 显式声明的前置输出」
3. **Immutable State**：每次节点完成产生新的不可变快照，旧快照永久保存 → 天然支持重放、调试、热加载一致性
4. **Isolated Execution**：DAG 并行无锁（Kotlin Coroutines），测试可完全隔离；节点函数不能写入共享状态 — 返回值是唯一输出通道

### 3.2 调度能力边界

核心引擎只负责**函数式工作流的一次性执行与编排**，**不内置**定时/延时任务调度。`TriggerType` 保留 `MANUAL` / `WEBHOOK` / `EVENT` / `API`（已移除 `CRON`）。周期性/延时触发建议由外部调度平台（Quartz / XXL-JOB / 延迟队列）在触发点回调已有的 HTTP/RPC/MQ 入口。

### 3.3 控制面与运行面职责边界

| 角色 | 职责 |
|------|------|
| **Runtime（Worker）** | 启动后自注册到注册中心；订阅配置中心推送；绑定协议端口；**仅**负责工作流执行 |
| **Admin** | 统一存储/管理工作流定义、函数、Schema；配置发布、版本管理、实例发现、监控查询 |
| **注册/配置中心** | 仅服务发现 + 配置推送，不承担工作流元数据主存储 |

> Admin **不代理查询** Runtime 的工作流列表 — 两者通过配置中心松耦合，Java 8 业务系统只需远程接入 Runtime 即可。

---

## 4. 整体架构分层

自底向上（依赖方向严格单向，禁止循环）：

```
┌───────────────────────────────────────────────────────────────────────┐
│  L5 · 应用层 Applications                                              │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐  ┌──────┐ │
│  │ fluxion-admin│  │fluxion-runtime│  │workflow-admin-ui │  │Deploy│ │
│  │ (控制面 Boot)│  │(运行面 Worker)│  │(Umi/React 前端)  │  │CI/CD │ │
│  └──────────────┘  └───────────────┘  └──────────────────┘  └──────┘ │
└──────────────────────────────┬────────────────────────────────────────┘
                               │ 装配（引入所有 :spring-boot 自动配置）
┌──────────────────────────────▼────────────────────────────────────────┐
│  L4 · 协议适配器 Protocol Adapters                                     │
│  fluxion-adapter-http (MVC/WebFlux) · -rpc (Dubbo/gRPC) · -mq (Kafka)  │
│  统一 → UnifiedRequest / UnifiedResponse → WorkflowRouter              │
└──────────────────────────────┬────────────────────────────────────────┘
┌──────────────────────────────▼────────────────────────────────────────┐
│  L3 · 能力域 Capability Domains（每个 domain:core / :backend / :sb）   │
│  fluxion-schema:*  ·  fluxion-function:*  ·  fluxion-config:*          │
│  fluxion-registry:*  ·  fluxion-discovery:*  ·  fluxion-redis:*        │
│  fluxion-script:*                                                      │
└──────────────────────────────┬────────────────────────────────────────┘
┌──────────────────────────────▼────────────────────────────────────────┐
│  L2 · 横切 / 组件 Cross-Cutting                                        │
│  fluxion-decorator (tx/cache/retry/ratelimit/metrics/trace/...)        │
│  fluxion-di:spring · fluxion-debug · fluxion-mock                      │
└──────────────────────────────┬────────────────────────────────────────┘
┌──────────────────────────────▼────────────────────────────────────────┐
│  L1 · 核心引擎 Core & Engine（零业务 / 零 Spring 依赖）                 │
│  fluxion-core（值类型/模型/JsonUtil）· fluxion-engine（Dag/Saga/Linear）│
│  fluxion-schema（JSON/Protobuf/Avro 校验）                              │
└──────────────────────────────┬────────────────────────────────────────┘
┌──────────────────────────────▼────────────────────────────────────────┐
│  L0 · SPI & 基础（零业务 / 零框架依赖）                                 │
│  fluxion-log（SLF4J Kotlin 懒日志扩展）                                 │
│  fluxion-adapter-spi（Router/Adapter/Config/Registry SPIs）            │
│  fluxion-acl-spi（权限/认证钩子，仅 Admin 使用）                        │
└───────────────────────────────────────────────────────────────────────┘
```

> 每一层的 `:spring-boot` 子模块仅负责「自动装配」，**绝不包含核心业务逻辑**；核心逻辑永远放在同域的 `:core` 中，确保非 Spring 环境也能直接实例化。

---

## 5. 技术栈

### 5.1 后端

| 类别 | 选型 | 约束 / 备注 |
|------|------|-------------|
| 语言 | Kotlin 2.x（JDK 21 target） + Java 互操作 | Java 8 业务系统通过 Runtime 远程接入，无需升级 |
| 构建 | Gradle 8.x · Kotlin DSL | settings.gradle.kts 统一仓库（阿里云镜像）；禁止子项目 declare repositories |
| 运行时 | Spring Boot 3.x · Spring Framework 6 | 虚拟线程 + Kotlin Coroutines（WebFlux 经 kotlinx-coroutines-reactor 桥接） |
| 配置中心 | Apollo / Nacos / HTTP | 启动时 Apollo→Nacos→HTTP 兜底；HTTP 用 JDK HttpClient 保持最小依赖 |
| 服务注册/发现 | Nacos / Consul / Eureka / Zookeeper（基于 Spring Cloud Commons） | 依赖 fluxion-starters 聚合模块按需引入；支持即插即用切换 |
| 持久化 | Spring Data JPA + Hibernate + Flyway | MySQL 8 InnoDB；ddl-auto=validate，所有 schema 变更走 Flyway migration |
| 缓存/分布式 | Redis（Lettuce / Redisson / Spring Data 三选一） | Lua 脚本限流、分布式锁、幂等存储、缓存装饰器 |
| RPC | Apache Dubbo 3 · gRPC（protobuf-gradle-plugin codegen） | 共享 `WorkflowRouter`，协议仅负责编解码 |
| MQ | Apache Kafka | request/reply 模式，headers 带 `x-workflow-id` / `x-reply-to` |
| 脚本/表达式 | Groovy（核心，带编译缓存） · AviatorScript（dev/Mock/规则评估） | **生产禁用 Spring Expression** 避免锁键解析 |
| API 契约 | OpenAPI 3.0 → openapi-generator `kotlin-spring` | 生成代码位于 `build/generated/openapi/`，**禁止手写修改**；容错逻辑写在 src/main |
| 日志规范 | SLF4J + Log4j2（结构化） | 所有 .kt 文件 **必须**用 `private val log = LoggerFactory.getLogger(javaClass)` + `log.xxx { "..." }` 懒 lambda；禁用 Lombok `@Slf4j` |

### 5.2 前端

| 类别 | 选型 | 备注 |
|------|------|------|
| 框架 | UmiJS 4（`@umijs/max`）· React 18 · TypeScript 5 | 路由/权限/请求/国际化走 Umi 插件体系 |
| UI | Ant Design 5.x（动态主题） | `App.useApp().message` 替代静态 `message.xxx()` |
| 画布 | React Flow 11 + 自定义节点/边 | 条件边（ConditionalEdge）、12 种节点卡片、快捷键 + 自动布局 |
| 表单 | `@rjsf/core` + Ant Design theme（JSON Schema 驱动） | AJV + `createAjvWithSchemaRefPlaceholders` 支持自定义 `schema:name` 引用 |
| 编辑器 | Monaco Editor（Groovy / AviatorScript / JSON / SQL） | `ExprEditor.tsx`（原 JexlExpressionEditor）按引擎切换关键字/函数提示 |
| 状态 | Zustand（画布/调试/Schema store）+ Umi `initialState`（用户/权限） | schemaMemoryCache 做模块级缓存，CRUD 时同步更新 |
| 图表 | ECharts 5 | 监控仪表盘、节点执行统计、Trace 时间线 |
| **类型生成** | `openapi-typescript` + `openapi-fetch` | `pnpm openapi` 生成 `api.generated.d.ts` → 全 SDK 类型安全 |
| SDK 入口 | `src/sdk/client.ts` + `src/sdk/index.ts`（`unwrap`/`silentHeaders`） | 经 Umi `request` 桥接，复用拦截器/代理/错误 toast |

### 5.3 Java 8 业务系统接入（Sidecar 模式）

主技术栈为 Java 21，但 Java 8 老业务系统可通过**控制面-运行面分离**远程接入，无需升级自身 JDK：

1. **控制面**：`fluxion-admin`（Java 21）管理工作流/函数/Schema，经配置中心向 Worker 推送定义
2. **运行面**：`fluxion-runtime` 作为独立 Worker 部署（sidecar 模式，Java 21），从配置中心拉取定义；默认暴露 HTTP `:8081`、gRPC `:50051`
3. **接入方式**：Java 8 业务系统以**纯调用方**身份调用 Runtime 暴露的 HTTP/RPC/MQ 接口，Runtime 承载执行

> 长期演进：可抽取 `fluxion-api`（Java 8 兼容的 DTO 契约层）+ `fluxion-client-java8`（HTTP 封装 + 重试 + 链路头注入）。详见 [fluxion-design.md §附录 H](workflow-platform/docs/fluxion-design.md)

---

## 6. 仓库结构

```
workflow/
├── doc/                                     # 全局契约 & 架构 SVG & 指南
│   ├── openapi.yaml                         #   后端 OpenAPI 3.0 契约（前端 TS 类型来源）
│   ├── schema-compatibility-guide.md        #   只增不删兼容性策略
│   └── diagrams/                            #   4 张 SVG 架构图（GitHub 直接可渲染）
│       ├── module-dependency.svg            #     6 层模块依赖（L0→L5）
│       ├── process-flow.svg                 #     3 泳道端到端执行流程
│       ├── admin-backend.svg                #     管理后台内部结构
│       └── architecture-layers.svg          #     平台分层总览
│
├── workflow-platform/                       # 后端平台（69 模块 Gradle 工程）
│   ├── settings.gradle.kts                  #   模块清单 & 仓库统一定义（PREFER_SETTINGS）
│   ├── build.gradle.kts                     #   根构建脚本（依赖管理 + Kotlin/Spring 插件）
│   ├── PROJECT_ARCHITECTURE.md              #   每模块职责/扩展点/坑点索引文档
│   ├── build-after-comments.log             #   构建日志（BUILD SUCCESSFUL，69 tasks）
│   │
│   ├── docs/                                 #   设计文档 & SQL 样例
│   │   ├── fluxion-design.md                #     核心设计哲学 + 抽象详解
│   │   ├── fluxion-long-connection-technical-design.md  #  WebSocket/TCP Actor 化长连接方案
│   │   ├── function-publish-guide.md        #     Admin→ConfigCenter→Worker 函数热加载链路
│   │   ├── admin-meta-workflows.sql         #     META 元工作流初始化（自举）
│   │   ├── workflow-ddl.sql                 #     平台全部建表语句
│   │   ├── sample-workflow-hello.json       #     Hello World 工作流样例
│   │   └── *.md （函数元数据 SPI / 迁移设计等）
│   │
│   ├── deploy/                               #   部署：Docker / k8s / GitOps / 限流脚本
│   │   ├── Dockerfile                        #     GraalVM native-image 可选
│   │   ├── docker-compose.yaml               #     dev profile: MySQL+Redis+Admin+Worker
│   │   ├── k8s/deployment.yaml               #     Kubernetes manifests
│   │   ├── gitops/argocd-application.yaml    #     ArgoCD Application CRD
│   │   ├── load-rate-limit-script.sh/.py     #     Redis Lua 限流脚本预加载
│   │   └── build.sh
│   │
│   ├── .github/workflows/                    #   CI：fluxion-publish (Maven 发布) · fluxion-deploy (镜像 + GitOps 推送)
│   │
│   ├── 【L0】 SPI / 基础模块
│   ├── fluxion-log/                          #   SLF4J Kotlin 懒日志扩展
│   ├── fluxion-adapter-spi/                  #   UnifiedRequest/Router/Config/Registry SPIs
│   ├── fluxion-acl-spi/                      #   权限/认证 SPI（仅 Admin 装配）
│   │
│   ├── 【L1】 核心 & 引擎
│   ├── fluxion-core/                         #   基类/枚举/JsonUtil/ErrorCode/DagTopology
│   │   └── spring-boot/                      #     Spring Boot 自动装配（值类型/ObjectMapper）
│   ├── fluxion-engine/                       #   WorkflowEngine · DagExecutor · SagaExecutor
│   │                                            LinearEngine · RuleEvaluator · RetryScheduler
│   ├── fluxion-schema/                       #   JSON Schema / Protobuf / Avro 校验 + 第三方 Registry 适配
│   ├── core/ · json/ · protobuf/ · avro/ #     每种格式 core+backend+spring-boot
│   ├── registry/confluent/                #     Confluent Schema Registry 适配
│   ├── registry/aws-glue/                 #     AWS Glue Schema Registry 适配
│   ├── registry/azure/                    #     Azure Schema Registry 适配
│   ├── registry/apicurio/                 #     Apicurio Schema Registry 适配
│   └── spring-boot/
│   │
│   ├── 【L2】 横切 / 组件
│   ├── fluxion-decorator/ + spring-boot/     #   NodeDecorator 实现（metrics/trace/cache/retry/ratelimit/tx/...）
│   ├── fluxion-debug/                        #   FunctionInstanceProvider · DependencyResolver · Debug 钩子
│   ├── fluxion-di/spring/                    #   Spring ApplicationContext 桥接 DI
│   ├── fluxion-script/ core + sb             #   Groovy 求值（编译缓存）
│   ├── fluxion-registry/ core + spring-boot  #   服务注册 SPI + Spring Cloud 适配
│   ├── fluxion-discovery/ core + spring-boot #   服务发现 SPI + Spring Cloud 适配
│   ├── fluxion-mock/                         #   Mock 引擎（AviatorScript 表达式）
│   │
│   ├── 【L3】 能力域（每域:core/:backend/:spring-boot 三层）
│   ├── fluxion-function/                     #   函数注册/发现/调用 SPI
│   │   ├── builtin/ · meta/ · spring-boot/
│   │   └── external/                         #     外部函数出站（dubbo/grpc/http）
│   │       ├── dubbo(/spring-boot) · grpc(/spring-boot) · http(/spring-boot)
│   ├── fluxion-config/                       #   配置中心客户端
│   │   ├── core/ · apollo/ · nacos/ · http/  #     Apollo→Nacos→HTTP 启动优先级
│   │   └── spring-boot/                      #     自动装配配置中心后端
│   ├── fluxion-redis/                        #   Redis 命令 SPI + 限流 Lua
│   │   ├── core/ · lettuce/ · redisson/      #     三后端：Lettuce/Redisson/Spring Data
│   │   ├── spring-data/ · spring-boot/       #     Spring Boot 按 classpath 择优装配
│   │
│   ├── 【L4】 协议适配器
│   ├── fluxion-adapter-http/                 #   HTTP 协议
│   │   ├── core/                              #     HttpRequestProcessor · mergeParams
│   │   ├── springmvc/                         #     WorkflowHandlerMapping 动态路由
│   │   │   ├── nacos/ · apollo/              #       路由配置存储后端
│   │   │   └── spring-boot/                  #       MVC 自动装配
│   │   └── webflux/                          #     RouterFunctions 响应式（coroutine 桥接）
│   ├── fluxion-adapter-rpc/                  #   RPC 协议
│   │   ├── dubbo/ · grpc/                    #     gRPC 带 protobuf-gradle-plugin codegen
│   │   └── spring-boot/                      #       自动导出注册服务
│   ├── fluxion-adapter-mq/                   #   MQ 协议
│   │   ├── kafka/                             #     request/reply topic
│   │   └── spring-boot/                      #       ListenerContainer 装配
│   │
│   ├── 【L5】 应用层（最终消费者）
│   ├── fluxion-admin/                        #   控制面 Spring Boot App
│   │   └── src/main/
│   │       ├── kotlin/com/fluxion/admin/
│   │       │   ├── entity/  · mapper/ · service/
│   │       │   ├── acl/  (LocalAclProvider / RemoteAclClient)
│   │       │   └── util/ (PageableUtils · JsonMapperHelper)
│   │       └── resources/
│   │           ├── db/migration/              #     Flyway V1..Vn（ddl-auto=validate）
│   │           └── application.yaml  (默认 port 8080，local profile 排除 Redis 自动配置)
│   ├── fluxion-runtime/ core + spring-boot + boot jar  #   运行面 Worker (默认 port 8081 + 50051)
│   ├── fluxion-test/ + webflux/              #   test fixtures（InMemory 实现 / WebFlux support）
│   └── fluxion-starters/                     #   聚合 starter（dubbo/grpc/http/mq/nacos/apollo/consul/eureka）
│
└── workflow-admin-ui/                         # 前端配置后台（Umi 4）
    ├── package.json / pnpm-lock.yaml          #   pnpm workspace；脚本 pnpm openapi/dev/build
    ├── config/                                #   Umi 配置：config.ts / routes.ts / proxy.ts
    ├── docs/admin-frontend-design.md          #   前端架构设计文档
    └── src/
        ├── sdk/                               #   类型安全 SDK（由 OpenAPI 契约驱动）
        │   ├── client.ts                      #     openapi-fetch + Umi request 桥接
        │   └── index.ts                       #     unwrap() / silentHeaders() / 类型再导出
        ├── services/                          #   API 服务层（workflow/function/schema/... 全部走 SDK）
        ├── pages/                             #   业务页面：workflow/designer · schema/editor · function/editor · monitor/dashboard · system/...
        ├── components/                        #   通用：FlowCanvas · JsonEditor · Monaco 封装
        ├── stores/  (Zustand: useDebugStore · useSchemaStore · useWorkflowStore)
        ├── constants/ (nodeTypes / errorStrategies / protocol / nodeParamSchemas)
        ├── shared/lib/ (json-schema 工具 / sql-format / sql-history)
        ├── hooks/ (useWorkflowDebug / useSchemaValidation / useKeyboardShortcuts)
        ├── utils/ (flowConverter · schemaCompatibility · schemaDiff · expression)
        └── types/
            ├── api.generated.d.ts             #     pnpm openapi 自动生成（勿手写修改）
            └── api.d.ts · api.ts              #     类型补全 / 向后兼容类型别名
```

完整模块清单（含 69 个子模块）见 [settings.gradle.kts](workflow-platform/settings.gradle.kts)，每模块职责详解见 [PROJECT_ARCHITECTURE.md](workflow-platform/PROJECT_ARCHITECTURE.md)。

---

## 7. 核心概念

### 7.1 工作流函数 `WorkflowFunction<O>`

```kotlin
fun interface WorkflowFunction<O> {
    /** 唯一入口，纯函数式：仅读 NodeInput，仅写 FunctionResult（含 sideEffects） */
    fun apply(input: NodeInput): FunctionResult<O>

    /** 可选：元信息（类名/超时/标签） */
    fun meta(): FunctionMeta = FunctionMeta.of(this::class.java.simpleName)

    /** 可选：失败降级（用于 Saga 补偿路径或优雅降级） */
    fun fallback(input: NodeInput, ex: Throwable): FunctionResult<O> = throw ex

    /** 函数组合，组合后仍是 WorkflowFunction */
    infix fun <V> andThen(after: WorkflowFunction<V>): WorkflowFunction<V> = TODO()
}
```

关键点：
- **sideEffects 显式声明**：I/O 副作用通过 `FunctionResult.sideEffects` 回传，用于 Saga 补偿
- **一致注册**：内置 `builtin:*`、用户自定义 `CUSTOM`、`SCRIPT`（Groovy）、`EXTERNAL`（HTTP/gRPC/Dubbo）共享同一个 `FunctionRegistry`
- **自动发现**：`@FluxionFunction(name="...", timeoutMs=5000)` 注解扫描（由 `fluxion-function:spring-boot` 完成）

### 7.2 不可变执行状态 `ImmutableExecutionState`

```kotlin
data class ImmutableExecutionState(
    val inputs: RawInput,              // 创建后不可变（原始工作流入参）
    val nodeOutputs: Map<NodeId, Any>, // append-only，只读
    val meta: ExecutionMeta,           // executionId / traceId / startTimeMs ...
    val compensationStack: List<CompensationEntry>, // Saga 用
) {
    /** 每次节点完成产生**新快照**，并发读无需锁 */
    fun withNodeOutput(nodeId: NodeId, output: Any): ImmutableExecutionState = copy(...)
    fun mergeNodeOutputs(batch: Map<NodeId, Any>): ImmutableExecutionState = copy(...)
}
```

### 7.3 节点输入 `NodeInput`

节点函数唯一数据入口，强制显式依赖：`directInput`（上一节点返回） + `declaredDeps`（显式引用的前置输出，按 `$ref` 路径提取） + `workflowInput`（原始入参） + `nodeParams`（节点静态配置） + `meta`。

### 7.4 NodeType 双层枚举（AI 接入三层防御）

| 层 | 枚举 | 用途 |
|----|------|------|
| Core（4 种） | `BUILTIN` / `CUSTOM` / `SCRIPT` / `EXTERNAL` | 引擎实际执行分发；**永远不删改** |
| DTO（12+ 种） | `PARAM_VALIDATE` / `DATA_QUERY` / `TRANSFORM` / `CONDITION` / `PARALLEL_FOR_EACH` ... | 前端视觉分类、AI 语义输出；到后端再 normalize 到 Core 枚举 |
| 防御层 | Jackson 反序列化容错 → `dag_json` 规范化清洗 → 读取容错兜底 `CUSTOM` | 抗 AI 幻觉 / 脏数据，永不白屏 |

### 7.5 装饰器（Decorators）

节点（或工作流级）`decorators` 数组声明横切能力，`fluxion-decorator:spring-boot` 按 `@Order` 组合成链注入。内置装饰器：

| 装饰器 | 说明 | 主要参数 |
|--------|------|---------|
| `logging:default` | 默认日志（保存/发布时自动注入首位） | — |
| `trace:span` | OpenTelemetry Span | spanName |
| `metrics:node` | Micrometer timer/counter | — |
| `tx:required` / `tx:requiresNew` | 事务（必需 / 新建独立） | isolation / timeoutMs |
| `cache:redis` | Redis 缓存 | cacheKeyExpr / ttlSeconds |
| `retry:exponential` | 指数退避重试 | maxRetries / baseDelayMs / jitter |
| `ratelimit:slidingWindow` | 滑动窗口限流（Redis Lua） | upLimited / cdSeconds / recoveryPerCd |
| `persist:execution` | 执行持久化（MySQL 装饰器） | debugMode / sampleRate |
| `log:sanitize` | 日志脱敏 | fields / maskChar |
| `async:ioPool` | 异步 IO 线程池调度 | — |
| `idempotency:redis` | 幂等去重（DAG 引擎默认启用） | keyExpr |

> 默认注入策略：保存/发布工作流时写入 `logging:default` 并去重置顶；前端创建新节点默认携带。

### 7.6 三种调度执行器

| 执行器 | 适用场景 | 实现要点 |
|--------|---------|---------|
| **Linear**（默认） | 简单工作流（节点数 ≤ 20，单链路） | 顺序执行 · 虚拟线程 · 低开销 |
| **DagExecutor** | 分支/并行 DAG | Kahn 拓扑 + readySet + Kotlin Coroutines `async{}` · 每节点独立协程依赖等待 |
| **SagaExecutor** | 分布式事务/补偿 | 前向记录 `CompensationEntry` 栈 · 失败逆序回滚 · 补偿失败 Best-Effort + 死信 |

三者**共享同一套**：`buildNodeInput → resolve+装饰器链 → fn.apply → ErrorStrategy → 写快照 → resolveNext` 管线。

---

## 8. 协议与适配器

所有协议适配器的唯一职责是**翻译**：协议原生请求 ⇄ `UnifiedRequest/Response`，然后把执行完全委托给 `WorkflowRouter`。

| 协议 | 适配器模块 | 绑定键 | 说明 |
|------|-----------|--------|------|
| **HTTP** | `fluxion-adapter-http` | `(method, pathPattern)` | SpringMVC（动态 HandlerMapping）+ WebFlux（RouterFunctions）；路由配置由 Nacos/Apollo 动态热更新，无需重启 |
| **Dubbo** | `fluxion-adapter-rpc:dubbo` | `serviceKey` | 暴露 `GenericWorkflowService.invoke(serviceName, methodName, params)` |
| **gRPC** | `fluxion-adapter-rpc:grpc` | protobuf `service/method` | `WorkflowService/Execute`；protobuf-gradle-plugin + protoc grpc-kotlin 插件代码生成 |
| **Kafka** | `fluxion-adapter-mq:kafka` | `topic` | 请求 topic `workflow.request.<group>`；headers 带 `x-workflow-id`、`x-execution-id`、`x-reply-to`（回复 topic） |
| **WebSocket/TCP** | `fluxion-adapter-longconnection:*`（规划中） | `executionId` | Actor 化实例（`WorkflowInstanceActor` 事件驱动状态机） |

统一响应头：`X-Fluxion-Execution-Id` — 永远返回，用于后续 Trace、Replay、Cancel。

---

## 9. 控制面自举（Meta Workflows）

管理后台的「函数发布/弃用、工作流发布/下线」**本身也是工作流**（`category = META`），由引擎执行，形成自举：

| 元工作流 ID | 对应操作 |
|------------|---------|
| `admin-function-publish` | 保存函数定义 → 校验 → 写 DB → Publisher 推配置中心 |
| `admin-function-deprecate` | 标记弃用 → 推送新版本（含 RETIRING 指令） |
| `admin-workflow-publish` | 校验 DAG + Schema 兼容性 → 版本号 +1 → Publisher 批量推 |
| `admin-workflow-deprecate` | 下线版本 → 通知 Worker 进入 RETIRING |

- 初始化脚本：[admin-meta-workflows.sql](workflow-platform/docs/admin-meta-workflows.sql)
- `is_protected=1` 受保护：前端禁用删除、编辑二次确认、自动备份
- 前端利用元工作流的 `inputSchema/outputSchema` 动态渲染表单（见 [admin-frontend-design.md §9](workflow-admin-ui/docs/admin-frontend-design.md)）

---

## 10. 函数发布机制

```
Admin UI → Admin API → WfFunctionService / WfSchemaService / WfWorkflowService
        → ConfigPublisher (workflow./function./schema. 键 + __index__ 聚合索引)
        → 配置中心（Apollo namespace / Nacos Group=WORKFLOW / HTTP Registry）
        → Worker ConfigSubscriber（启动拉全量 + 推送监听）
        → FunctionConfigApplier / SchemaValidator
        → FunctionRegistry / SchemaManager（热加载 + 版本 ACTIVE/RETIRING 共存）
        → inFlight 计数 → 零时 purgeRetiring()（零停机）
```

- **函数类型差异**：`BUILTIN`/`CUSTOM`（代码随 Jar，无需发布） vs `SCRIPT`（Groovy，需发布） vs `EXTERNAL`（类名/端点，需发布）
- **灰度 & 回滚**：`PublishTarget` 支持 `ALL` / `APP_GROUP` / `INSTANCES`；回滚只需再发一次旧版本配置
- **Worker 启动顺序自检**：Apollo → Nacos → HTTP Registry 兜底；HTTP 用 JDK `java.net.http.HttpClient` 保持启动 classpath 最小

详细指南：[function-publish-guide.md](workflow-platform/docs/function-publish-guide.md)

---

## 11. Schema 兼容性策略

**只增不删 · 单份 Schema · 无多版本文件 · 运行逻辑兼容老客户端**（完整规则见 [schema-compatibility-guide.md](doc/schema-compatibility-guide.md)）

| 层 | 规则 |
|----|------|
| **后端入参** | 新字段全 optional；废弃字段 `@JsonIgnoreProperties(ignoreUnknown=true)` 不报错；枚举值兼容旧值；校验范围只松不紧；接口路由/状态码不可变 |
| **后端出参** | 旧字段永久返回；新增字段允许 `null`；类型转换失败仅 `log.warn { ... }` 不中断 |
| **前端** | 所有字段 `?.` 可选链 + 空值兜底；未知枚举展示「未知」不白屏；废弃字段保留解析、UI 逐步隐藏 |
| **数据库** | Flyway 只增字段、不删不改旧字段；废弃字段不回写；变更通过 `wf_schema_change_log` 追踪 |

前端 AJV 校验支持自定义 `schema:name` 格式引用（跨 Schema 引用）：由 `createAjvWithSchemaRefPlaceholders` 注入占位 schema 解决默认 AJV 无法解析的问题。

---

## 12. 前端 SDK（类型安全）

前端**禁止手写**原始 HTTP 调用 — 全部走由 OpenAPI 契约生成的类型安全 SDK：

```bash
cd workflow-admin-ui
pnpm openapi   # 根据 ../doc/openapi.yaml 生成 src/types/api.generated.d.ts
```

### 12.1 架构

```
OpenAPI 契约 (doc/openapi.yaml)
    ├── openapi-typescript ──→ src/types/api.generated.d.ts  (1000+ types)
    └── openapi-fetch        ──→ src/sdk/client.ts  (createClient<paths>)
                                    │
                                    ▼
                              umiFetch 桥接（注入 Umi request）
                                    │  ├── 复用代理 /api → http://localhost:7070
                                    │  ├── 复用鉴权 header / 全局错误 toast
                                    │  └── x-skip-global-error 控制 silent 模式
                                    ▼
                         src/sdk/index.ts 导出
                         ├── client  (paths 下所有 GET/POST/PUT/DELETE/OPTIONS)
                         ├── unwrap(result)  (error 抛 BizError 保持旧 apiGet 语义)
                         ├── silentHeaders(options?: RequestOptions)
                         └── 类型再导出（paths/components/{schemas,parameters}/...)
                                    │
                                    ▼
                      src/services/*.ts（workflow/function/schema/audit/...）
                      薄封装 → 业务组件
```

### 12.2 典型用法

```typescript
import { client, unwrap, silentHeaders } from '@/sdk';
import type { API } from '@/types/api';

// 分页查询工作流
export async function listWorkflows(params: API.PageParams, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/workflows', {
      params: { query: params },
      headers: silentHeaders(options),
    }),
  ) as unknown as API.PageResponse<WorkflowDefinition>;
}

// 删除函数（路径参数自动 URL 编码）
export async function deleteFunction(functionName: string) {
  unwrap(
    await client.DELETE('/api/admin/functions/{functionName}', {
      params: { path: { functionName } },
    }),
  );
}
```

> 注意：生成类型 `PageResponseWorkflowDefinition` 与项目泛型 `PageResponse<T>` 语义相同但类型别名不同 — 使用 `as unknown as PageResponse<WorkflowDefinition>` 桥接即可。未在 OpenAPI 契约中的端点（如 publish/deprecate、sql-preview、marketplace）暂用 `apiGet/apiPost` 兜底，后续建议补齐 `openapi.yaml` 后迁回 SDK。

---

## 13. 快速开始

### 13.1 环境要求

| 依赖 | 版本 | 备注 |
|------|------|------|
| **JDK** | 21+（推荐 GraalVM 21.0.11+9.1） | fluxion-admin 启动 **JDK 8 不兼容**；Java 8 业务系统走 Runtime 远程接入 |
| Node.js | ≥ 18 | 前端构建 |
| pnpm / npm | pnpm ≥ 8 | 前端依赖管理（含 workspace） |
| MySQL | 8.0+ | InnoDB · utf8mb4；Flyway 自动建表 |
| Redis | 7.x | 限流 / 幂等 / 缓存 / 分布式锁；**local profile 排除 Redis 自动配置**（走 LocalRateLimitStore + Caffeine） |
| 配置中心 | 可选 | Apollo / Nacos；无则走 HTTP Registry（内嵌轻量实现） |

**Spring Profile 本地开发必用**：`local,nacos`（local 关 Redis 自动配置；nacos 选注册中心实现；本地连不上 Nacos 可改用 `local` 单独跑，走 HTTP 回退）。

### 13.2 后端构建与启动

```bash
cd workflow-platform

# 全模块构建（含 69 tasks，含 openapi/protobuf 代码生成）
./gradlew build
# 或 Windows：gradlew.bat build
# 预期：BUILD SUCCESSFUL in 15s ~ 40s（视缓存）

# 启动控制面（默认 8080；local,nacos profile）
./gradlew :fluxion-admin:bootRun \
  --args='--spring.profiles.active=local,nacos'

# 编译 & 启动 Worker（运行面，默认 8081 HTTP + 50051 gRPC）
./gradlew :fluxion-runtime:bootJar
java -jar fluxion-runtime/build/libs/fluxion-runtime-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=local,nacos \
  --workflow.instance.role=worker
```

> **首次启动**：Flyway 自动建表（`fluxion-admin/src/main/resources/db/migration/`）。如需启用元工作流自举，执行 [admin-meta-workflows.sql](workflow-platform/docs/admin-meta-workflows.sql)。

### 13.3 前端启动

```bash
cd workflow-admin-ui
pnpm install            # 首次：安装依赖（.npmrc 已配置国内源可按需）
pnpm openapi            # 根据 doc/openapi.yaml 生成 src/types/api.generated.d.ts
pnpm dev                # 开发服务器（默认 8000，/api 代理 http://localhost:7070）
pnpm build              # 生产构建 → dist/ （60+ chunks 输出）
```

代理配置：[config/proxy.ts](workflow-admin-ui/config/proxy.ts) 已将 `/api` → `http://localhost:7070`，与 `fluxion-admin` 端口对齐。

### 13.4 Docker Compose 一键拉起（开发环境）

```bash
cd workflow-platform/deploy
docker compose --profile dev up -d
# 开发 profile 容器：MySQL 8 :3306 · Redis :6379 · Admin :8080 · Worker :8081
# 可选 GraalVM Native Image（零 JRE 依赖二进制）：
docker compose --profile native up -d
```

Redis Lua 限流脚本预加载脚本提供 `load-rate-limit-script.sh`（Linux/Mac）与 `.py`（跨平台）。

---

## 14. 开发规范与测试

### 14.1 后端（Kotlin）

#### 日志规范（强制）
- 每个 `.kt` 文件首行域内声明：`private val log = LoggerFactory.getLogger(javaClass)`
- 日志**只能**用懒 lambda 形式：`log.info { "..." }` / `log.warn(e) { "..." }`
- **禁止** Lombok `@Slf4j`（Java 静态 logger 无法配合 Kotlin inline lambda）
- **禁止**字符串拼接作为日志参数（非懒加载开销）
- 参考规则：[.trae/rules/kotlin-logging.md](.trae/rules/kotlin-logging.md)

#### 模块依赖规范
- `<domain>:<backend>:spring-boot` 必须显式依赖 `<domain>:<backend>` + `<domain>:core`（不靠传递）
- 应用层（`fluxion-admin` / `fluxion-runtime`）只依赖 `:spring-boot` 自动装配 jar，**绝不**直接 import 后端实现类
- Gradle daemon 不支持路径中的 `~`，CI 与脚本必须用绝对路径

#### OpenAPI 生成规范
- **禁止修改** `build/generated/openapi/` 下生成代码
- 容错逻辑写在 `src/main/kotlin` 手写层（mapper/service/controller wrapper）
- Controller 时间字段统一 `Long`（epoch millis），禁止用 `OffsetDateTime` 回传

#### KDoc 规范
- KDoc 中**永不写** `/*` 两字符序列（Kotlin 词法器不识别嵌套注释，会报 `Unclosed comment`）
- 用 HTML `<code>` 标签或反引号 + 省略替换路径通配符

#### 测试
- 核心模块：`NodeInput` / `RuleEvaluator` / `SchemaValidator` / `DagTopology` / `RetryScheduler` 要求 Given-When-Then 单元测试
- `fluxion-test`（`testImplementation`）提供 `InMemoryConfigSubscriber` / `InMemoryInstanceRegistry` / `InMemoryRedisCommands` 等 fixture；**禁止** `jar`/`bootJar` 发布
- WebFlux 测试：需手动提供 `RouteConfigStore` bean，`fluxion-test:webflux` 含支持
- DAG 幂等测试：用 `IdempotencyStore` Redis 集成测试覆盖哈希碰撞

### 14.2 前端（TypeScript / React）

- **禁止 class component**：全量 Hooks 函数式组件
- **禁止 `any`**：必要时 `unknown` + 类型守卫（TS 严格模式）
- 状态分层原则：
  - 服务端数据 → `useRequest`（Umi）/ SWR
  - 交互状态（画布/调试/Schema）→ Zustand `useXXXStore`
  - 全局会话（用户/权限）→ Umi `initialState`
- Schema 表单：统一 `SchemaFormEditor` / `SchemaValidator`（内置 `SchemaFieldPreview` + `collectTopLevelFields`，支持顶层数组/对象/allOf/$ref + 循环引用保护）
- `designer.tsx` 提交：`handleSave` 在提交**即时**从 `inputSchemaRef`/`outputSchemaRef` 拉取 schema 名 → 调用 `getSchemas()` 解析对象 → 写入 payload（避免 Form↔store 中间态同步丢 null 问题）
- `flowConverter.ts` 序列化：payload 必须**无条件包含** `inputSchema`/`inputSchemaFormat`/`outputSchema`/`outputSchemaFormat`，空值设为 `null`，**不得省略字段**
- 前端 SDK：新端点优先在 `doc/openapi.yaml` 中声明后 `pnpm openapi` 走 SDK；legacy `apiGet`/`apiPost` 仅作 fallback

### 14.3 常见陷阱（Lessons Learned）

| # | 坑 | 解决方案 |
|---|----|---------|
| 1 | fluxion-core 中误用 SpEL 解析锁键 → 线程阻塞 | 用 AviatorScript（dev） + Groovy（核心） |
| 2 | Gradle 模块路径 vs dependencies 路径不一致 → 构建循环依赖 | settings.gradle.kts `include()` 路径与 `implementation project(":x:y")` 1:1 对齐 |
| 3 | WebFlux `mono { ... }` 忘 `.awaitSingle()` → `Mono<Mono<X>>` 编译错 | `kotlinx-coroutines-reactor.awaitSingle` 显式桥接（WebFluxWorkflowHandler 等） |
| 4 | Controller 返回 OffsetDateTime → 前端反序列化失败 | 统一 `Long` epoch millis，mapper 层 `atZone(UTC).toInstant().toEpochMilli()` 转换 |
| 5 | 前端 flowConverter 漏发 `inputSchema: null` → 后端字段被忽略 → 数据库存 null | payload 中 schema 字段**无条件包含**，空传 null |
| 6 | Ant Design Typography 中写 `<Code>` → 白屏（子组件不存在） | 用 `<Text code>...</Text>` |
| 7 | AJV 默认无法解析自定义 `schema:order.OrderInfo` 引用 → 校验 500 | `createAjvWithSchemaRefPlaceholders` 注入占位 |
| 8 | `log.warn(e) { ... }` 写成 `log.warn("..." + e)` → 非懒加载 + Kotlin 类型错 | 统一 `log.xxx(throwable) { "..." }` 重载形式 |

---

## 15. 文档导航

| 领域 | 文档 | 关键内容 |
|------|------|---------|
| **架构总览** | [PROJECT_ARCHITECTURE.md](workflow-platform/PROJECT_ARCHITECTURE.md) | 69 模块逐模块职责、Gradle 构建约定、端到端请求流、扩展点、常见坑 |
| **核心设计** | [fluxion-design.md](workflow-platform/docs/fluxion-design.md) | 不可变执行状态、装饰器管线、DAG/Saga/Linear 引擎、长连接附录 |
| **架构 SVG** | [module-dependency.svg](doc/diagrams/module-dependency.svg) · [process-flow.svg](doc/diagrams/process-flow.svg) · [admin-backend.svg](doc/diagrams/admin-backend.svg) · [architecture-layers.svg](doc/diagrams/architecture-layers.svg) | 6 层依赖、3 泳道流程、Admin 内部结构、整体分层（GitHub 可直接渲染） |
| **OpenAPI 契约** | [doc/openapi.yaml](doc/openapi.yaml) | 全局 REST API 契约，后端生成 Kotlin 接口 + 前端生成 `api.generated.d.ts` |
| **前端架构** | [admin-frontend-design.md](workflow-admin-ui/docs/admin-frontend-design.md) | Umi 路由/权限、调试面板、Schema 动态表单、元工作流表单自举 |
| **前端 SDK 入口** | [sdk/client.ts](workflow-admin-ui/src/sdk/client.ts) · [sdk/index.ts](workflow-admin-ui/src/sdk/index.ts) | `openapi-fetch` 客户端 + Umi request 桥接 + unwrap/silentHeaders |
| **函数发布** | [function-publish-guide.md](workflow-platform/docs/function-publish-guide.md) | 配置中心链路、ACTIVE/RETIRING 双版本、灰度与回滚 |
| **Schema 兼容性** | [schema-compatibility-guide.md](doc/schema-compatibility-guide.md) | 前后端 + DB 的「只增不删」硬性规则 |
| **长连接规划** | [fluxion-long-connection-technical-design.md](workflow-platform/docs/fluxion-long-connection-technical-design.md) | WebSocket/TCP Actor 化状态机、断连续存 |
| **数据库** | [workflow-ddl.sql](workflow-platform/docs/workflow-ddl.sql) · `db/migration/` (Flyway) | DDL 脚本 + 迁移；**Hibernate ddl-auto=validate** |
| **元工作流** | [admin-meta-workflows.sql](workflow-platform/docs/admin-meta-workflows.sql) | 自举 SQL（发布/弃用函数 & 工作流） |
| **样例** | [sample-workflow-hello.json](workflow-platform/docs/sample-workflow-hello.json) · [sample-workflow-hello.sql](workflow-platform/docs/sample-workflow-hello.sql) | Hello World 导入即运行 |
| **部署** | [deploy/](workflow-platform/deploy) | Dockerfile · docker-compose.yaml · K8s manifests · ArgoCD · Redis Lua 预加载 |
| **CI** | `.github/workflows/fluxion-publish.yaml` · `.github/workflows/fluxion-deploy.yaml` | 发布 Maven 包 / 构建镜像 & 推 GitOps 仓库 |

---

> 本 README 综合自 `doc/`、`workflow-platform/docs`、`workflow-admin-ui/docs`、`PROJECT_ARCHITECTURE.md` 与实际代码结构（`settings.gradle.kts` 69 modules + 前端 `src/` 目录）逐项对齐；项目仍在演进，细节以对应文档与源码为准。
