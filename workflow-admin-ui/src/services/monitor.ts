import { client, unwrap } from '@/sdk';
import type { MetricsOverview, NodeLatency, ErrorLog, TraceDetail, MetricsTrend } from '@/types/monitor';

export async function getMetricsOverview() {
  return unwrap(
    await client.GET('/api/admin/monitor/metrics'),
  ) as MetricsOverview;
}

export async function getMetricsTrend(params?: { metric?: string; hours?: number }) {
  return unwrap(
    await client.GET('/api/admin/monitor/metrics/trend', { params: { query: params ?? {} } }),
  ) as MetricsTrend;
}

export async function getNodeLatencies() {
  return unwrap(
    await client.GET('/api/admin/monitor/metrics/node-latencies'),
  ) as NodeLatency[];
}

export async function getErrorLogs(params?: { page?: number; pageSize?: number }) {
  return unwrap(
    await client.GET('/api/admin/monitor/errors', { params: { query: params ?? {} } }),
  ) as unknown as API.PageResponse<ErrorLog>;
}

/**
 * Trace detail endpoint.
 *
 * Note: the legacy file hard-coded `/api/monitor/traces/...` (missing the
 * `/admin` prefix).  The OpenAPI spec defines the standard path
 * `/api/admin/monitor/traces/{executionId}`, which is what the SDK uses here.
 * If the server actually exposes the unprefixed path, add an Nginx rewrite or
 * fall back to `apiGet('/api/monitor/traces/' + executionId)`.
 */
export async function getTrace(executionId: string) {
  return unwrap(
    await client.GET('/api/admin/monitor/traces/{executionId}', {
      params: { path: { executionId } },
    }),
  ) as TraceDetail;
}
