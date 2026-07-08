import { client, unwrap, silentHeaders } from '@/sdk';
import { apiGet, apiPost } from './request';
import type { WorkflowDefinition, DebugResult, DebugExecutionRecord } from '@/types/workflow';
import type { MockConfig, PublishTarget } from '@/types/api';
import type { RequestOptions } from './request';

export async function getWorkflows(
  params?: { keyword?: string; page?: number; pageSize?: number },
  options?: RequestOptions,
) {
  return unwrap(
    await client.GET('/api/admin/workflows', {
      params: { query: params ?? {} },
      headers: silentHeaders(options),
    }),
  ) as unknown as API.PageResponse<WorkflowDefinition>;
}

export async function getWorkflow(id: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/workflows/{workflowId}', {
      params: { path: { workflowId: id } },
      headers: silentHeaders(options),
    }),
  ) as WorkflowDefinition;
}

export async function createWorkflow(data: WorkflowDefinition, options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/workflows', {
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as WorkflowDefinition;
}

export async function updateWorkflow(id: string, data: WorkflowDefinition, options?: RequestOptions) {
  return unwrap(
    await client.PUT('/api/admin/workflows/{workflowId}', {
      params: { path: { workflowId: id } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as WorkflowDefinition;
}

export async function deleteWorkflow(id: string, options?: RequestOptions) {
  unwrap(
    await client.DELETE('/api/admin/workflows/{workflowId}', {
      params: { path: { workflowId: id } },
      headers: silentHeaders(options),
    }),
  );
  return undefined as void;
}

export async function publishWorkflow(id: string, target?: PublishTarget, options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/workflows/{workflowId}/publish', {
      params: { path: { workflowId: id } },
      body: target as any,
      headers: silentHeaders(options),
    }),
  ) as WorkflowDefinition;
}

export async function rollbackWorkflow(id: string, version: number, options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/workflows/{workflowId}/rollback', {
      params: { path: { workflowId: id } },
      body: { version } as any,
      headers: silentHeaders(options),
    }),
  ) as WorkflowDefinition;
}

export async function getWorkflowVersions(id: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/workflows/{workflowId}/versions', {
      params: { path: { workflowId: id } },
      headers: silentHeaders(options),
    }),
  ) as WorkflowDefinition[];
}

export async function debugWorkflow(
  id: string,
  inputs: Record<string, any>,
  breakpoints?: string[],
  mockConfig?: MockConfig,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/workflows/{workflowId}/debug', {
      params: { path: { workflowId: id } },
      body: { inputs, breakpoints, mockConfig } as any,
      headers: silentHeaders(options),
    }),
  ) as DebugResult;
}

export async function debugWorkflowNode(
  id: string,
  nodeId: string,
  inputData?: any,
  mockConfig?: MockConfig,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/workflows/{workflowId}/debug/node', {
      params: { path: { workflowId: id } },
      body: { nodeId, inputData, mockConfig } as any,
      headers: silentHeaders(options),
    }),
  );
}

export async function debugWorkflowStep(
  id: string,
  snapshot: any,
  breakpoints?: string[],
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/workflows/{workflowId}/debug/step', {
      params: { path: { workflowId: id } },
      body: { snapshot, breakpoints } as any,
      headers: silentHeaders(options),
    }),
  ) as DebugResult;
}

export async function rerunWorkflowNode(
  id: string,
  snapshot: any,
  nodeId: string,
  overrideInput?: any,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/workflows/{workflowId}/debug/rerun', {
      params: { path: { workflowId: id } },
      body: { snapshot, nodeId, overrideInput } as any,
      headers: silentHeaders(options),
    }),
  ) as DebugResult;
}

export async function getDebugHistory(id: string, page = 1, size = 20, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/workflows/{workflowId}/debug/history', {
      params: { path: { workflowId: id }, query: { page, size } },
      headers: silentHeaders(options),
    }),
  ) as unknown as API.PageResponse<DebugExecutionRecord>;
}

export async function getDebugHistoryDetail(id: string, executionId: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/workflows/{workflowId}/debug/history/{executionId}', {
      params: { path: { workflowId: id, executionId } },
      headers: silentHeaders(options),
    }),
  ) as DebugExecutionRecord;
}

export async function importOpenAPI(file: File, options?: RequestOptions) {
  const formData = new FormData();
  formData.append('file', file);
  return unwrap(
    await client.POST('/api/admin/workflows/import/openapi', {
      body: formData as any,
      headers: silentHeaders(options),
    }),
  );
}

export async function confirmImportOpenAPI(items: WorkflowDefinition[], options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/workflows/import/openapi/confirm', {
      body: { items } as any,
      headers: silentHeaders(options),
    }),
  ) as WorkflowDefinition[];
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
    const queryStr = new URLSearchParams(params).toString();
    return apiGet<any>(
      `/api/admin/workflows/${encodeURIComponent(ids[0])}/export/openapi${queryStr ? '?' + queryStr : ''}`,
      undefined,
      options,
    );
  }
  if (ids && ids.length > 1) {
    return unwrap(
      await client.POST('/api/admin/workflows/export/openapi', {
        body: { ids, title, version } as any,
        headers: silentHeaders(options),
      }),
    );
  }
  return unwrap(
    await client.GET('/api/admin/workflows/export/openapi', {
      params: { query: params },
      headers: silentHeaders(options),
    }),
  );
}
