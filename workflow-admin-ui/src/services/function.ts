import { client, unwrap, silentHeaders } from '@/sdk';
import { apiPost } from './request';
import type { FunctionDefinition } from '@/types/function';
import type { RequestOptions } from './request';

export async function getFunctions(
  params?: {
    keyword?: string;
    category?: string;
    page?: number;
    pageSize?: number;
    appId?: number;
    sortBy?: string;
    sortDir?: string;
  },
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

export async function createFunction(data: FunctionDefinition, options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/functions', {
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as FunctionDefinition;
}

export async function updateFunction(id: string, data: FunctionDefinition, options?: RequestOptions) {
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
  return unwrap(
    await client.POST('/api/admin/functions/{functionName}/test', {
      params: { path: { functionName: id } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  );
}

export async function publishFunction(id: string, options?: RequestOptions) {
  return apiPost<FunctionDefinition>(`/api/admin/functions/${encodeURIComponent(id)}/publish`, undefined, options);
}

export async function deprecateFunction(id: string, options?: RequestOptions) {
  return apiPost<FunctionDefinition>(`/api/admin/functions/${encodeURIComponent(id)}/deprecate`, undefined, options);
}
