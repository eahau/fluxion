import { apiGet } from './request';
import type { MetricsOverview, NodeLatency, ErrorLog, TraceDetail, MetricsTrend } from '@/types/monitor';

export async function getMetricsOverview() {
  return apiGet<MetricsOverview>('/api/admin/monitor/metrics');
}

export async function getMetricsTrend(params?: { metric?: string; hours?: number }) {
  return apiGet<MetricsTrend>('/api/admin/monitor/metrics/trend', params);
}

export async function getNodeLatencies() {
  return apiGet<NodeLatency[]>('/api/admin/monitor/metrics/node-latencies');
}

export async function getErrorLogs(params?: { page?: number; pageSize?: number }) {
  return apiGet<API.PageResponse<ErrorLog>>('/api/admin/monitor/errors', params);
}

export async function getTrace(executionId: string) {
  return apiGet<TraceDetail>(`/api/monitor/traces/${executionId}`);
}
