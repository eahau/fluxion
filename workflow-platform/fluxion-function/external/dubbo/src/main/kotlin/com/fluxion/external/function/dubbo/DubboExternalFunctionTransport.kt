/**
 * Dubbo-backed external function transport.
 *
 * Uses Dubbo's `GenericService` so we can invoke arbitrary service
 * interfaces without depending on the application stub jars at compile
 * time. The caller provides an interface FQCN, a method name and a list of
 * parameter types through the config extras; we build, cache and reuse a
 * `ReferenceConfig` keyed by those routing inputs.
 *
 * Example `ExternalFunctionConfig` JSON:
 * ```json
 * {
 *   "protocol": "dubbo",
 *   "service": "com.example.UserService",
 *   "method": "getUser",
 *   "endpoint": "dubbo://127.0.0.1:20880",
 *   "extras": {
 *     "version": "1.0.0",
 *     "group": "default",
 *     "parameterTypes": ["java.lang.Long"],
 *     "registryAddress": "nacos://127.0.0.1:8848"
 *   }
 * }
 * ```
 *
 * Resource lifecycle (channels, reference configs, application models) is
 * delegated to [ExternalFunctionTransportLifecycle] so we never leak a
 * Dubbo scope model across redeploys.
 */
package com.fluxion.external.function.dubbo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionRequest
import com.fluxion.core.function.external.ExternalFunctionResponse
import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.function.external.ExternalFunctionTransportLifecycle
import org.apache.dubbo.config.ApplicationConfig
import org.apache.dubbo.config.ReferenceConfig
import org.apache.dubbo.config.RegistryConfig
import org.apache.dubbo.rpc.model.ApplicationModel
import org.apache.dubbo.rpc.model.FrameworkModel
import org.apache.dubbo.rpc.model.ModuleModel
import org.apache.dubbo.rpc.service.GenericService
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic-service based Dubbo transport; caches `ReferenceConfig` and
 * `ApplicationModel` instances keyed by the unique routing tuple.
 */
class DubboExternalFunctionTransport : ExternalFunctionTransport {

