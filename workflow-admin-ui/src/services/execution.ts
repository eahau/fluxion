import { apiGet, apiPost } from './request';
import type { ExecutionRecord, TraceDetail } from '@/types/api';

export async function listExecutions(params?: {
  keyword?: string;
  status?: string;
  workflowId?: string;
  page?: number;
  pageSize?: number;
}) {
  return apiGet<API.PageResponse<ExecutionRecord>>('/api/admin/executions', params);
}

export async function getExecution(executionId: string) {
  return apiGet<TraceDetail>(`/api/admin/executions/${executionId}`);
}

export async function rerunExecution(executionId: string, nodeId?: string) {
  return apiPost<TraceDetail>(`/api/admin/executions/${executionId}/rerun`, { nodeId });
}

// ─── Signal/Query API ─────────────────────────────────────────────

/** 发送信号到运行中的工作流执行 */
export async function sendSignal(executionId: string, signalName: string, payload?: any) {
  return apiPost(`/api/admin/executions/${executionId}/signals`, { signalName, payload });
}

/** 查询工作流执行状态（运行中/已完成） */
export async function queryExecution(executionId: string) {
  return apiGet<any>(`/api/admin/executions/${executionId}/query`);
}

/** 列出所有运行中的执行 */
export async function listRunningExecutions() {
  return apiGet<any[]>('/api/admin/executions/running');
}
