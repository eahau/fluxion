// features/redis-function-test/lib/utils.ts
// Redis Feature 专属：入参 Schema 特征检测 + structured/raw 切换

import type { SchemaObject } from '@/shared/lib/json-schema';
import { isRedisCommandSchema as baseCheck } from '@/shared/lib/json-schema';
import { REDIS_STRUCTURED_FIELDS as _sharedFields } from '@/utils/schemaUiResolver';

export const REDIS_STRUCTURED_FIELDS: string[] = Array.isArray((_sharedFields as any)) ? _sharedFields : [
  'command', 'key', 'keys', 'field', 'fields', 'value', 'values',
  'member', 'members', 'score', 'scores', 'min', 'max', 'start', 'stop', 'offset', 'count',
  'index', 'length', 'expire', 'ttl', 'nx', 'xx', 'get', 'incr',
];

export const isRedisCommandSchema = (schema: SchemaObject | null): boolean => baseCheck(schema);

export const isRedisDomain = (domain?: string, schema?: SchemaObject | null): boolean => {
  if (domain && String(domain).toLowerCase() === 'redis') return true;
  return !!schema && isRedisCommandSchema(schema);
};
