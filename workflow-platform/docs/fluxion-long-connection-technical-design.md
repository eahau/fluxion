# Fluxion 长连接支持技术方案

## 版本信息

| 项目 | 内容 |
|------|------|
| 文档版本 | v1.0 |
| 适用范围 | fluxion-platform |
| 目标读者 | 后端架构师、核心开发工程师 |
| 关联模块 | fluxion-adapter-spi、fluxion-adapter-http、fluxion-core、fluxion-runtime |

---

## 1. 背景与目标

### 1.1 背景

当前 fluxion 已支持 HTTP、Dubbo、gRPC、Kafka 等多种协议接入，业务逻辑通过工作流编排替代传统 Controller/Service 模式。然而随着实时通信、IoT 设备接入、在线协作等场景需求增长，短连接式的请求-响应模型已无法满足以下诉求：

- 服务端主动向客户端推送消息。
- 同一连接多次交互共享业务上下文。
- 连接断开后可恢复会话状态。
- 跨节点部署时长连接会话可路由。

### 1.2 目标

在现有 fluxion 架构基础上，新增 WebSocket 与 TCP Socket 长连接协议支持，实现：

1. **协议统一**：长连接请求统一转换为 `UnifiedRequest`，复用现有 `WorkflowRouter` 与 DAG 执行引擎。
2. **会话保持**：一次长连接对应一个工作流实例（executionId），连接生命周期事件驱动工作流状态流转。
3. **双向通信**：工作流节点可通过输出通道向客户端主动推送消息。
4. **状态续存**：连接断开后重连，可基于 `executionId` 恢复会话状态。
5. **水平扩展**：跨节点部署时，executionId 可按现有配置中心/注册中心路由到正确节点。

---

## 2. 总体架构

### 2.1 架构分层

```
┌─────────────────────────────────────────────────────────────────────┐
│                        客户端（Browser / App / IoT）                  │
└───────────────────┬─────────────────────────────────────────────────┘
                    │ WebSocket / TCP
┌───────────────────▼─────────────────────────────────────────────────┐
│                     协议适配器层（Adapter Layer）                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ HTTP Adapter │  │ WebSocket    │  │ TCP Socket Adapter       │  │
│  │ (existing)   │  │ Adapter      │  │ (Netty)                  │  │
│  └──────────────┘  └──────┬───────┘  └─────────────┬────────────┘  │
│                           │                        │               │
│                           └────────┬───────────────┘               │
│                                    ▼                                │
│                         UnifiedRequest / UnifiedResponse            │
│                         WorkflowEvent (long-lived)                  │
└────────────────────────────────────┬────────────────────────────────┘
                                     │
┌────────────────────────────────────▼────────────────────────────────┐
│                        实例管理层（Instance Layer）                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │
│  │ SessionManager  │  │ ActorManager    │  │ ConnectionBridge    │  │
│  │ (session映射)    │  │ (actor 生命周期) │  │ (输出 → 客户端)      │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────┘
                                     │
┌────────────────────────────────────▼────────────────────────────────┐
│                        执行引擎层（Engine Layer）                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │
│  │ WorkflowInstance │  │ SignalBroker    │  │ ExecutionSnapshot   │  │
│  │ Actor           │  │ (existing)      │  │ Store (existing)    │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 设计原则

1. **零侵入现有核心**：`fluxion-core` 保持纯 Kotlin 零框架依赖，长连接能力通过新增 adapter 模块提供。
2. **复用现有抽象**：复用 `UnifiedRequest`/`UnifiedResponse`、`WorkflowRouter`、`SignalBroker`、`ExecutionSnapshotStore`。
3. **Actor 化实例**：每个长连接工作流实例对应一个 `WorkflowInstanceActor`，事件驱动状态推进。
4. **配置中心路由**：分布式路由优先复用现有 Nacos/Apollo/注册中心，不引入重型 actor 框架。

---

## 3. 协议适配层设计

### 3.1 Protocol 枚举扩展

在 `fluxion-core/src/main/kotlin/com/fluxion/core/enums/Protocol.kt` 中新增协议类型：

```kotlin
package com.fluxion.core.enums

