package com.fluxion.core.enums

/** 节点函数类型（决定 FunctionRegistry 的解析优先级） */
enum class NodeType {
    /** 内置函数（builtin:* 前缀，引擎内置） */
    BUILTIN,
    /** 自定义函数（代码中 registry.register() 注册） */
    CUSTOM,
    /** 脚本函数（Groovy/JS，存储在 wf_function 表） */
    SCRIPT,
    /** 外部服务函数（通过 Function Gateway gRPC/HTTP 调用） */
    EXTERNAL,
    /** 循环节点（遍历数组，每次迭代执行子节点） */
    LOOP,
    /** 等待节点（暂停执行，等待外部信号恢复） */
    WAIT,
    /** 子工作流节点（调用另一个工作流，实现逻辑复用与组合） */
    SUB_WORKFLOW
}
