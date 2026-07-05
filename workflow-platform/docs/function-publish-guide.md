# 函数发布到 Worker 使用指南

## 1. 概述

函数定义（尤其是 `SCRIPT` / `EXTERNAL` 类型）保存在 `wf_function` 表中。为了让 Worker 实例在运行时能够热加载这些函数，平台提供了从 Admin 到 Worker 的函数配置推送机制。

函数发布链路：

```text
Admin UI → Admin API → WfFunctionService → FunctionConfigPublisher → 配置中心 → FunctionConfigSubscriber → FunctionConfigApplier → FunctionRegistry
```

支持三种配置中心：

| 类型 | 配置项 | 适用场景 |
|------|--------|----------|
| HTTP | `workflow.config.type=http` | 默认，Admin 与 Worker 通过 HTTP 推拉 |
| Apollo | `workflow.config.type=apollo` |  Apollo 配置中心 |
| Nacos | `workflow.config.type=nacos` |  Nacos 配置中心 |

## 2. 支持的函数类型

| 类型 | 存储位置 | 是否需要发布 | 说明 |
|------|----------|--------------|------|
| `BUILTIN` | 代码 / Jar | 否 | 随应用启动自动注册 |
| `CUSTOM` | 代码 / Jar | 否 | 随应用启动自动注册 |
| `SCRIPT` | `wf_function.script_body` | 是 | Groovy / JS 脚本，发布到 Worker 后包装为 `ScriptWorkflowFunction` |
| `EXTERNAL` | `wf_function.class_name` | 是 | 外部类名引用，发布到 Worker 后包装为 `ExternalWorkflowFunction` |

## 3. FunctionConfigSnapshot 字段说明

函数快照是 Admin 到 Worker 的传输值对象，JSON 序列化后存储在配置中心。

| 字段 | 类型 | 说明 |
|------|------|------|
| `functionName` | String | 函数名（如 `myGroovyFunction`） |
| `functionType` | String | `SCRIPT_GROOVY` / `SCRIPT_JS` / `EXTERNAL` |
| `version` | Long | **函数版本号，单调递增；0 表示未显式指定版本（向后兼容）** |
| `scriptBody` | String? | 脚本内容（`SCRIPT` 类型必填） |
| `className` | String? | 外部类名（已废弃，保留兼容） |
| `endpoint` | String? | 外部服务端点（`EXTERNAL` 类型） |
| `paramSchema` | String? | 入参 JSON Schema |
| `outputSchema` | String? | 出参 JSON Schema |
| `description` | String? | 函数说明 |
| `enabled` | Boolean | 是否启用 |
| `targetGroups` | List<String>? | 目标分组，为空表示全局生效 |

示例：

```json
{
  "functionName": "myGroovyFunction",
  "functionType": "SCRIPT_GROOVY",
  "version": 3,
  "scriptBody": "return [status: 'OK']",
  "paramSchema": null,
  "outputSchema": null,
  "enabled": true,
  "targetGroups": ["group-a"]
}
```

> **version 使用建议**：每次发布函数更新时，Admin 应将 `version` 单调递增（如 +1）。Worker 收到后按版本注册，旧版本进入 `RETIRING`，保证在途调用不中断。

## 4. 应用角色配置

同一个 `workflow-admin` Jar 包通过 `workflow.instance.role` 区分角色：

### 4.1 Admin 角色

Admin 负责管理函数元数据，并在保存 / 发布 / 下线 / 删除时调用 `FunctionConfigPublisher`。

```yaml
workflow:
  config:
    type: http          # http | apollo | nacos
  instance:
    role: admin
```

### 4.2 Worker 角色

Worker 启动时从配置中心拉取已启用函数，注册到本地 `FunctionRegistry`；运行期间监听配置变更实现热更新。

```yaml
workflow:
  config:
    type: http
  instance:
    role: worker
    app-group: group-a  # 可选，按分组过滤函数
```

## 5. 三种配置中心配置

### 5.1 HTTP 配置中心

Admin 侧自动维护一个内存实例注册表，Worker 启动时向 Admin 注册并拉取全量函数配置；Admin 在函数变更时主动推送到所有已注册 Worker。

**Admin 配置：**

```yaml
workflow:
  config:
    type: http
  instance:
    role: admin
```