enum class Protocol {
    HTTP,
    HTTPS,
    DUBBO,
    GRPC,
    KAFKA,
    WEBSOCKET,
    TCP,
    INTERNAL
}
```

### 3.2 新增模块规划

```
fluxion-adapter-longconnection/
├── core/                          # 协议无关的公共抽象
│   └── src/main/kotlin/com/fluxion/adapter/longconnection/core/
├── websocket-netty/               # 基于 Netty 的 WebSocket 服务器（零 Spring）
│   └── src/main/kotlin/com/fluxion/adapter/websocket/netty/
├── websocket-spring-boot/         # Spring WebFlux / JSR-356 集成
│   └── src/main/kotlin/com/fluxion/adapter/websocket/spring/boot/
├── tcp-netty/                     # 基于 Netty 的 TCP Socket 服务器
│   └── src/main/kotlin/com/fluxion/adapter/tcp/netty/
└── build.gradle.kts
```

### 3.3 长连接路由定义

```kotlin
package com.fluxion.adapter.longconnection.core

import com.fluxion.core.enums.Protocol

data class LongConnectionRouteDefinition(
    val routeKey: String,
    val protocol: Protocol,
    val bindKey: String,
    val workflowId: String?,
    val scope: String = "PRIVATE",
    val enabled: Boolean = true,
    val messageTrigger: MessageTrigger = MessageTrigger.FIRST_MESSAGE
)

enum class MessageTrigger {
    FIRST_MESSAGE,   // 第一条消息触发工作流
    EVERY_MESSAGE    // 每条消息都作为事件注入工作流
}
```

### 3.4 连接生命周期事件

```kotlin
package com.fluxion.adapter.longconnection.core

sealed class ConnectionEvent {
    abstract val sessionId: String
    abstract val executionId: String?

    data class Opened(
        override val sessionId: String,
        override val executionId: String?,
        val pathVariables: Map<String, String>,
        val headers: Map<String, String>,
        val queryParams: Map<String, Any>
    ) : ConnectionEvent()

    data class MessageReceived(
        override val sessionId: String,
        override val executionId: String?,
        val message: String,
        val metadata: Map<String, String> = emptyMap()
    ) : ConnectionEvent()

    data class Closed(
        override val sessionId: String,
        override val executionId: String?
    ) : ConnectionEvent()

    data class Error(
        override val sessionId: String,
        override val executionId: String?,
        val error: Throwable
    ) : ConnectionEvent()
}
```

### 3.5 WebSocket 适配器实现

#### 3.5.1 Netty 零 Spring 实现

```kotlin
package com.fluxion.adapter.websocket.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler

class WebSocketWorkflowServer(
    private val connectionHandler: LongConnectionHandler,
    private val port: Int
) {
    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup()

    fun start() {
        ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(
                        HttpServerCodec(),
                        HttpObjectAggregator(65536),
                        WebSocketServerProtocolHandler("/ws"),
                        WebSocketWorkflowChannelHandler(connectionHandler)
                    )
                }
            })
            .bind(port)
            .sync()
    }

    fun stop() {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }
}

class WebSocketWorkflowChannelHandler(
    private val connectionHandler: LongConnectionHandler
) : SimpleChannelInboundHandler<TextWebSocketFrame>() {

    private var sessionId: String? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        sessionId = generateSessionId()
        val session = NettyWebSocketSession(sessionId!!, ctx.channel())
        connectionHandler.onOpen(session)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, frame: TextWebSocketFrame) {
        sessionId?.let { connectionHandler.onMessage(it, frame.text()) }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        sessionId?.let { connectionHandler.onClose(it) }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        sessionId?.let { connectionHandler.onError(it, cause) }
    }

    private fun generateSessionId(): String =
        "ws-${UUID.randomUUID().toString().replace("-", "")}"
}
```

#### 3.5.2 Spring Boot 自动装配

```kotlin
package com.fluxion.adapter.websocket.spring.boot

import com.fluxion.adapter.longconnection.core.ActorManager
import com.fluxion.adapter.longconnection.core.SessionManager
import com.fluxion.adapter.spi.WorkflowRouter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import javax.websocket.server.ServerEndpoint

