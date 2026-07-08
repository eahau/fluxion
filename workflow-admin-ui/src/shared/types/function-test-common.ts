// shared/types/function-test-common.ts
// 跨 features 通用：函数测试相关类型 & 常量

export type FieldType =
  | 'string' | 'number' | 'integer' | 'boolean'
  | 'array' | 'object' | 'null' | 'unknown';

export interface FieldSchemaMeta {
  title?: string;
  description?: string;
  required?: boolean;
  enum?: any[];
  itemsType?: FieldType;
  propertiesCount?: number;
}

export interface SqlHistoryEntry {
  sql: string;
  result?: any;
  error?: string;
  executedAt: number;
  truncated?: boolean;
}

export type FunctionDomain = 'db' | 'redis' | 'script' | 'http' | 'builtin' | 'custom';
export type ResultType = 'list' | 'one' | 'count';

export const SQL_HISTORY_KEY = 'fluxion:fn:sqlHistory';
export const MAX_SQL_HISTORY = 15;
export const RESULT_MAX_JSON_BYTES = 200_000;
export const MAX_DISPLAY_ROWS = 50;
