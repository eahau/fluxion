package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.TriggerFunctionsApi
import com.fluxion.admin.generated.model.TriggerFunctionMeta
import com.fluxion.admin.generated.model.TriggerFunctionParam
import com.fluxion.admin.service.TriggerFunctionMetaRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class TriggerFunctionController(
    private val registry: TriggerFunctionMetaRegistry
) : TriggerFunctionsApi {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun listTriggerFunctions(): ResponseEntity<List<TriggerFunctionMeta>> {
        return try {
            val list = registry.listAll().map { spiMeta ->
                TriggerFunctionMeta(
                    functionRef = spiMeta.functionRef,
                    label = spiMeta.label,
                    paramSchema = spiMeta.paramSchema.map { p -> spiParamToGenerated(p) }.toMutableList()
                ).apply {
                    icon = spiMeta.icon
                    description = spiMeta.description.ifBlank { null }
                    legacyProtocol = spiMeta.legacyProtocol
                }
            }
            log.info("listTriggerFunctions: returning {} adapters -> {}", list.size, list.map { it.functionRef })
            ResponseEntity.ok(list)
        } catch (t: Throwable) {
            log.error("listTriggerFunctions: failed to build trigger meta list", t)
            ResponseEntity.ok(emptyList())
        }
    }

    private fun spiParamToGenerated(p: com.fluxion.core.spi.TriggerFunctionParam): TriggerFunctionParam =
        TriggerFunctionParam(
            name = p.name,
            type = p.type
        ).apply {
            required = p.required
            label = p.label
            description = p.description
            defaultValue = p.defaultValue
            options = p.options?.map { pair ->
                mutableListOf<Any>(pair.first, pair.second)
            }?.toMutableList()
        }
}