@AutoConfiguration
@ConditionalOnClass(ServerEndpoint::class)
@ConditionalOnProperty(prefix = "workflow.adapter.websocket", name = ["enabled"], havingValue = "true")
class WebSocketAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun webSocketConnectionHandler(
        workflowRouter: WorkflowRouter,
        sessionManager: SessionManager<WebSocketSession>,
        actorManager: ActorManager
    ): WebSocketConnectionHandler = WebSocketConnectionHandler(
        workflowRouter, sessionManager, actorManager
    )

    @Bean
    @ConditionalOnMissingBean
    fun webSocketWorkflowEndpoint(
        handler: WebSocketConnectionHandler
    ): WebSocketWorkflowEndpoint = WebSocketWorkflowEndpoint(handler)

    @Bean
    @ConditionalOnMissingBean
    fun webSocketSessionManager(): SessionManager<WebSocketSession> =
        InMemorySessionManager()
}
```

### 3.6 TCP Socket 适配器实现

```kotlin
package com.fluxion.adapter.tcp.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.string.StringDecoder

class TcpWorkflowServer(
    private val connectionHandler: LongConnectionHandler,
    private val port: Int
) {
    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup()

    fun start() {
        ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(
                        DelimiterBasedFrameDecoder(8192, *Delimiters.lineDelimiter()),
                        StringDecoder(),
                        TcpWorkflowChannelHandler(connectionHandler)
                    )
                }
            })
            .bind(port)
            .sync()
    }

    fun stop() {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }
}
```

---

## 4. 连接事件处理层

### 4.1 LongConnectionHandler

```kotlin
package com.fluxion.adapter.longconnection.core

import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.enums.Protocol

class LongConnectionHandler(
    private val workflowRouter: WorkflowRouter,
    private val sessionManager: SessionManager<Any>,
    private val actorManager: ActorManager,
    private val routeResolver: LongConnectionRouteResolver
) {

    fun onOpen(session: LongConnectionSession) {
        val route = routeResolver.resolve(session.path, session.protocol)
            ?: throw LongConnectionException("No route found for ${session.protocol}:${session.path}")

        val executionId = generateExecutionId()
        sessionManager.bind(session, executionId)

        // 创建 actor 并启动
        val actor = actorManager.create(executionId, route.workflowId)

        // 发送 OPEN 事件
        actor.send(WorkflowEvent.ConnectionOpened(
            executionId = executionId,
            sessionId = session.id,
            pathVariables = session.pathVariables,
            headers = session.headers,
            queryParams = session.queryParams
        ))
    }

    fun onMessage(sessionId: String, message: String) {
        val executionId = sessionManager.getExecutionId(sessionId)
            ?: throw LongConnectionException("Session not bound: $sessionId")

        actorManager.get(executionId)?.send(WorkflowEvent.ConnectionMessageReceived(
            executionId = executionId,
            sessionId = sessionId,
            message = message
        ))
    }

    fun onClose(sessionId: String) {
        val executionId = sessionManager.getExecutionId(sessionId) ?: return
        actorManager.get(executionId)?.send(WorkflowEvent.ConnectionClosed(
            executionId = executionId,
            sessionId = sessionId
        ))
        sessionManager.unbindBySessionId(sessionId)
    }

    fun onError(sessionId: String, error: Throwable) {
        val executionId = sessionManager.getExecutionId(sessionId) ?: return
        actorManager.get(executionId)?.send(WorkflowEvent.ConnectionError(
            executionId = executionId,
            sessionId = sessionId,
            error = error
        ))
    }
}
```

### 4.2 重连恢复流程

```kotlin
fun onReconnect(session: LongConnectionSession, executionId: String) {
    // 1. 绑定新 session 到原 executionId
    sessionManager.bind(session, executionId)

    // 2. 获取或恢复 actor
    val actor = actorManager.getOrCreate(executionId, workflowId = null)

    // 3. 发送重连事件
    actor.send(WorkflowEvent.ConnectionReconnected(
        executionId = executionId,
        sessionId = session.id,
        headers = session.headers
    ))
}
```

---

## 5. Actor 化工作流实例

### 5.1 WorkflowEvent 事件模型

```kotlin
package com.fluxion.adapter.longconnection.core

sealed class WorkflowEvent {
    abstract val executionId: String

    data class Start(
        override val executionId: String,
        val workflowId: String,
        val input: Map<String, Any>
    ) : WorkflowEvent()

