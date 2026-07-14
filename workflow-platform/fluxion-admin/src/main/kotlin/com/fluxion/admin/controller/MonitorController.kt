package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.MonitorApi
import com.fluxion.admin.generated.model.ErrorLog
import com.fluxion.admin.generated.model.MetricsOverview
import com.fluxion.admin.generated.model.MetricsTrend
import com.fluxion.admin.generated.model.NodeLatency
import com.fluxion.admin.generated.model.PageResponseErrorLog
import com.fluxion.admin.generated.model.TraceDetail
import com.fluxion.admin.generated.model.TraceNode
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Observability / monitoring REST API for the admin dashboard.
 *
 * Implements the generated OpenAPI `MonitorApi` contract. The current implementation
 * returns empty zero-state placeholders; a follow-up integration will wire a real
 * metrics backend (Micrometer + Prometheus or ClickHouse) and execution trace store.
 */
@RestController
class MonitorController : MonitorApi {

    override fun getMetricsOverview(): ResponseEntity<MetricsOverview> {
        return ResponseEntity.ok(MetricsOverview().apply {
            totalRequests = 0
            successRate = 100.0
            p99LatencyMs = 0
            activeWorkflows = 0
        })
    }

    override fun getMetricsTrend(
        metric: String?,
        startTime: Long?,
        endTime: Long?,
        hours: Int?,
        interval: String?
    ): ResponseEntity<MetricsTrend> {
        return ResponseEntity.ok(MetricsTrend().apply {
            this.metric = metric ?: "requests"
            this.interval = interval ?: "1h"
            points = mutableListOf()
        })
    }

    override fun getNodeLatencies(): ResponseEntity<List<NodeLatency>> {
        return ResponseEntity.ok(emptyList())
    }

    override fun getTrace(executionId: String): ResponseEntity<TraceDetail> {
        return ResponseEntity.ok(TraceDetail().apply {
            this.executionId = executionId
            workflowName = ""
            totalDurationMs = 0
            status = "UNKNOWN"
            traces = mutableListOf()
        })
    }

    override fun listErrorLogs(
        keyword: String?,
        startTime: Long?,
        endTime: Long?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseErrorLog> {
        return ResponseEntity.ok(PageResponseErrorLog().apply {
            list = mutableListOf()
            total = 0
            this.page = page
            this.pageSize = pageSize
        })
    }
}
