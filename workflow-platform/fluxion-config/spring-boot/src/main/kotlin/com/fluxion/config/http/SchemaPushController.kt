package com.fluxion.config.http

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP Schema 推送接收端点 — Worker 侧
 *
 * 接收 Admin 通过 [HttpSchemaConfigPublisher] 推送的 Schema 配置变更。
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