    data class ConnectionOpened(
        override val executionId: String,
        val sessionId: String,
        val pathVariables: Map<String, String>,
        val headers: Map<String, String>,
        val queryParams: Map<String, Any>
    ) : WorkflowEvent()

    data class ConnectionMessageReceived(
        override val executionId: String,
        val sessionId: String,
        val message: String
    ) : WorkflowEvent()

    data class ConnectionClosed(
        override val executionId: String,
        val sessionId: String
    ) : WorkflowEvent()

    data class ConnectionReconnected(
        override val executionId: String,
        val sessionId: String,
        val headers: Map<String, String>
    ) : WorkflowEvent()

    data class ConnectionError(
        override val executionId: String,
        val sessionId: String,
        val error: Throwable
    ) : WorkflowEvent()

    data class NodeCompleted(
        override val executionId: String,
        val nodeId: String,
        val record: NodeExecutionRecord
    ) : WorkflowEvent()

    data class NodeFailed(
        override val executionId: String,
        val nodeId: String,
        val error: Throwable
    ) : WorkflowEvent()

    data class SignalReceived(
        override val executionId: String,
        val signal: Signal
    ) : WorkflowEvent()

    data class Timeout(
        override val executionId: String,
        val nodeId: String
    ) : WorkflowEvent()
}
```

### 5.2 WorkflowInstanceActor

```kotlin
package com.fluxion.adapter.longconnection.core

import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.signal.SignalBroker
import com.fluxion.runtime.core.provider.DefinitionProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class WorkflowInstanceActor(
    val executionId: String,
    private val workflowId: String,
    private val definitionProvider: DefinitionProvider,
    private val workflowEngine: WorkflowEngine,
    private val functionDispatcher: CoroutineDispatcher,
    private val connectionBridge: ConnectionBridge,
    private val signalBroker: SignalBroker,
    private val snapshotStore: ExecutionSnapshotStore?
) {
    private val mailbox = Channel<WorkflowEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var state: ImmutableExecutionState? = null
    private var definition: WorkflowDefinition? = null
    private var pendingNodes = mutableMapOf<String, Job>()
    private var isClosed = false

    fun start() {
        scope.launch {
            for (event in mailbox) {
                if (isClosed) break
                handleEvent(event)
            }
        }
    }

    suspend fun send(event: WorkflowEvent) {
        mailbox.send(event)
    }

    private suspend fun handleEvent(event: WorkflowEvent) {
        when (event) {
            is WorkflowEvent.Start -> handleStart(event)
            is WorkflowEvent.ConnectionOpened -> handleConnectionOpened(event)
            is WorkflowEvent.ConnectionMessageReceived -> handleMessage(event)
            is WorkflowEvent.ConnectionClosed -> handleConnectionClosed(event)
            is WorkflowEvent.ConnectionReconnected -> handleReconnected(event)
            is WorkflowEvent.NodeCompleted -> handleNodeCompleted(event)
            is WorkflowEvent.NodeFailed -> handleNodeFailed(event)
            is WorkflowEvent.SignalReceived -> handleSignalReceived(event)
            is WorkflowEvent.Timeout -> handleTimeout(event)
            else -> {}
        }

        // 每次事件处理后尝试快照
        if (shouldSnapshot(event)) {
            snapshotStore?.save(state!!, workflowId, definition?.version ?: 0)
        }
    }

    private suspend fun handleStart(event: WorkflowEvent.Start) {
        definition = definitionProvider.get(event.workflowId)
            ?: throw WorkflowNotFoundException(event.workflowId)
        state = ImmutableExecutionState.start(definition!!, event.input)
        advance()
    }

    private suspend fun handleMessage(event: WorkflowEvent.ConnectionMessageReceived) {
        // 将消息作为输入注入工作流
        // 具体行为由工作流定义决定，可触发特定节点或更新状态
        val currentState = state ?: return
        state = currentState.withInput(currentState.inputs + mapOf("_lastMessage" to event.message))
        advance()
    }

    private suspend fun handleNodeCompleted(event: WorkflowEvent.NodeCompleted) {
        pendingNodes.remove(event.nodeId)
        state = state?.withNodeOutput(event.nodeId, event.record.output)

        // 如果节点输出包含 _pushMessage，则推送给客户端
        val pushMessage = extractPushMessage(event.record.output)
        if (pushMessage != null) {
            connectionBridge.send(executionId, pushMessage)
        }

        advance()
    }

    private suspend fun handleConnectionClosed(event: WorkflowEvent.ConnectionClosed) {
        // 根据工作流定义决定是否结束 actor
        // 若工作流配置为 close 即结束，则 isClosed = true
        // 否则保持 actor 等待重连或信号
    }

    private fun advance() {
        val currentState = state ?: return
        val currentDef = definition ?: return

        // 计算就绪节点并派发执行
        val readyNodes = calculateReadyNodes(currentDef, currentState)
        readyNodes.forEach { node ->
            if (pendingNodes.containsKey(node.id)) return@forEach
            pendingNodes[node.id] = scope.launch(functionDispatcher) {
                try {
                    val record = workflowEngine.executeNode(node, null, currentState)
                    send(WorkflowEvent.NodeCompleted(executionId, node.id, record))
                } catch (ex: Exception) {
                    send(WorkflowEvent.NodeFailed(executionId, node.id, ex))
                }
            }
        }
    }

    fun stop() {
        isClosed = true
        pendingNodes.values.forEach { it.cancel() }
        scope.cancel()
    }
}
```

---

## 6. 会话管理与会话保持

### 6.1 SessionManager SPI

```kotlin
package com.fluxion.adapter.longconnection.core

