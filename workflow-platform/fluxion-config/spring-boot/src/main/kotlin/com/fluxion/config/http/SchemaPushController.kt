package com.fluxion.config.http

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP Schema 鎺ㄩ€佹帴鏀剁鐐?鈥?Worker 渚?
 *
 * 鎺ユ敹 Admin 閫氳繃 [HttpSchemaConfigPublisher] 鎺ㄩ€佺殑 Schema 閰嶇疆鍙樻洿銆?
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
