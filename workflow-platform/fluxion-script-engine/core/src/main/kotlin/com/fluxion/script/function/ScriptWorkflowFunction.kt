package com.fluxion.script.function

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import com.fluxion.script.groovy.GroovyScriptFunction

/**
 * 配置中心推送的脚本函数包装器。
 *
 * 每个 SCRIPT 函数在 Worker 侧被注册为一个独立的 WorkflowFunction，
 * 执行时把预先加载的脚本内容作为 `script` 参数委托给通用脚本引擎。
 */
class ScriptWorkflowFunction(
    private val name: String,
    private val scriptBody: String,
    private val paramSchema: String? = null,
    private val outputSchema: String? = null,
    private val description: String? = null,
    private val groovyEngine: GroovyScriptFunction? = null
) : WorkflowFunction<Any> {

    override fun apply(input: NodeInput): FunctionResult<Any> {
        val engineFn = groovyEngine
            ?: throw IllegalStateException("Groovy engine not available for function [$name]")
        val engineInput = NodeInput(
            directInput = input.directInput,
            nodeParams = mapOf("script" to scriptBody) + input.nodeParams,
            declaredDeps = input.declaredDeps,
            workflowInput = input.workflowInput,
            meta = input.meta
        )
        return engineFn.apply(engineInput).uncheckedCast<FunctionResult<Any>>()!!
    }

    override fun meta(): FunctionMeta = FunctionMeta.builder(name)
        .description(description ?: "Script function: $name")
        .apply {
            paramSchema?.let { paramSchema(it) }
            outputSchema?.let { outputSchema(it) }
        }
        .build()
}