    companion object {
        private const val DEFAULT_APPLICATION_NAME = "fluxion-external-function"
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private val lifecycle = ExternalFunctionTransportLifecycle()
    private val referenceCache = ConcurrentHashMap<String, ReferenceConfig<GenericService>>()
    private val applicationModelCache = ConcurrentHashMap<String, ApplicationModel>()
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    init {
        lifecycle.onClearCache("dubbo-reference-cache") { referenceCache.clear() }
        lifecycle.onClearCache("dubbo-application-model-cache") { applicationModelCache.clear() }
    }

    override fun protocol(): String = "dubbo"

    override fun prepare(configs: List<ExternalFunctionConfig>) {
        if (lifecycle.isShutdown()) {
            log.warn { "Dubbo transport is shutdown, skipping prepare for ${configs.size} config(s)" }
            return
        }
        configs.forEach { config ->
            val key = buildCacheKey(config)
            referenceCache.computeIfAbsent(key) { buildReference(config) }
        }
        log.info { "Dubbo transport prepared ${configs.size} reference(s)" }
    }

    override fun invoke(request: ExternalFunctionRequest): ExternalFunctionResponse {
        if (lifecycle.isShutdown()) {
            return errorResponse("Dubbo transport is shutdown")
        }
        val config = request.config
        val service = config.service
            ?: return errorResponse("service is required for dubbo protocol")
        val method = config.method
            ?: return errorResponse("method is required for dubbo protocol")

        val reference = referenceCache.computeIfAbsent(buildCacheKey(config)) { buildReference(config) }
        val genericService = reference.get()
            ?: return errorResponse("Failed to obtain Dubbo GenericService reference for [$service]")

        val parameterTypes = resolveParameterTypes(config, request.input)
        val args = resolveArgs(config, request.input)

        return try {
            log.debug { "Dubbo generic invoke [$service:$method], parameterTypes=${parameterTypes.contentToString()}, args=${args.contentToString()}" }
            val result = genericService.`$invoke`(method, parameterTypes, args)
            ExternalFunctionResponse(output = convertResult(result))
        } catch (ex: Exception) {
            log.error(ex) { "Dubbo generic invoke failed: [$service:$method]" }
            ExternalFunctionResponse(
                output = null,
                success = false,
                errorCode = "DUBBO_INVOKE_ERROR",
                errorMessage = ex.message ?: ex.javaClass.name
            )
        }
    }

    private fun buildReference(config: ExternalFunctionConfig): ReferenceConfig<GenericService> {
        val applicationName = config.extraString("applicationName") ?: DEFAULT_APPLICATION_NAME
        val key = buildCacheKey(config)
        val applicationModel: ApplicationModel = applicationModelCache.computeIfAbsent(key) {
            FrameworkModel.defaultModel().newApplication().apply {
                applicationConfigManager.setApplication(ApplicationConfig(applicationName))
                lifecycle.onShutdown("dubbo-application-model:`$key") {
                    if (!this.isDestroyed) {
                        this.destroy()
                    }
                }
            }
        }
        val moduleModel: ModuleModel = applicationModel.getDefaultModule()

        val reference = ReferenceConfig<GenericService>()
        reference.scopeModel = moduleModel
        reference.setInterface(config.service)
        reference.setGeneric("true")
        reference.version = config.extraString("version") ?: ""
        reference.group = config.extraString("group") ?: ""

        if (config.timeoutMs > 0) {
            reference.timeout = config.timeoutMs.toInt()
        }

        if (!config.endpoint.isNullOrBlank()) {
            reference.url = config.endpoint
        } else {
            val registryAddress = config.extraString("registryAddress")
            if (!registryAddress.isNullOrBlank()) {
                reference.registry = RegistryConfig(applicationModel, registryAddress)
            }
        }
        lifecycle.onShutdown("dubbo-reference:`$key") { reference.destroy() }
        return reference
    }

    private fun buildCacheKey(config: ExternalFunctionConfig): String {
        val applicationName = config.extraString("applicationName") ?: DEFAULT_APPLICATION_NAME
        val registryAddress = config.extraString("registryAddress") ?: ""
        return listOfNotNull(
            config.service,
            config.extraString("version"),
            config.extraString("group"),
            config.endpoint,
            registryAddress,
            applicationName,
            config.timeoutMs.takeIf { it > 0 }
        ).joinToString(":")
    }

    private fun resolveParameterTypes(config: ExternalFunctionConfig, input: Any?): Array<String> {
        return config.extraStringList("parameterTypes")?.toTypedArray()
            ?: (input as? Map<*, *>)?.let { arrayOf(Map::class.java.name) }
            ?: emptyArray()
    }

    private fun resolveArgs(config: ExternalFunctionConfig, input: Any?): Array<Any?> {
        val parameterTypes = resolveParameterTypes(config, input)
        if (parameterTypes.isEmpty()) {
            return emptyArray()
        }

        // Single parameter shortcut: pass the engine input straight through.
        if (parameterTypes.size == 1) {
            return arrayOf(input)
        }

        // Multi-param: pull positional values from the extras.args list, each
        // entry may be a literal or a $-prefixed JSON pointer into the input.
        val argsConfig = config.extras?.get("args") as? List<*>
            ?: return arrayOfNulls(parameterTypes.size)
        return argsConfig.map { resolveArg(it, input) }.toTypedArray()
    }

    private fun resolveArg(value: Any?, input: Any?): Any? {
        if (value !is String || !value.startsWith("$")) return value
        val path = value.removePrefix("$")
        return getByPath(input as? Map<*, *>, path)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getByPath(source: Map<*, *>?, path: String): Any? {
        if (source == null) return null
        val parts = path.split('.')
        var current: Any? = source
        for (part in parts) {
            current = (current as? Map<*, *>)?.get(part) ?: return null
        }
        return current
    }

    private fun convertResult(result: Any?): Any? {
        return when (result) {
            null -> null
            is Map<*, *>, is List<*>, is String, is Number, is Boolean -> result
            else -> try {
                objectMapper.convertValue(result, Map::class.java)
            } catch (ex: Exception) {
                // Fallback: if a POJO refuses conversion to Map, serialise
                // to JSON text so downstream nodes still receive something
                // serialisable rather than an opaque proxy.
                objectMapper.writeValueAsString(result)
            }
        }
    }

    override fun shutdown() {
        val referencesBeforeShutdown = referenceCache.size
        val applicationModelsBeforeShutdown = applicationModelCache.size
        lifecycle.shutdown()
        log.info {
            "Dubbo transport shutdown completed, " +
                "$referencesBeforeShutdown reference(s) and $applicationModelsBeforeShutdown application model(s) released"
        }
    }

    private fun errorResponse(message: String): ExternalFunctionResponse = ExternalFunctionResponse(
        output = null,
        success = false,
        errorCode = "DUBBO_CONFIG_ERROR",
        errorMessage = message
    )
}
