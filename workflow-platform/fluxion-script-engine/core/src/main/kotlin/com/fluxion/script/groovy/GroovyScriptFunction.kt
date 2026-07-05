package com.fluxion.script.groovy

import com.fluxion.script.meta.ScriptEngineFunctionMetas

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionResult
import com.fluxion.di.DependencyResolver
import com.fluxion.script.util.BeanNameMap
import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.Script
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import org.slf4j.*

/**
 * Groovy 脚本执行函数
 */
class GroovyScriptFunction(maxCacheSize: Int = 500) : WorkflowFunction<Any> {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Caffeine 编译缓存：scriptHash → Script Class */
    private val compileCache: Cache<Int, Class<out Script>> =
        Caffeine.newBuilder().maximumSize(maxCacheSize.toLong()).build()

    /**
     * 可选的 scriptRef 解析器。
     * 当节点参数使用 scriptRef 而非 inline script 时，优先通过解析器获取实际脚本内容。
     */
    var scriptRefResolver: ((String) -> String?)? = null

    /**
     * 可选的依赖解析器。
     * 向 Groovy 脚本暴露 `bean(name)` / `beanByType(type)` 函数，使其能访问 DI 容器中的实例。
     */
    var dependencyResolver: DependencyResolver? = null

    private val shell: GroovyShell

    init {
        val config = CompilerConfiguration()
        config.targetBytecode = CompilerConfiguration.JDK17

        val secure = SecureASTCustomizer()
        secure.allowedImports = listOf("java.util.*", "java.math.*", "groovy.json.*")
        config.addCompilationCustomizers(secure)

        this.shell = GroovyShell(Binding(), config)
    }

    override fun apply(input: NodeInput): FunctionResult<Any> {
        var rawScript: String? = input.param("script")
        if (rawScript.isNullOrBlank()) {
            val scriptRef = input.param<String>("scriptRef")
            if (!scriptRef.isNullOrBlank()) {
                rawScript = scriptRefResolver?.invoke(scriptRef) ?: scriptRef
            }
        }
        if (rawScript.isNullOrBlank()) {
            throw WorkflowNodeException(meta().name,
                IllegalArgumentException("Either 'script' or 'scriptRef' param is required"))
        }

        val script: String = rawScript
        val scriptHash = script.hashCode()

        val binding = buildBinding(input)
        try {
            val scriptClass: Class<out Script> = compileCache.get(scriptHash) {
                try {
                    shell.parse(script, "WorkflowScript_$scriptHash").javaClass
                } catch (ex: CompilationFailedException) {
                    throw RuntimeException("Script compilation failed: ${ex.message}", ex)
                }
            }!!

            val instance = scriptClass.getDeclaredConstructor().newInstance()
            instance.binding = binding
            val result: Any? = instance.run()
            return FunctionResult.success(result).uncheckedCast<FunctionResult<Any>>()!!
        } catch (ex: RuntimeException) {
            if (ex.cause is CompilationFailedException) {
                throw WorkflowNodeException(meta().name, ex.cause)
            }
            throw WorkflowNodeException(meta().name, ex)
        } catch (ex: Exception) {
            throw WorkflowNodeException(meta().name, ex)
        }
    }

    override fun meta() = ScriptEngineFunctionMetas.GROOVY_SCRIPT
    private fun buildBinding(input: NodeInput): Binding {
        val binding = Binding()
        binding.setVariable("input", input.directInput)
        binding.setVariable("params", input.nodeParams)
        binding.setVariable("deps", input.declaredDeps)
        binding.setVariable("wfInput", input.workflowInput)
        binding.setVariable("meta", input.meta)

        dependencyResolver?.let { resolver ->
            binding.setVariable("bean") { name: String -> resolver.resolve(name) }
            binding.setVariable("beanByType") { type: Class<*> -> resolver.resolveByType(type) }
            binding.setVariable("beanType") { className: String ->
                resolver.resolveByType(Class.forName(className))
            }
            binding.setVariable("beans", BeanNameMap(resolver))
        }

        return binding
    }
}
