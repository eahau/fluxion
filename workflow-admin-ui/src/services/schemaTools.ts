import { client, unwrap, silentHeaders } from '@/sdk';
import type { schemas, RequestOptions } from '@/sdk';

export type SchemaCompatCheckRequest = schemas['SchemaCompatCheckRequest'];
export type SchemaApplyMappingRequest = schemas['SchemaApplyMappingRequest'];
export type CompatResult = schemas['CompatResult'];

export async function checkSchemaCompatibility(
  req: SchemaCompatCheckRequest,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/schema-tools/compatibility-check', {
      body: req as any,
      headers: silentHeaders(options),
    }),
  ) as CompatResult;
}

export async function applySchemaMapping(
  req: SchemaApplyMappingRequest,
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/schema-tools/apply-mapping', {
      body: req as any,
      headers: silentHeaders(options),
    }),
  ) as Record<string, any>;
}
