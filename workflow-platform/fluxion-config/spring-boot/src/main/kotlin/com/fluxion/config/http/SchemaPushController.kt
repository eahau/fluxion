package com.fluxion.config.http

import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.SchemaConfigSnapshot
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP Schema push endpoint — Worker side.
 *
 * Receives Schema config updates pushed by Admin via [HttpSchemaConfigPublisher].
 */
@RestController
@RequestMapping("/internal/workflow/schema")
class SchemaPushController(
    private val subscriber: HttpSchemaConfigSubscriber
) {

    @PostMapping("/push")
    fun receivePush(@RequestBody snapshot: SchemaConfigSnapshot): ResponseEntity<Void> {
        val changeType = if (snapshot.enabled) ChangeType.UPDATE else ChangeType.REMOVE
        subscriber.onPushReceived(snapshot, changeType)
        return ResponseEntity.ok().build()
    }
}
