import { client, unwrap, silentHeaders } from '@/sdk';
import type { schemas, RequestOptions } from '@/sdk';

export type FunctionSetDTO = schemas['FunctionSetDTO'];
export type PublishFunctionSetRequest = schemas['PublishFunctionSetRequest'];
export type CanComposeAfterRequest = schemas['CanComposeAfterRequest'];
export type CompositionResult = schemas['CompositionResult'];

export async function listFunctionSets(
  params?: { appId?: number; scope?: 'PUBLIC' | 'PRIVATE' },
  options?: RequestOptions,
) {
  return unwrap(
    await client.GET('/api/admin/function-sets', {
      params: { query: params ?? {} },
      headers: silentHeaders(options),
    }),
  ) as FunctionSetDTO[];
}

export async function publishFunctionSet(
  definitionId: number,
  req?: PublishFunctionSetRequest,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/function-sets/{definitionId}/publish', {
      params: { path: { definitionId } },
      body: req as any,
      headers: silentHeaders(options),
    }),
  ) as FunctionSetDTO;
}

export async function unpublishFunctionSet(
  definitionId: number,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/function-sets/{definitionId}/unpublish', {
      params: { path: { definitionId } },
      headers: silentHeaders(options),
    }),
  ) as FunctionSetDTO;
}

export async function canComposeAfter(
  req: CanComposeAfterRequest,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/function-sets/can-compose-after', {
      body: req as any,
      headers: silentHeaders(options),
    }),
  ) as CompositionResult;
}