**Worker 配置：**

```yaml
workflow:
  config:
    type: http
    http:
      admin-url: http://localhost:8080
  instance:
    role: worker
    app-group: group-a  # 可选
```

### 5.2 Apollo 配置中心

函数配置存储在 Apollo 私有 Namespace `workflow-functions` 中，每个函数一条配置，Key 为函数名，Value 为 JSON 化的 `FunctionConfigSnapshot`。

**Admin / Worker 共同配置：**

```yaml
workflow:
  config:
    type: apollo
    apollo:
      portal-url: http://localhost:8070
      token: xxx
      app-id: workflow-platform
      env: DEV
      cluster: default
      operator: workflow-admin
```

使用前需确保 Apollo 中已创建 Namespace `workflow-functions`。

### 5.3 Nacos 配置中心

函数配置以 Nacos Config 形式存储：

- Group: `WORKFLOW`
- DataId 前缀: `workflow.function.`
- 索引 DataId: `workflow.function.__index__`（JSON 数组，记录所有函数名）

**Admin / Worker 共同配置：**

```yaml
workflow:
  config:
    type: nacos
    nacos:
      server-addr: localhost:8848
      namespace: workflow
      username: nacos
      password: nacos
```

## 6. 前端操作

进入 **函数管理** 页面：

1. 新建 / 编辑函数：填写函数名、类型（`SCRIPT` / `EXTERNAL`）、脚本内容或类名、Schema 等信息。
2. 点击 **保存**：如果函数状态为启用，会自动推送到 Worker。
3. 点击 **发布**：将函数状态置为启用并推送到 Worker。
4. 点击 **下线**：将函数状态置为禁用，并从 Worker 移除。
5. 点击 **删除**：从数据库删除，并从 Worker 移除。

## 7. Worker 侧热加载行为

Worker 启动时：

1. `FunctionConfigApplier` 调用 `subscriber.loadAll()` 拉取全量已启用函数。
2. 根据 `functionType` 创建包装器：
   - `GROOVY` / `JS` → `ScriptWorkflowFunction`
   - `EXTERNAL` → `ExternalWorkflowFunction`
3. 按 `script:<functionName>` 或 `external:<functionName>` 前缀，以 `registry.register(name, version, meta, function)` 形式注册到 `FunctionRegistry`。
   - `version` 来自 `FunctionConfigSnapshot.version`，单调递增。
   - 新版本注册后，旧版本进入 `RETIRING` 状态，仍继续服务在途调用；无在途调用后可被 `purgeRetiring()` 清理。
4. 如果配置了 `workflow.instance.app-group`，只加载 `targetGroups` 包含该分组或为空的函数。

> 多版本共存（最简模型）：每个函数最多同时保留 `ACTIVE + RETIRING` 两个版本。旧版本在途调用数为 0 时即可安全清理，避免新执行引用旧实现。

运行时变更：

- HTTP：Admin 通过 `/internal/functions/push` 主动推送到 Worker。
- Apollo / Nacos：Worker 监听配置变化，自动触发注册或注销。

## 8. 脚本函数引用

工作流节点中引用脚本函数时，可使用以下形式：

```json
{
  "functionRef": "script:myGroovyFunction"
}
```

或直接在通用脚本引擎节点中使用 `scriptRef`：

```json
{
  "functionRef": "builtin:groovyScript",
  "params": {
    "scriptRef": "myGroovyFunction"
  }
}
```

`scriptRef` 会通过 `FunctionConfigSubscriber` 实时解析为对应函数的 `scriptBody`。

## 9. 注意事项

1. **配置中心唯一性**：同一环境建议只启用一种 `workflow.config.type`，避免多个 `FunctionConfigPublisher` 同时生效。
2. **Apollo Namespace**：需提前在 Apollo 门户创建 `workflow-functions` Namespace。
3. **Nacos 索引**：发布 / 下线函数时会同步更新 `workflow.function.__index__`，不要手动修改该索引。
4. **Worker 分组过滤**：`app-group` 只影响 Worker 加载哪些函数，Admin 侧推送时仍会把 `targetGroups` 信息一起下发。
5. **下线与删除**：下线会发送 `enabled=false` 的快照或调用 `unpublish`，Worker 会注销对应函数；删除也会触发 `unpublish`。
