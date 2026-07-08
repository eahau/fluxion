import { client, unwrap } from '@/sdk';
import { apiGet } from './request';
import type { ExecutionRecord, TraceDetail } from '@/types/api';

export async function listExecutions(params?: {
  keyword?: string;
  status?: string;
  workflowId?: string;
  page?: number;
  pageSize?: number;
}) {
  return unwrap(
    await client.GET('/api/admin/executions', { params: { query: params ?? {} } }),
  ) as unknown as API.PageResponse<ExecutionRecord>;
}

export async function getExecution(executionId: string) {
  return unwrap(
    await client.GET('/api/admin/executions/{executionId}', {
      params: { path: { executionId } },
    }),
  ) as TraceDetail;
}

export async function rerunExecution(executionId: string, nodeId?: string) {
  return unwrap(
    await client.POST('/api/admin/executions/{executionId}/rerun', {
      params: { path: { executionId } },
      body: { nodeId } as any,
    }),
  ) as TraceDetail;
}

export async function sendSignal(executionId: string, signalName: string, payload?: any) {
  return unwrap(
    await client.POST('/api/admin/executions/{executionId}/signal', {
      params: { path: { executionId } },
      body: { signalName, payload } as any,
    }),
  );
}

export async function queryExecution(executionId: string) {
  return apiGet<any>(`/api/admin/executions/${encodeURIComponent(executionId)}/query`);
}

export async function listRunningExecutions() {
  return apiGet<any[]>('/api/admin/executions/running');
}
