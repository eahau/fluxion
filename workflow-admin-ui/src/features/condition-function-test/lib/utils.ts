// features/condition-function-test/lib/utils.ts
// Condition/Filter Feature 专属纯函数（私有，其他 Feature 不引）

import {
  parseSchema,
  extractSchemaFieldPaths, extractSchemaFieldTypes, extractSchemaFieldMeta,
  generateSampleFromSchema, sanitizeSchemaForRJSF, applyOperatorLabels, mergeLocalConditionSchema,
  type SchemaObject,
} from '@/shared/lib/json-schema';
import type { FieldSchemaMeta, FieldType } from '@/shared/types/function-test-common';
import { NODE_PARAM_SCHEMAS } from '@/constants/nodeParamSchemas';

export const VALUE_LESS_OPERATORS = new Set([
  'isNull', 'isNotNull', 'isEmpty', 'isNotEmpty', 'exists', 'notExists',
]);

export function isConditionFunction(functionId: string): boolean {
  return functionId === 'builtin:filter' || functionId === 'builtin:conditionBranch';
}

export function validateConditionRules(data: unknown): string | null {
  const checkRules = (rules: any[], context: string): string | null => {
    if (!Array.isArray(rules)) return null;
    for (let i = 0; i < rules.length; i++) {
      const r = rules[i];
      if (!r || typeof r !== 'object') continue;
      if (!r.field) return `${context} 第 ${i + 1} 条规则：字段名不能为空`;
      if (!r.operator) return `${context} 第 ${i + 1} 条规则：操作符不能为空`;
      if (!VALUE_LESS_OPERATORS.has(r.operator) && (r.value == null || r.value === '')) {
        return `${context} 第 ${i + 1} 条规则：操作符 ${r.operator} 需要填写值`;
      }
    }
    return null;
  };
  if (!data || typeof data !== 'object') return null;
  const d = data as any;
  if (Array.isArray(d.rules)) {
    const err = checkRules(d.rules, '过滤规则');
    if (err) return err;
  }
  if (Array.isArray(d.conditions)) {
    for (let ci = 0; ci < d.conditions.length; ci++) {
      const cond = d.conditions[ci];
      if (Array.isArray(cond?.rules)) {
        const err = checkRules(cond.rules, `条件 #${ci + 1}`);
        if (err) return err;
      }
    }
  }
  return null;
}

export function normalizeRuleValues(obj: any): any {
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return obj;
  const result = { ...obj };
  if ('field' in result && 'operator' in result && result.value == null) {
    result.value = '';
  }
  for (const key of Object.keys(result)) {
    const val = result[key];
    if (Array.isArray(val)) {
      result[key] = val.map((item) => normalizeRuleValues(item));
    } else if (val && typeof val === 'object' && !Array.isArray(val)) {
      result[key] = normalizeRuleValues(val);
    }
  }
  return result;
}

// 重新导出 shared 的 schema 工具（供此 feature 内部统一 import）
export {
  parseSchema,
  extractSchemaFieldPaths, extractSchemaFieldTypes, extractSchemaFieldMeta,
  generateSampleFromSchema, sanitizeSchemaForRJSF, applyOperatorLabels, mergeLocalConditionSchema,
};
export type { SchemaObject, FieldSchemaMeta, FieldType };
