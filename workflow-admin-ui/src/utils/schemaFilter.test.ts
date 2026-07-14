import { filterOutBuiltinSchemas, isBuiltinSchema } from './schemaFilter';
import type { SchemaDefinition } from '@/types/api';

function makeSchema(partial: Partial<SchemaDefinition> = {}): SchemaDefinition {
  return {
    schemaName: 'custom:user:param',
    name: 'custom:user:param',
    schemaFormat: 'json-schema',
    schemaType: 'INPUT',
    scope: 'PRIVATE',
    description: '测试 Schema',
    ...partial,
  } as SchemaDefinition;
}

describe('schemaFilter（函数测试输入 Schema 过滤规则）', () => {
  it('Case1: builtin:* 开头的系统 Schema 全部被过滤', () => {
    const input: SchemaDefinition[] = [
      makeSchema({ schemaName: 'builtin:Execute:output', name: 'builtin:Execute:output', scope: 'PLATFORM', schemaType: 'OUTPUT' }),
      makeSchema({ schemaName: 'builtin:groovy:Script:param', name: 'builtin:groovy:Script:param', scope: 'PLATFORM' }),
      makeSchema({ schemaName: 'builtin:redis:Command:param', name: 'builtin:redis:Command:param', scope: 'PLATFORM' }),
      makeSchema({ schemaName: 'custom:user:create:input', scope: 'PRIVATE' }),
    ];
    const result = filterOutBuiltinSchemas(input);
    expect(result.map((s) => s.schemaName)).toEqual(['custom:user:create:input']);
    expect(result).toHaveLength(1);
  });

  it('Case2: 自定义 Schema（含用户冻结的平台 Schema）全部保留，不过滤', () => {
    const input: SchemaDefinition[] = [
      makeSchema({ schemaName: 'myapp:order:create:input', scope: 'PRIVATE' }),
      makeSchema({ schemaName: 'common:user:profile', scope: 'PLATFORM' }),
      makeSchema({ schemaName: 'demo:login:request', scope: 'PRIVATE' }),
    ];
    const result = filterOutBuiltinSchemas(input);
    expect(result.map((s) => s.schemaName)).toEqual([
      'myapp:order:create:input',
      'common:user:profile',
      'demo:login:request',
    ]);
    expect(result).toHaveLength(3);
  });

  it('Case3: 空数据 / null / undefined 输入安全返回空数组，不抛异常', () => {
    expect(filterOutBuiltinSchemas([])).toEqual([]);
    expect(filterOutBuiltinSchemas(null as any)).toEqual([]);
    expect(filterOutBuiltinSchemas(undefined as any)).toEqual([]);
  });

  it('Bonus: isBuiltinSchema 边界判断（前后空格 + name 回退 + 空值）', () => {
    expect(isBuiltinSchema(makeSchema({ schemaName: '  builtin:filter:param  ' }))).toBe(true);
    expect(isBuiltinSchema(makeSchema({ schemaName: undefined as any, name: 'builtin:xx' }))).toBe(true);
    expect(isBuiltinSchema(null)).toBe(false);
    expect(isBuiltinSchema(undefined)).toBe(false);
  });
});
