// Entities/Function API —— 实体级 CRUD（仅限实体自身的增删改查 + publish/deprecate 这类实体状态转换）
// 注：testFunction 属于"执行动作"，不是实体 CRUD，放在 features/function-test/api 下，feature 自己内聚，不污染 entity
import { apiGet, apiPost, apiPut, apiDelete, RequestOptions } from '@/shared/api/request';
import type { FunctionDefinition } from './model/types';

export async function listFunctions(
  params?: { keyword?: string; category?: string; page?: number; pageSize?: number },
  options?: RequestOptions,
) {
  return apiGet<API.PageResponse<FunctionDefinition>>('/api/admin/functions', params, options);
}

export async function getFunction(id: string, options?: RequestOptions) {
  return apiGet<FunctionDefinition>(`/api/admin/functions/${id}`, undefined, options);
}

export async function createFunction(data: Partial<FunctionDefinition>, options?: RequestOptions) {
  return apiPost<FunctionDefinition>('/api/admin/functions', data, options);
}

export async function updateFunction(id: string, data: Partial<FunctionDefinition>, options?: RequestOptions) {
  return apiPut<FunctionDefinition>(`/api/admin/functions/${id}`, data, options);
}

export async function deleteFunction(id: string, options?: RequestOptions) {
  return apiDelete<void>(`/api/admin/functions/${id}`, options);
}

export async function publishFunction(id: string, options?: RequestOptions) {
  return apiPost<FunctionDefinition>(`/api/admin/functions/${id}/publish`, {}, options);
}

export async function deprecateFunction(id: string, options?: RequestOptions) {
  return apiPost<FunctionDefinition>(`/api/admin/functions/${id}/deprecate`, {}, options);
}
