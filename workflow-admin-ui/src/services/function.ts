import { apiGet, apiPost, apiPut, apiDelete } from './request';
import type { FunctionDefinition } from '@/types/function';
import type { RequestOptions } from './request';

export async function getFunctions(
  params?: { keyword?: string; category?: string; page?: number; pageSize?: number },
  options?: RequestOptions,
) {
  return apiGet<API.PageResponse<FunctionDefinition>>('/api/admin/functions', params, options);
}

export async function getFunction(id: string, options?: RequestOptions) {
  return apiGet<FunctionDefinition>(`/api/admin/functions/${id}`, undefined, options);
}

export async function createFunction(data: FunctionDefinition, options?: RequestOptions) {
  return apiPost<FunctionDefinition>('/api/admin/functions', data, options);
}

export async function updateFunction(id: string, data: FunctionDefinition, options?: RequestOptions) {
  return apiPut<FunctionDefinition>(`/api/admin/functions/${id}`, data, options);
}

export async function deleteFunction(id: string, options?: RequestOptions) {
  return apiDelete<void>(`/api/admin/functions/${id}`, options);
}

export async function testFunction(
  id: string,
  data: {
    inputs: any;
    directInput?: any;
    workflowInput?: any;
    declaredDeps?: any;
  },
  options?: RequestOptions,
) {
  return apiPost<any>(`/api/admin/functions/${id}/test`, data, options);
}

export async function publishFunction(id: string, options?: RequestOptions) {
  return apiPost<FunctionDefinition>(`/api/admin/functions/${id}/publish`, {}, options);
}

export async function deprecateFunction(id: string, options?: RequestOptions) {
  return apiPost<FunctionDefinition>(`/api/admin/functions/${id}/deprecate`, {}, options);
}
