import { apiGet, apiPost, apiPut, apiDelete } from './request';
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
  return apiGet<API.PageResponse<SchemaDefinition>>('/api/admin/schemas', params, options);
}

export async function getSchema(id: string, options?: RequestOptions) {
  const result = await apiGet<SchemaDefinition>(`/api/admin/schemas/${id}`, undefined, options);
  if (result) {
    const key = String(result.id || (result as any).schemaName || id);
    schemaMemoryCache.set(key, result);
  }
  return result;
}

export async function createSchema(data: SchemaDefinition, options?: RequestOptions) {
  const result = await apiPost<SchemaDefinition>('/api/admin/schemas', data, options);
  if (result) {
    const key = String(result.id || (result as any).schemaName);
    schemaMemoryCache.set(key, result);
  }
  return result;
}

export async function updateSchema(id: string, data: SchemaDefinition, options?: RequestOptions) {
  const result = await apiPut<SchemaDefinition>(`/api/admin/schemas/${id}`, data, options);
  if (result) {
    const key = String(result.id || (result as any).schemaName || id);
    schemaMemoryCache.set(key, result);
  }
  return result;
}

export async function deleteSchema(id: string, options?: RequestOptions) {
  const result = await apiDelete<void>(`/api/admin/schemas/${id}`, options);
  schemaMemoryCache.delete(String(id));
  return result;
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
  return apiGet<string[]>('/api/admin/schemas/tables', dataSource ? { dataSource } : undefined, options);
}

export async function getTablesWithColumns(dataSource?: string, options?: RequestOptions) {
  return apiGet<TableMetadata[]>(
    '/api/admin/schemas/tables/detail',
    dataSource ? { dataSource } : undefined,
    options,
  );
}

export async function getTableColumns(table: string, dataSource?: string, options?: RequestOptions) {
  return apiGet<ColumnMetadata[]>(
    `/api/admin/schemas/tables/${encodeURIComponent(table)}/columns`,
    dataSource ? { dataSource } : undefined,
    options,
  );
}

export async function getTableForeignKeys(table: string, dataSource?: string, options?: RequestOptions) {
  return apiGet<ForeignKeysResult>(
    `/api/admin/schemas/tables/${encodeURIComponent(table)}/foreign-keys`,
    dataSource ? { dataSource } : undefined,
    options,
  );
}

export async function getTableJsonSchema(table: string, dataSource?: string, options?: RequestOptions) {
  return apiGet<Record<string, any>>(
    `/api/admin/schemas/tables/${encodeURIComponent(table)}/json-schema`,
    dataSource ? { dataSource } : undefined,
    options,
  );
}

export async function getSchemaVersions(id: string, options?: RequestOptions) {
  return apiGet<SchemaDefinition[]>(`/api/admin/schemas/${id}/versions`, undefined, options);
}

export async function previewSql(
  sql: string,
  params?: Record<string, any>,
  dataSource?: string,
  options?: RequestOptions,
) {
  return apiPost<{ columns: string[]; rows: Record<string, any>[] }>(
    '/api/admin/schemas/sql-preview',
    {
      sql,
      params,
      dataSource,
    },
    options,
  );
}
