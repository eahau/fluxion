package com.fluxion.script.function

import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionConfigParser
import com.fluxion.core.function.external.ExternalFunctionRequest
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult

/**
 * 外部函数工作流节点实现。
 *
 * 通过 [ExternalFunctionTransportRegistry] 按 protocol 路由到对应传输层，
 * 支持 HTTP / gRPC / Dubbo 等 outbound 调用。
 */
class ExternalWorkflowFunction(
    private val name: String,
    private val config: ExternalFunctionConfig,
    private val transportRegistry: ExternalFunctionTransportRegistry,
    private val paramSchema: String? = null,
    private val outputSchema: String? = null,
    private val description: String? = null
) : WorkflowFunction<Any?> {

    constructor(
        name: String,
        configMap: Map<String, Any>?,
        transportRegistry: ExternalFunctionTransportRegistry,
        paramSchema: String? = null,
        outputSchema: String? = null,
        description: String? = null
    ) : this(
        name = name,
        config = ExternalFunctionConfigParser.parse(configMap),
        transportRegistry = transportRegistry,
        paramSchema = paramSchema,
        outputSchema = outputSchema,
        description = description
    )

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val transport = transportRegistry.resolve(config.protocol)
            ?: throw WorkflowNodeException(
                name,
                UnsupportedOperationException(
                    "No ExternalFunctionTransport registered for protocol [${config.protocol}]. " +
                        "Registered protocols: ${transportRegistry.protocols()}"
                )
            )

        val mapping = config.paramMapping
        val requestInput = if (mapping.isNullOrEmpty()) {
            input.directInput
        } else {
            mapInput(mapping, input.directInput)
        }

        val request = ExternalFunctionRequest(
            config = config,
            input = requestInput,
            functionRef = name,
            description = description
        )

        val response = try {
            transport.invoke(request)
        } catch (ex: Exception) {
            throw WorkflowNodeException(name, ex)
        }

        if (!response.success) {
            throw WorkflowNodeException(
                name,
                RuntimeException(
                    "External function invocation failed: [${response.errorCode}] ${response.errorMessage}"
                )
            )
        }

        return FunctionResult.success(response.output)
    }

    override fun meta(): FunctionMeta = FunctionMeta.builder(name)
        .description(description ?: "External function: $name")
        .apply {
            paramSchema?.let { paramSchema(it) }
            outputSchema?.let { outputSchema(it) }
        }
        .build()

    /**
     * 按 paramMapping 从源对象中提取字段，构造目标参数 Map。
     * value 支持点号路径，如 user.address.city。
     */
    private fun mapInput(mapping: Map<String, String>, source: Any?): Map<String, Any?> {
        val srcMap = source as? Map<*, *> ?: return emptyMap()
        return mapping.mapValues { (_, path) -> getByPath(srcMap, path) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getByPath(source: Map<*, *>, path: String): Any? {
        val parts = path.split('.')
        var current: Any? = source
        for (part in parts) {
            current = (current as? Map<*, *>)?.get(part) ?: return null
        }
        return current
    }
}
