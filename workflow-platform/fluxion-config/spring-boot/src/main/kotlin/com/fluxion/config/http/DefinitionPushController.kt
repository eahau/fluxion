package com.fluxion.config.http

import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.WorkflowDefinitionSnapshot
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP push endpoint — Worker side.
 */
@RestController
@RequestMapping("/internal/workflow/definition")
class DefinitionPushController(
    private val subscriber: HttpDefinitionConfigSubscriber
) {

    @PostMapping("/push")
    fun receivePush(@RequestBody payload: HttpDefinitionPushPayload): ResponseEntity<Void> {
        val snapshot: WorkflowDefinitionSnapshot
        val changeType: ChangeType

        if (payload.removed) {
            snapshot = WorkflowDefinitionSnapshot.removed(payload.workflowId)
            changeType = ChangeType.REMOVE
        } else {
            snapshot = WorkflowDefinitionSnapshot.of(
                payload.workflowId, payload.definitionJson!!, payload.version, true
            )
            changeType = ChangeType.UPDATE
        }

        subscriber.onPushReceived(payload.workflowId, snapshot, changeType)
        return ResponseEntity.ok().build()
    }
}
