# Schema 兼容性策略（Schema Compatibility Guide）

## 概述

本项目的 Schema 管理遵循 **单份 Schema、只增不删、无多版本文件、靠运行逻辑兼容老客户端** 的原则。  
所有变更通过修改记录（ChangeLog）追踪，运行时通过兼容逻辑保证新旧客户端/前端共存。

---

## 一、后端接口运行时兼容逻辑

### 1. 入参校验逻辑

| 规则 | 描述 | 实现方式 |
|------|------|----------|
| 新字段全可选 | 新增入参字段必须 `nullable = true`，不做非空校验 | Kotlin 类型签名使用 `?`，Bean Validation 仅用 `@Nullable` |
| 废弃字段忽略 | 已标记 `@Deprecated` 的字段：不拒绝、不抛异常、打印 WARN 日志 | `SchemaCompatibility.safeGetParam()` 自动转换 |
| 校验范围只松不紧 | 原有字段校验只能放宽（如 `age >= 0` 不能改成 `age >= 18`） | Code Review 约束 |
| 枚举兼容旧值 | 老枚举值永久保留；未知新值兜底为默认空/未知状态，不抛异常 | `SchemaCompatibility.safeEnumValue()` |

### 2. 出参序列化逻辑

| 规则 | 描述 | 实现方式 |
|------|------|----------|
| 历史字段永久返回 | 数据库/内存中存在的旧字段必须序列化输出，不能剔除 | DTO 中保留所有历史字段 |
| 新增字段允许 null | 新字段对老前端返回 null，不依赖默认值 | `@JsonInclude(Include.NON_NULL)` |
| 嵌套对象完整兼容 | 嵌套内部旧子字段完整输出；嵌套新增子字段返回 null | 保持嵌套结构稳定，不移动旧字段 |

### 3. 类型兼容兜底

| 规则 | 描述 | 实现方式 |
|------|------|----------|
| 数字 ↔ 字符串 | 安全转换，失败返回 null 不抛 500 | `SchemaCompatibility.safeToNumber()` / `safeToString()` |
| 布尔兼容 | 0/1、"true"/"false" 自动转 Boolean | `SchemaCompatibility.safeToBoolean()` |
| 兜底原则 | 转换失败仅日志告警，不中断接口 | 全局 `@ControllerAdvice` 不拦截此类错误 |

### 4. 接口基础信息

- 原有接口 Method、路由、HTTP 状态码不能变更
- 不能删除原有接口，只能新增接口
- 原有 Header/Query 参数只增不减，旧参数永久兼容

---

## 二、前端侧逻辑兼容

### 1. 请求发送

- 新增请求参数全部可选传递，不强制携带
- 废弃参数逐步移除页面赋值，但请求工具不删除字段定义
- 统一请求拦截器捕获后端废弃字段告警日志，上报埋点

### 2. 响应解析与渲染

- **所有字段读取使用可选链 / 空值兜底**：`res.data.user?.age`
- **禁止直接访问** `res.data.user.age` —— 新字段为 null 不会页面报错
- 废弃字段兼容展示：页面逐步隐藏废弃字段，但解析逻辑保留
- 枚举兼容：后端返回未知枚举值，统一展示「未知」，不白屏
- 类型容错：接口返回类型和本地 TS 类型不一致时，做安全转换

---

## 三、数据库层配套

| 规则 | 描述 |
|------|------|
| 只增字段 | 新增字段允许 NULL、设置默认值 |
| 不删旧字段 | 旧字段不删除、不修改类型、不改为非空 |
| 废弃字段不回写 | 废弃 DB 字段不再写入，但查询时正常 SELECT 返回 |

---

## 四、Modification History（修改记录）

由于 Schema 只有单份文件（无多版本），变更记录通过 `wf_schema_change_log` 表追踪。

```sql
CREATE TABLE wf_schema_change_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    schema_name VARCHAR(128)  NOT NULL,
    field_path  VARCHAR(256)  NOT NULL COMMENT '修改的字段路径，如 $.schemaJson.properties.age',
    change_type VARCHAR(32)   NOT NULL COMMENT 'ADD / MODIFY / DEPRECATE / REMOVE',
    old_value   TEXT          NULL,
    new_value   TEXT          NULL,
    operator    VARCHAR(128)  NULL COMMENT '操作人',
    reason      VARCHAR(512)  NULL COMMENT '变更原因',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_schema_name (schema_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

每次修改 Schema 时自动记录一条或多条 ChangeLog，供审计和历史追溯。

---

## 五、极简总结（前后端代码里要写死的兼容逻辑）

### 后端运行时兜底逻辑
- 入参：新字段非必填、废弃字段忽略不报错、枚举旧值兼容、校验范围只松不紧
- 出参：旧字段全部返回、新字段允许 null、嵌套结构不迁移旧字段
- 容错：类型转换失败仅日志，不中断接口

### 前端渲染/请求兜底逻辑
- 所有字段可选链读取，空值兜底
- 未知枚举兼容展示，不崩溃
- 废弃字段解析保留，页面逐步隐藏
- 老页面缓存代码传废弃参数不报错