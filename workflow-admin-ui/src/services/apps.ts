import { client, unwrap, silentHeaders } from '@/sdk';
import type { RequestOptions } from './request';
import type { schemas } from '@/sdk';

export type App = schemas['App'];
export type AppBinding = schemas['AppBinding'];
export type ResourceOption = schemas['ResourceOption'];
export type AppCreateRequest = schemas['AppCreateRequest'];
export type AppUpdateRequest = schemas['AppUpdateRequest'];
export type AppBindingCreateRequest = schemas['AppBindingCreateRequest'];
export type AppBindingUpdateRequest = schemas['AppBindingUpdateRequest'];

export async function listApps(options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/apps', {
      headers: silentHeaders(options),
    }),
  ) as App[];
}

export async function getApp(id: number, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/apps/{id}', {
      params: { path: { id } },
      headers: silentHeaders(options),
    }),
  ) as App;
}

export async function createApp(data: AppCreateRequest, options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/apps', {
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as App;
}

export async function updateApp(id: number, data: AppUpdateRequest, options?: RequestOptions) {
  return unwrap(
    await client.PUT('/api/admin/apps/{id}', {
      params: { path: { id } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as App;
}

export async function deleteApp(id: number, options?: RequestOptions) {
  unwrap(
    await client.DELETE('/api/admin/apps/{id}', {
      params: { path: { id } },
      headers: silentHeaders(options),
    }),
  );
  return undefined as void;
}

export async function listAppBindings(appId: number, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/apps/{id}/bindings', {
      params: { path: { id: appId } },
      headers: silentHeaders(options),
    }),
  ) as AppBinding[];
}

export async function createAppBinding(
  appId: number,
  data: AppBindingCreateRequest,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/apps/{id}/bindings', {
      params: { path: { id: appId } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as AppBinding;
}

export async function updateAppBinding(
  appId: number,
  bindingId: number,
  data: AppBindingUpdateRequest,
  options?: RequestOptions,
) {
  return unwrap(
    await client.PUT('/api/admin/apps/{id}/bindings/{bindingId}', {
      params: { path: { id: appId, bindingId } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as AppBinding;
}

export async function deleteAppBinding(
  appId: number,
  bindingId: number,
  options?: RequestOptions,
) {
  unwrap(
    await client.DELETE('/api/admin/apps/{id}/bindings/{bindingId}', {
      params: { path: { id: appId, bindingId } },
      headers: silentHeaders(options),
    }),
  );
  return undefined as void;
}

export async function listAppResourceOptions(
  appId: number,
  resourceType: string,
  options?: RequestOptions,
) {
  return unwrap(
    await client.GET('/api/admin/apps/{id}/resource-options', {
      params: { path: { id: appId }, query: { resourceType } },
      headers: silentHeaders(options),
    }),
  ) as ResourceOption[];
}
