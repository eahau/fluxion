import { apiGet, apiPost, apiPut, apiDelete } from './request';
import type { WorkflowDefinition, DebugResult, DebugExecutionRecord } from '@/types/workflow';
import type { MockConfig, PublishTarget } from '@/types/api';
import type { RequestOptions } from './request';

export async function getWorkflows(
  params?: { keyword?: string; page?: number; pageSize?: number },
  options?: RequestOptions,
) {
  return apiGet<API.PageResponse<WorkflowDefinition>>('/api/admin/workflows', params, options);
}

export async function getWorkflow(id: string, options?: RequestOptions) {
  return apiGet<WorkflowDefinition>(`/api/admin/workflows/${id}`, undefined, options);
}

export async function createWorkflow(data: WorkflowDefinition, options?: RequestOptions) {
  return apiPost<WorkflowDefinition>('/api/admin/workflows', data, options);
}

export async function updateWorkflow(id: string, data: WorkflowDefinition, options?: RequestOptions) {
  return apiPut<WorkflowDefinition>(`/api/admin/workflows/${id}`, data, options);
}

export async function deleteWorkflow(id: string, options?: RequestOptions) {
  return apiDelete<void>(`/api/admin/workflows/${id}`, options);
}

export async function publishWorkflow(id: string, target?: PublishTarget, options?: RequestOptions) {
  return apiPost<WorkflowDefinition>(`/api/admin/workflows/${id}/publish`, target, options);
}

export async function rollbackWorkflow(id: string, version: number, options?: RequestOptions) {
  return apiPost<WorkflowDefinition>(`/api/admin/workflows/${id}/rollback`, { version }, options);
}

export async function getWorkflowVersions(id: string, options?: RequestOptions) {
  return apiGet<WorkflowDefinition[]>(`/api/admin/workflows/${id}/versions`, undefined, options);
}

export async function debugWorkflow(
  id: string,
  inputs: Record<string, any>,
  breakpoints?: string[],
  mockConfig?: MockConfig,
  options?: RequestOptions,
) {
  return apiPost<DebugResult>(
    `/api/admin/workflows/${id}/debug`,
    { inputs, breakpoints, mockConfig },
    options,
  );
}

export async function debugWorkflowNode(
  id: string,
  nodeId: string,
  inputData?: any,
  mockConfig?: MockConfig,
  options?: RequestOptions,
) {
  return apiPost<any>(`/api/admin/workflows/${id}/debug/node`, { nodeId, inputData, mockConfig }, options);
}

export async function debugWorkflowStep(
  id: string,
  snapshot: any,
  breakpoints?: string[],
  options?: RequestOptions,
) {
  return apiPost<DebugResult>(
    `/api/admin/workflows/${id}/debug/step`,
    { snapshot, breakpoints },
    options,
  );
}

export async function rerunWorkflowNode(
  id: string,
  snapshot: any,
  nodeId: string,
  overrideInput?: any,
  options?: RequestOptions,
) {
  return apiPost<DebugResult>(
    `/api/admin/workflows/${id}/debug/rerun`,
    { snapshot, nodeId, overrideInput },
    options,
  );
}

export async function getDebugHistory(id: string, page = 1, size = 20, options?: RequestOptions) {
  return apiGet<API.PageResponse<DebugExecutionRecord>>(
    `/api/admin/workflows/${id}/debug/history`,
    { page, size },
    options,
  );
}

export async function getDebugHistoryDetail(id: string, executionId: string, options?: RequestOptions) {
  return apiGet<DebugExecutionRecord>(
    `/api/admin/workflows/${id}/debug/history/${executionId}`,
    undefined,
    options,
  );
}

export async function importOpenAPI(file: File, options?: RequestOptions) {
  const formData = new FormData();
  formData.append('file', file);
  return apiPost<any>('/api/admin/workflows/import/openapi', formData, options);
}

export async function confirmImportOpenAPI(items: WorkflowDefinition[], options?: RequestOptions) {
  return apiPost<WorkflowDefinition[]>('/api/admin/workflows/import/openapi/confirm', { items }, options);
}

export async function importWorkflowDefinition(file: File, options?: RequestOptions) {
  const formData = new FormData();
  formData.append('file', file);
  return apiPost<WorkflowDefinition>('/api/admin/workflows/import/definition', formData, options);
}

export async function exportOpenAPI(
  ids?: string[],
  title?: string,
  version?: string,
  options?: RequestOptions,
) {
  const params: Record<string, any> = {};
  if (title) params.title = title;
  if (version) params.version = version;
  if (ids && ids.length === 1) {
    return apiGet<any>(`/api/admin/workflows/${ids[0]}/export/openapi`, params, options);
  }
  if (ids && ids.length > 1) {
    return apiPost<any>('/api/admin/workflows/export/openapi', { ids, title, version }, options);
  }
  return apiGet<any>('/api/admin/workflows/export/openapi', params, options);
}
