import { client, unwrap, silentHeaders } from '@/sdk';
import { apiPost } from './request';
import type { SchemaDefinition } from '@/types/schema';
import type { RequestOptions } from './request';

const schemaMemoryCache = new Map<string, SchemaDefinition>();

export function getCachedSchemaMap(): Map<string, SchemaDefinition> {
  return schemaMemoryCache;
}

export async function getSchemas(
  params?: { keyword?: string; schemaType?: string; page?: number; pageSize?: number },
  options?: RequestOptions,
) {
  return unwrap(
    await client.GET('/api/admin/schemas', {
      params: { query: params ?? {} },
      headers: silentHeaders(options),
    }),
  ) as unknown as API.PageResponse<SchemaDefinition>;
}

export async function getSchema(id: string, options?: RequestOptions) {
  const result = unwrap(
    await client.GET('/api/admin/schemas/{schemaName}', {
      params: { path: { schemaName: id } },
      headers: silentHeaders(options),
    }),
  ) as SchemaDefinition;
  if (result) {
    const key = String(result.id || (result as any).schemaName || id);
    schemaMemoryCache.set(key, result);
  }
  return result;
}

export async function createSchema(data: SchemaDefinition, options?: RequestOptions) {
  const result = unwrap(
    await client.POST('/api/admin/schemas', {
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as SchemaDefinition;
  if (result) {
    const key = String(result.id || (result as any).schemaName);
    schemaMemoryCache.set(key, result);
  }
  return result;
}

export async function updateSchema(id: string, data: SchemaDefinition, options?: RequestOptions) {
  const result = unwrap(
    await client.PUT('/api/admin/schemas/{schemaName}', {
      params: { path: { schemaName: id } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as SchemaDefinition;
  if (result) {
    const key = String(result.id || (result as any).schemaName || id);
    schemaMemoryCache.set(key, result);
  }
  return result;
}

export async function deleteSchema(id: string, options?: RequestOptions) {
  unwrap(
    await client.DELETE('/api/admin/schemas/{schemaName}', {
      params: { path: { schemaName: id } },
      headers: silentHeaders(options),
    }),
  );
  schemaMemoryCache.delete(String(id));
  return undefined as void;
}

export interface ColumnMetadata {
  name: string;
  dataType?: string;
  sqlType?: number;
  size?: number;
  nullable: boolean;
  defaultValue?: string;
  comment?: string;
  ordinalPosition?: number;
  autoIncrement: boolean;
}

export interface TableMetadata {
  name: string;
  columns: ColumnMetadata[];
}

export interface ForeignKeyMetadata {
  fkName?: string;
  sourceTable: string;
  sourceColumn: string;
  targetTable: string;
  targetColumn: string;
  keySeq: number;
}

export interface ForeignKeysResult {
  incoming: ForeignKeyMetadata[];
  outgoing: ForeignKeyMetadata[];
}

export async function getTables(dataSource?: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/schemas/tables', {
      params: { query: dataSource ? { dataSource } : {} },
      headers: silentHeaders(options),
    }),
  ) as unknown as string[];
}

export async function getTablesWithColumns(dataSource?: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/schemas/tables/detail', {
      params: { query: dataSource ? { dataSource } : {} },
      headers: silentHeaders(options),
    }),
  ) as TableMetadata[];
}

export async function getTableColumns(table: string, dataSource?: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/schemas/tables/{tableName}/columns', {
      params: { path: { tableName: table }, query: dataSource ? { dataSource } : {} },
      headers: silentHeaders(options),
    }),
  ) as ColumnMetadata[];
}

export async function getTableForeignKeys(table: string, dataSource?: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/schemas/tables/{tableName}/foreign-keys', {
      params: { path: { tableName: table }, query: dataSource ? { dataSource } : {} },
      headers: silentHeaders(options),
    }),
  ) as ForeignKeysResult;
}

export async function getTableJsonSchema(table: string, dataSource?: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/schemas/tables/{tableName}/json-schema', {
      params: { path: { tableName: table }, query: dataSource ? { dataSource } : {} },
      headers: silentHeaders(options),
    }),
  ) as Record<string, any>;
}

export async function getSchemaVersions(id: string, options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/schemas/{schemaName}/versions', {
      params: { path: { schemaName: id } },
      headers: silentHeaders(options),
    }),
  ) as SchemaDefinition[];
}

export async function previewSql(
  sql: string,
  params?: Record<string, any>,
  dataSource?: string,
  options?: RequestOptions,
) {
  return apiPost<{ columns: string[]; rows: Record<string, any>[] }>(
    '/api/admin/schemas/sql-preview',
    { sql, params, dataSource },
    options,
  );
}
