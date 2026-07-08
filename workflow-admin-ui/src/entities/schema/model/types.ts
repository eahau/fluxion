import type { SchemaTypeTag, SchemaDefinition } from '@/types/api';
export type { SchemaTypeTag, SchemaDefinition };

/** Schema 格式标识 */
export type SchemaFormat = 'json-schema' | 'protobuf' | 'avro';

export interface SchemaField {
  name: string;
  type: string;
  required: boolean;
  description?: string;
  format?: string;
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  enum?: any[];
  items?: any;
  properties?: Record<string, any>;
  schemaRef?: string;
}

export interface SchemaBrief {
  id: string;
  name: string;
  description?: string;
  type?: SchemaTypeTag;
  version?: number;
  updatedAt?: number;
}
