package com.fluxion.config.http

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP 函数推送接收端点 — Worker 侧
 */
@RestController
@RequestMapping("/internal/workflow/function")
class FunctionPushController(
    private val subscriber: HttpFunctionConfigSubscriber
) {

    @PostMapping("/push")
    fun receivePush(@RequestBody snapshot: FunctionConfigSnapshot): ResponseEntity<Void> {
        val changeType = if (snapshot.enabled) ChangeType.UPDATE else ChangeType.REMOVE
        subscriber.onPushReceived(snapshot, changeType)
        return ResponseEntity.ok().build()
    }
}
