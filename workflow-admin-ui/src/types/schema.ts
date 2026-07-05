/**
 * Schema 相关类型
 * @deprecated 请直接从 '@/types/api' 导入
 */
export type { SchemaTypeTag, SchemaDefinition } from './api';

/** Schema 格式标识 */
export type SchemaFormat = 'json-schema' | 'protobuf' | 'avro';

export interface SchemaField {
  name: string;
  type: string;
  required: boolean;
  description?: string;
  /** JSON Schema format（如 email、date、date-time、uri 等） */
  format?: string;
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  enum?: any[];
  items?: any;
  properties?: Record<string, any>;
  /** 引用已注册的 Schema 名称（仅 type=object 时生效） */
  schemaRef?: string;
}
