import { client, unwrap, silentHeaders, type RequestOptions } from '@/sdk';
import { apiPost } from '@/services/request';
import type { FunctionDefinition } from './model/types';

export async function listFunctions(
  params?: { keyword?: string; category?: string; page?: number; pageSize?: number },
  options?: RequestOptions,
) {
  return unwrap(
    await client.GET('/api/admin/functions', {
      params: { query: params ?? {} },
      headers: silentHeaders(options),
    }),
  ) as unknown as API.PageResponse<FunctionDefinition>;
}

export async function getFunction(id: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/functions/{functionName}', {
      params: { path: { functionName: id } },
      headers: silentHeaders(options),
    }),
  ) as FunctionDefinition;
}

export async function createFunction(data: Partial<FunctionDefinition>, options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/functions', {
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as FunctionDefinition;
}

export async function updateFunction(
  id: string,
  data: Partial<FunctionDefinition>,
  options?: RequestOptions,
) {
  return unwrap(
    await client.PUT('/api/admin/functions/{functionName}', {
      params: { path: { functionName: id } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as FunctionDefinition;
}

export async function deleteFunction(id: string, options?: RequestOptions) {
  unwrap(
    await client.DELETE('/api/admin/functions/{functionName}', {
      params: { path: { functionName: id } },
      headers: silentHeaders(options),
    }),
  );
  return undefined as void;
}

export async function publishFunction(id: string, options?: RequestOptions) {
  return apiPost<FunctionDefinition>(`/api/admin/functions/${encodeURIComponent(id)}/publish`, undefined, options);
}

export async function deprecateFunction(id: string, options?: RequestOptions) {
  return apiPost<FunctionDefinition>(`/api/admin/functions/${encodeURIComponent(id)}/deprecate`, undefined, options);
}
