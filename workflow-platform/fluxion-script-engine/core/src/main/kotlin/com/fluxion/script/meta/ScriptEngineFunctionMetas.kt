package com.fluxion.script.meta

import com.fluxion.core.value.FunctionMeta

/**
 * 内置函数元信息常量（由代码生成，禁止手动修改；schema 变更请修改对应函数类后重新生成）
 */
object ScriptEngineFunctionMetas {

    @JvmField
    val GROOVY_SCRIPT = FunctionMeta.builder("builtin:groovyScript")
        .description("执行 Groovy 脚本，支持 inline/scriptRef 双模式，Caffeine 编译缓存")
        .domain("script")
        .paramSchema("""
{
        "type": "object",
        "properties": {
                "script": {
                        "type": "string",
                        "description": "Groovy 脚本代码（inline 模式）"
                },
                "scriptRef": {
                        "type": "string",
                        "description": "脚本引用 ID（initScript 模式）"
                }
        }
}
        """)
        .build()

}
