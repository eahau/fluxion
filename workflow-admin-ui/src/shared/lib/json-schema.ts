// shared/lib/json-schema.ts
// 跨 features 通用：JSON Schema 处理工具（所有功能都要处理 paramSchema）

import type { FieldType, FieldSchemaMeta } from '../types/function-test-common';
import { NODE_PARAM_SCHEMAS } from '@/constants/nodeParamSchemas';

export type SchemaObject = Record<string, any>;

export function parseSchema(raw: unknown): SchemaObject | null {
  if (!raw) return null;
  if (typeof raw === 'string') {
    try { return JSON.parse(raw); } catch { return null; }
  }
  if (typeof raw === 'object') return raw as SchemaObject;
  return null;
}

export function sanitizeSchemaForRJSF(schema: unknown): SchemaObject {
  if (!schema || typeof schema !== 'object') return schema as any;
  const result = { ...(schema as SchemaObject) };

  if (result.properties && typeof result.properties === 'object') {
    const patched: Record<string, any> = {};
    let changed = false;
    for (const [key, prop] of Object.entries(result.properties)) {
      const p = prop as Record<string, any>;
      if (p && typeof p === 'object' && !p.type && !p.$ref && !p.oneOf && !p.anyOf && !p.allOf) {
        patched[key] = { ...p, type: 'string' };
        changed = true;
      } else {
        patched[key] = p;
      }
    }
    if (changed) result.properties = patched;
  }

  if (result.items && typeof result.items === 'object') {
    result.items = sanitizeSchemaForRJSF(result.items);
  }

  if (result.properties && typeof result.properties === 'object') {
    const deepPatched: Record<string, any> = {};
    let deepChanged = false;
    for (const [key, prop] of Object.entries(result.properties)) {
      const p = prop as Record<string, any>;
      if (p?.items && typeof p.items === 'object') {
        const sanitized = sanitizeSchemaForRJSF(p.items);
        if (sanitized !== p.items) {
          deepPatched[key] = { ...p, items: sanitized };
          deepChanged = true;
        } else {
          deepPatched[key] = p;
        }
      } else {
        deepPatched[key] = p;
      }
    }
    if (deepChanged) result.properties = { ...result.properties, ...deepPatched };
  }

  return result;
}

export const hasSqlField = (schema: SchemaObject | null): boolean => {
  if (!schema) return false;
  const props = (schema as any)?.properties;
  return props && typeof props.sql === 'object';
};

export const isRedisCommandSchema = (schema: SchemaObject | null): boolean => {
  if (!schema) return false;
  const props = (schema as any)?.properties;
  return props && typeof props.command === 'object' && typeof props.raw === 'object';
};

// —— Schema 字段 introspection（供 Condition / DB / Generic 等多种 feature 使用）——

export function extractSchemaFieldPaths(schema: SchemaObject): string[] {
  const paths: string[] = [];
  const walk = (sch: any, prefix: string) => {
    if (!sch || typeof sch !== 'object') return;
    if (sch.properties && typeof sch.properties === 'object') {
      for (const [k, v] of Object.entries(sch.properties as Record<string, any>)) {
        const nextPath = prefix ? `${prefix}.${k}` : k;
        paths.push(nextPath);
        walk(v, nextPath);
      }
    }
    if (sch.items) {
      walk(sch.items, prefix ? `${prefix}[*]` : '[*]');
    }
  };
  walk(schema, '');
  return paths;
}

export function extractSchemaFieldTypes(schema: SchemaObject): Record<string, FieldType> {
  const out: Record<string, FieldType> = {};
  const walk = (sch: any, prefix: string) => {
    if (!sch || typeof sch !== 'object') return;
    if (sch.properties && typeof sch.properties === 'object') {
      for (const [k, v] of Object.entries(sch.properties as Record<string, any>)) {
        const nextPath = prefix ? `${prefix}.${k}` : k;
        const t: FieldType = Array.isArray(v.type) ? (v.type[0] as FieldType) : (v.type as FieldType) || 'unknown';
        out[nextPath] = t;
        walk(v, nextPath);
      }
    }
    if (sch.items) {
      const nextPrefix = prefix ? `${prefix}[*]` : '[*]';
      const t: FieldType = Array.isArray(sch.items.type) ? sch.items.type[0] : sch.items.type || 'unknown';
      out[nextPrefix] = t;
      walk(sch.items, nextPrefix);
    }
  };
  walk(schema, '');
  return out;
}

export function extractSchemaFieldMeta(schema: SchemaObject): Record<string, FieldSchemaMeta> {
  const out: Record<string, FieldSchemaMeta> = {};
  const walk = (sch: any, prefix: string, requiredSet: Set<string>) => {
    if (!sch || typeof sch !== 'object') return;
    const props = sch.properties && typeof sch.properties === 'object' ? sch.properties : {};
    for (const [k, v] of Object.entries(props as Record<string, any>)) {
      const nextPath = prefix ? `${prefix}.${k}` : k;
      const vObj = v as Record<string, any>;
      out[nextPath] = {
        title: vObj.title,
        description: vObj.description,
        required: requiredSet?.has(k),
        enum: vObj.enum,
        itemsType: Array.isArray(vObj.items?.type) ? (vObj.items.type[0] as FieldType) : vObj.items?.type,
        propertiesCount:
          vObj.properties && typeof vObj.properties === 'object'
            ? Object.keys(vObj.properties).length
            : undefined,
      };
      const innerReq = Array.isArray(vObj.required) ? new Set(vObj.required) : new Set<string>();
      walk(v, nextPath, innerReq);
    }
    if (sch.items) {
      const nextPrefix = prefix ? `${prefix}[*]` : '[*]';
      const innerReq = Array.isArray(sch.items.required) ? new Set(sch.items.required) : new Set<string>();
      walk(sch.items, nextPrefix, innerReq);
    }
  };
  const rootReq = Array.isArray(schema.required) ? new Set(schema.required) : new Set<string>();
  walk(schema, '', rootReq);
  return out;
}

