import { client, unwrap, silentHeaders } from '@/sdk';
import type { RequestOptions } from './request';
import type { schemas } from '@/sdk';

export type Resource = schemas['Resource'];
export type ResourceCreateRequest = schemas['ResourceCreateRequest'];
export type ResourceUpdateRequest = schemas['ResourceUpdateRequest'];

export async function listResources(
  params?: { resourceType?: string },
  options?: RequestOptions,
) {
  return unwrap(
    await client.GET('/api/admin/resources', {
      params: { query: params ?? {} },
      headers: silentHeaders(options),
    }),
  ) as Resource[];
}

export async function getResource(id: number, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/resources/{id}', {
      params: { path: { id } },
      headers: silentHeaders(options),
    }),
  ) as Resource;
}

export async function createResource(data: ResourceCreateRequest, options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/resources', {
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as Resource;
}

export async function updateResource(
  id: number,
  data: ResourceUpdateRequest,
  options?: RequestOptions,
) {
  return unwrap(
    await client.PUT('/api/admin/resources/{id}', {
      params: { path: { id } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as Resource;
}

export async function deleteResource(id: number, options?: RequestOptions) {
  unwrap(
    await client.DELETE('/api/admin/resources/{id}', {
      params: { path: { id } },
      headers: silentHeaders(options),
    }),
  );
  return undefined as void;
}

export async function testResource(
  data: { resourceType: string; configJson: Record<string, unknown>; resourceId?: number },
  options?: RequestOptions,
): Promise<{ ok: boolean; message: string; latencyMs?: number }> {
  const start = Date.now();
  try {
    if (data.resourceId) {
      const res = await client.POST('/api/admin/resources/{id}/test', {
        params: { path: { id: data.resourceId } },
        body: { configJson: data.configJson } as any,
        headers: silentHeaders(options),
      });
      return { ok: true, message: '连接成功', latencyMs: Date.now() - start, ...(res as any) };
    }
    const res = await client.POST('/api/admin/resources/test-connection', {
      body: { resourceType: data.resourceType, configJson: data.configJson } as any,
      headers: silentHeaders(options),
    });
    return { ok: true, message: '连接成功', latencyMs: Date.now() - start, ...(res as any) };
  } catch (e: any) {
    const status = e?.response?.status ?? e?.status;
    const msg = e?.message || String(e);
    if (status === 404 || msg.includes('404') || msg.includes('not found') || msg.includes('Not Found')) {
      return {
        ok: false,
        message: '后端「测试连接」接口尚未实现（/api/admin/resources/test-connection 或 /{id}/test）。请先手工校验 host/port 等配置，保存后即可正常使用。',
      };
    }
    return {
      ok: false,
      message: e?.response?.data?.message || msg || '连接失败',
      latencyMs: Date.now() - start,
    };
  }
}