interface SessionManager<S> {
    fun bind(session: S, executionId: String)
    fun unbind(session: S)
    fun unbindBySessionId(sessionId: String)
    fun getExecutionId(sessionId: String): String?
    fun getSession(executionId: String): S?
    fun send(executionId: String, message: String): Boolean
    fun isOnline(executionId: String): Boolean
    fun listOnlineSessions(): Collection<String>
}
```

### 6.2 单节点内存实现

```kotlin
package com.fluxion.adapter.longconnection.core

class InMemorySessionManager<S> : SessionManager<S> {
    private val sessionToExec = ConcurrentHashMap<String, String>()
    private val execToSession = ConcurrentHashMap<String, S>()

    override fun bind(session: S, executionId: String) {
        val sessionId = extractSessionId(session)
        sessionToExec[sessionId] = executionId
        execToSession[executionId] = session
    }

    override fun unbind(session: S) {
        val sessionId = extractSessionId(session)
        val executionId = sessionToExec.remove(sessionId)
        executionId?.let { execToSession.remove(it) }
    }

    override fun unbindBySessionId(sessionId: String) {
        val executionId = sessionToExec.remove(sessionId)
        executionId?.let { execToSession.remove(it) }
    }

    override fun getExecutionId(sessionId: String): String? = sessionToExec[sessionId]
    override fun getSession(executionId: String): S? = execToSession[executionId]

    override fun send(executionId: String, message: String): Boolean {
        val session = execToSession[executionId] ?: return false
        return doSend(session, message)
    }

    override fun isOnline(executionId: String): Boolean = execToSession.containsKey(executionId)
    override fun listOnlineSessions(): Collection<String> = execToSession.keys

    private fun extractSessionId(session: S): String {
        return when (session) {
            is LongConnectionSession -> session.id
            else -> throw IllegalArgumentException("Unsupported session type")
        }
    }
}
```

### 6.3 分布式 Redis 实现

```kotlin
package com.fluxion.adapter.longconnection.redis