export function applyOperatorLabels(schema: SchemaObject): SchemaObject {
  const patch = (obj: any): any => {
    if (!obj || typeof obj !== 'object') return obj;
    const copy = Array.isArray(obj) ? [...obj] : { ...obj };
    for (const [k, v] of Object.entries(copy)) {
      if (k === 'operator' && v && typeof v === 'object' && !Array.isArray(v)) {
        const vo = v as Record<string, any>;
        if (!vo.enum) {
          vo.enum = [
            'eq', 'ne', 'gt', 'gte', 'lt', 'lte',
            'contains', 'notContains', 'startsWith', 'endsWith',
            'in', 'notIn', 'between',
            'isNull', 'isNotNull', 'isEmpty', 'isNotEmpty',
          ];
        }
        copy[k] = vo;
      } else {
        copy[k] = patch(v);
      }
    }
    return copy;
  };
  return patch(schema);
}

export function mergeLocalConditionSchema(
  apiSchema: Record<string, any>,
  key: string,
): SchemaObject {
  const localKey = (key === 'builtin:filter' ? 'FILTER' : key === 'builtin:conditionBranch' ? 'CONDITION_BRANCH' : key) as 'FILTER' | 'CONDITION_BRANCH';
  const localSchema: any = NODE_PARAM_SCHEMAS?.[localKey];
  if (!localSchema?.properties) return apiSchema as SchemaObject;
  const result = { ...(apiSchema || {}) };
  if (!result.type) result.type = localSchema.type;
  if (!result.required && localSchema.required) result.required = localSchema.required;

  const apiProps = { ...(result.properties || {}) };
  for (const [k, localProp] of Object.entries(localSchema.properties as Record<string, any>)) {
    if (!apiProps[k]) { apiProps[k] = localProp; continue; }
    const apiProp = apiProps[k] as Record<string, any>;
    if (localProp.items?.properties && apiProp.items) {
      const apiItems = { ...apiProp.items };
      const localItems = localProp.items;
      if (!apiItems.properties) apiItems.properties = {};
      for (const [ik, il] of Object.entries(localItems.properties as Record<string, any>)) {
        if (!apiItems.properties[ik]) {
          apiItems.properties = { ...apiItems.properties, [ik]: il };
          continue;
        }
        if (il.items?.properties && apiItems.properties[ik]?.items) {
          const deepApi = { ...apiItems.properties[ik] };
          const deepItems = { ...(deepApi.items || {}) };
          if (!deepItems.properties) deepItems.properties = {};
          for (const [dk, dl] of Object.entries(il.items.properties as Record<string, any>)) {
            if (!deepItems.properties[dk]) {
              deepItems.properties = { ...deepItems.properties, [dk]: dl };
            }
          }
          deepApi.items = deepItems;
          apiItems.properties = { ...apiItems.properties, [ik]: deepApi };
        }
      }
      apiProps[k] = { ...apiProp, items: apiItems };
    }
  }
  result.properties = apiProps;
  return result;
}

export function generateSampleFromSchema(schema: SchemaObject): Record<string, any> {
  const gen = (sch: any): any => {
    if (!sch || typeof sch !== 'object') return null;
    const type = Array.isArray(sch.type) ? sch.type[0] : sch.type;
    if (sch.example !== undefined) return sch.example;
    if (sch.default !== undefined) return sch.default;
    if (sch.enum && sch.enum.length > 0) return sch.enum[0];
    switch (type) {
      case 'string':
        if (sch.format === 'date') return '2026-01-01';
        if (sch.format === 'date-time') return '2026-01-01T10:00:00.000Z';
        if (sch.format === 'email') return 'user@example.com';
        if (sch.format === 'uri') return 'https://example.com';
        return sch.minLength ? 'a'.repeat(Math.min(sch.minLength, 8)) : 'sample';
      case 'number':
      case 'integer': return sch.minimum ?? 0;
      case 'boolean': return false;
      case 'array': {
        const size = sch.minItems ?? 0;
        const itemSample = sch.items ? gen(sch.items) : null;
        const arr: any[] = [];
        for (let i = 0; i < size; i++) arr.push(itemSample);
        return arr;
      }
      case 'object': {
        const out: Record<string, any> = {};
        const props = sch.properties && typeof sch.properties === 'object' ? sch.properties : {};
        for (const [k, v] of Object.entries(props)) out[k] = gen(v);
        return out;
      }
      case 'null': return null;
      default:
        if (sch.properties) {
          const out: Record<string, any> = {};
          const props = sch.properties && typeof sch.properties === 'object' ? sch.properties : {};
          for (const [k, v] of Object.entries(props)) out[k] = gen(v);
          return out;
        }
        return null;
    }
  };
  return gen(schema);
}