class RedisSessionManager<S>(
    private val redis: RedisTemplate<String, String>,
    private val localRegistry: LocalSessionRegistry<S>,
    private val nodeId: String,
    private val clusterBroadcaster: ClusterMessageBroadcaster
) : SessionManager<S> {

    companion object {
        private const val EXEC_NODE_KEY = "fluxion:lc:exec:node"
        private const val EXEC_META_KEY = "fluxion:lc:exec:meta"
        private const val EXEC_SESSION_KEY = "fluxion:lc:exec:session"
    }

    override fun bind(session: S, executionId: String) {
        val sessionId = extractSessionId(session)
        localRegistry.bind(sessionId, session)

        redis.opsForHash<String, String>().put(EXEC_NODE_KEY, executionId, nodeId)
        redis.opsForHash<String, String>().put(EXEC_SESSION_KEY, executionId, sessionId)
    }

    override fun send(executionId: String, message: String): Boolean {
        // 先查本地
        if (localRegistry.send(executionId, message)) {
            return true
        }

        // 本地没有，查 Redis 获取目标节点
        val targetNode = redis.opsForHash<String, String>().get(EXEC_NODE_KEY, executionId)
            ?: return false

        // 广播到目标节点
        clusterBroadcaster.send(targetNode, ClusterMessage.Push(executionId, message))
        return true
    }

    override fun getExecutionId(sessionId: String): String? {
        return localRegistry.getExecutionId(sessionId)
            ?: redis.opsForHash<String, String>().entries(EXEC_SESSION_KEY)
                .entries.find { it.value == sessionId }?.key
    }
}
```

---

## 7. 状态管理与持久化

### 7.1 状态分层

| 层级 | 数据 | 存储 | 生命周期 |
|------|------|------|----------|
| 连接层 | sessionId ↔ executionId 映射 | 内存 / Redis | 连接期间 |
| 执行层 | ImmutableExecutionState | Actor 内存 | 工作流执行期间 |
| 持久层 | 快照 + 事件日志 | DB / Redis / 文件 | 长期 |

### 7.2 快照策略

```kotlin
package com.fluxion.adapter.longconnection.core

interface SnapshotStrategy {
    fun shouldSnapshot(event: WorkflowEvent): Boolean
}

class DefaultSnapshotStrategy : SnapshotStrategy {
    override fun shouldSnapshot(event: WorkflowEvent): Boolean {
        return when (event) {
            is WorkflowEvent.ConnectionClosed -> true
            is WorkflowEvent.ConnectionReconnected -> true
            is WorkflowEvent.NodeCompleted -> true
            is WorkflowEvent.SignalReceived -> true
            else -> false
        }
    }
}
```

### 7.3 事件日志

可选事件日志用于审计和精确重放：

```kotlin
interface WorkflowEventLog {
    fun append(executionId: String, event: WorkflowEvent)
    fun read(executionId: String): List<WorkflowEvent>
}
```

---

## 8. 分布式路由

### 8.1 ActorLocationResolver

```kotlin
package com.fluxion.adapter.longconnection.core

interface ActorLocationResolver {
    fun locate(executionId: String): NodeAddress?
    fun currentNode(): NodeAddress
}

class ConfigCenterActorLocationResolver(
    private val instanceRegistry: InstanceRegistry,
    private val currentNode: NodeAddress
) : ActorLocationResolver {

    override fun locate(executionId: String): NodeAddress? {
        val nodes = instanceRegistry.listHealthyNodes()
        if (nodes.isEmpty()) return null
        return consistentHash(executionId, nodes)
    }

    override fun currentNode(): NodeAddress = currentNode

    private fun consistentHash(key: String, nodes: List<NodeAddress>): NodeAddress {
        val sorted = nodes.sortedBy { hash(it.id + key) }
        return sorted.first()
    }
}
```

### 8.2 请求转发

当节点 A 收到属于节点 B 的 executionId 的消息时：

```kotlin
fun routeEvent(event: WorkflowEvent) {
    val targetNode = actorLocationResolver.locate(event.executionId)
    if (targetNode == actorLocationResolver.currentNode()) {
        actorManager.getOrCreate(event.executionId, null).send(event)
    } else {
        interNodeTransport.send(targetNode, event)
    }
}
```

### 8.3 故障转移

1. 节点 B 宕机，注册中心更新节点列表。
2. 节点 A 发现 `locate(executionId)` 结果变化。
3. 节点 A 接管 executionId，从快照恢复 actor。
4. 客户端重连时被路由到新节点。

---

## 9. 与现有 HTTP 适配器的集成

### 9.1 统一路由配置

HTTP 路由与长连接路由共用配置中心，但使用不同 dataId：

```yaml
# HTTP 路由
workflow.http.routes:
  routes:
    - routeKey: "POST:/api/user/login"
      path: "/api/user/login"
      method: "POST"
      workflowId: "user-login"

# WebSocket 路由
workflow.websocket.routes:
  routes:
    - routeKey: "WS:/chat/{roomId}"
      path: "/chat/{roomId}"
      protocol: "WEBSOCKET"
      workflowId: "chat-room"
```

### 9.2 统一入口处理

所有协议最终都通过 `WorkflowRouter` 进入引擎：

```kotlin
// HTTP
val httpResult = workflowRouter.executeSuspend(
    UnifiedRequest("HTTP", workflowId, headers, params)
)

// WebSocket
val wsResult = workflowRouter.executeSuspend(
    UnifiedRequest("WEBSOCKET", workflowId, headers, params)
)
```

### 9.3 自动装配隔离

长连接适配器通过 `@ConditionalOnClass` 和 `@ConditionalOnProperty` 按需启用，不影响现有 HTTP 适配器：

```kotlin
@AutoConfiguration
@ConditionalOnClass(ServerEndpoint::class)
@ConditionalOnProperty(prefix = "workflow.adapter.websocket", name = ["enabled"])
class WebSocketAdapterAutoConfiguration
```

---

## 10. 实施路线图

### Phase 1：本地 WebSocket 适配器（2 周）

- 新增 `fluxion-adapter-longconnection-core` 模块。
- 新增 `fluxion-adapter-websocket-netty` 模块。
- 实现 `LongConnectionHandler`、`InMemorySessionManager`、`WorkflowInstanceActor`。
- 实现单节点 WebSocket → 工作流 → 客户端推送闭环。
- 编写单元测试和集成测试。

### Phase 2：状态持久化与恢复（2 周）

- 接入 `ExecutionSnapshotStore`。
- 实现 `DefaultSnapshotStrategy`。
- 实现 actor 崩溃后从快照恢复。
- 支持连接断开/重连后的状态续存。

### Phase 3：TCP Socket 适配器（1 周）

- 新增 `fluxion-adapter-tcp-netty` 模块。
- 支持行分隔帧和长度字段帧。
- 复用 WebSocket 的 actor 和 session 管理逻辑。

### Phase 4：分布式路由与 Spring Boot 集成（2 周）

- 实现 `RedisSessionManager`。
- 实现 `ConfigCenterActorLocationResolver`。
- 新增 `fluxion-adapter-websocket-spring-boot` 模块。
- 实现节点间事件转发。
- 故障转移测试。

### Phase 5：生产化完善（2 周）

- 心跳与连接保活。
- 限流与背压。
- 监控指标（在线连接数、消息吞吐、actor 数量）。
- 文档与示例。

---

## 11. 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| Actor 化改动范围大 | 中 | Phase 1 先做单节点闭环，不改动现有 HTTP 路径 |
| 长连接占用资源多 | 中 | 引入心跳超时、冷 actor 卸载、连接数限流 |
| 分布式状态一致性问题 | 高 | 单 executionId 单 actor，通过一致性哈希保证 |
| 网络分区导致脑裂 | 中 | 配置中心感知节点变化，结合 fencing token |
| 函数阻塞拖垮 actor | 低 | 函数执行始终外派到 functionDispatcher，actor 不阻塞 |

---

## 12. 附录

### 12.1 关键接口清单

| 接口/类 | 位置 | 职责 |
|--------|------|------|
| `SessionManager` | `fluxion-adapter-longconnection-core` | 会话绑定与消息推送 |
| `ActorManager` | `fluxion-adapter-longconnection-core` | actor 生命周期管理 |
| `WorkflowInstanceActor` | `fluxion-adapter-longconnection-core` | 事件驱动状态机 |
| `ConnectionBridge` | `fluxion-adapter-longconnection-core` | 工作流向客户端输出 |
| `LongConnectionHandler` | `fluxion-adapter-longconnection-core` | 连接事件入口 |
| `ActorLocationResolver` | `fluxion-adapter-longconnection-core` | 分布式路由 |
| `WebSocketWorkflowServer` | `fluxion-adapter-websocket-netty` | Netty WebSocket 服务 |
| `TcpWorkflowServer` | `fluxion-adapter-tcp-netty` | Netty TCP 服务 |

### 12.2 配置示例

```yaml
workflow:
  adapter:
    websocket:
      enabled: true
      port: 8081
      path: /ws
      heartbeat-interval-sec: 30
      message-trigger: FIRST_MESSAGE
    tcp:
      enabled: true
      port: 9090
      frame-type: LINE_DELIMITED
  session:
    distributed: true
    redis:
      host: localhost
      port: 6379
  actor:
    snapshot-enabled: true
    snapshot-interval-ms: 5000
```
