import type { SchemaField } from '@/types/schema';
import Ajv from 'ajv';
import addFormats from 'ajv-formats';
/** $ref 前缀，用于区分本地 JSON Schema ref 与外部 Schema 注册表引用 */
export const SCHEMA_REF_PREFIX = 'schema:';

export function fieldsToJsonSchema(fields: SchemaField[]): any {
  const properties: Record<string, any> = {};
  const required: string[] = [];

  fields.forEach((field) => {
    // 若字段引用了已注册的 Schema，直接输出 $ref，不写入 type / properties
    if (field.schemaRef) {
      properties[field.name] = {
        $ref: `${SCHEMA_REF_PREFIX}${field.schemaRef}`,
        description: field.description,
      };
      if (field.required) required.push(field.name);
      return;
    }

    const prop: any = { type: field.type, description: field.description };
    if (field.format) prop.format = field.format;
    if (field.minimum !== undefined) prop.minimum = field.minimum;
    if (field.maximum !== undefined) prop.maximum = field.maximum;
    if (field.minLength !== undefined) prop.minLength = field.minLength;
    if (field.maxLength !== undefined) prop.maxLength = field.maxLength;
    if (field.pattern) prop.pattern = field.pattern;
    if (field.enum) prop.enum = field.enum;
    if (field.type === 'array' && field.items) {
      prop.items = field.items;
    }
    if (field.type === 'object' && field.properties) {
      prop.properties = field.properties;
    }
    properties[field.name] = prop;
    if (field.required) required.push(field.name);
  });

  return { type: 'object', properties, required };
}

export function jsonSchemaToFields(schema: any): SchemaField[] {
  const properties = schema?.properties || {};
  const required = schema?.required || [];
  return Object.entries(properties).map(([name, prop]: [string, any]) => {
    // 检查是否为外部 Schema 引用（$ref: "schema:XXX"）
    const ref: string | undefined = prop?.$ref;
    if (ref?.startsWith(SCHEMA_REF_PREFIX)) {
      return {
        name,
        type: 'object',
        required: required.includes(name),
        description: prop.description,
        schemaRef: ref.slice(SCHEMA_REF_PREFIX.length),
      };
    }

    return {
      name,
      type: prop.type || 'string',
      required: required.includes(name),
      description: prop.description,
      format: prop.format,
      minimum: prop.minimum,
      maximum: prop.maximum,
      minLength: prop.minLength,
      maxLength: prop.maxLength,
      pattern: prop.pattern,
      enum: prop.enum,
      items: prop.items,
      properties: prop.properties,
    };
  });
}

/**
 * 递归提取 JSON Schema 中所有直接出现的 schema:xxx 引用名（不去重展开，只是收集字符串）。
 * 会进入 properties / items / allOf / anyOf / oneOf / not / $defs / definitions 等嵌套结构。
 */
export function extractSchemaRefs(schema: any): string[] {
  const set = new Set<string>();
  const seenObj = new WeakSet();
  const walk = (node: any) => {
    if (!node || typeof node !== 'object') return;
    if (seenObj.has(node)) return;
    seenObj.add(node);
    if (Array.isArray(node)) {
      node.forEach(walk);
      return;
    }
    if (typeof node.$ref === 'string' && node.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      set.add(node.$ref.slice(SCHEMA_REF_PREFIX.length));
    }
    Object.values(node).forEach(walk);
  };
  walk(schema);
  return Array.from(set);
}

export interface FlatFieldInfo {
  /** 点路径，如 data.list[].itemId */
  path: string;
  /** 短字段名（最后一段） */
  name: string;
  /** JSON Schema type，数组时为 items.type，有引用时显示 `ref:XXX` */
  type: string;
  format?: string;
  required: boolean;
  description?: string;
  /** 引用的 Schema 名，若当前节点是引用 */
  schemaRef?: string;
  /** 数组元素类型，若 type === 'array' */
  itemType?: string;
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  enum?: (string | number)[];
}

/**
 * 只提取 JSON Schema 的「直接顶层字段」（1 层），用于 hover 预览卡片，避免把嵌套都展开成一长串路径。
 * - 支持：顶层 object(properties) / 顶层 array(items) / 顶层 allOf|anyOf|oneOf 组合 / 顶层本身是引用
 * - 每个字段若本身是「嵌套 object / ref / array 含复杂 items / 含组合关键字」，会附带 innerSchemaForPreview，
 *   上层 UI 可把该字段包成 Popover 再递归预览。
 * - 循环引用受 refVisited 保护，引用不会无限展开成自身字段，refVisited 从调用方传入。
 */
export interface TopLevelField {
  name: string;
  path: string;
  type: string;
  format?: string;
  required: boolean;
  description?: string;
  schemaRef?: string;
  itemType?: string;
  /** 1-2 个约束摘要，供预览在类型 Tag 后面展示：如 "1-99"、"枚举:3项"、"正则" */
  constraints?: string[];
  /** 若该字段可递归预览，这里是传给 collectTopLevelFields 的下一层 schema（引用会被预先 resolve） */
  innerSchemaForPreview?: any;
}

const buildConstraintTags = (prop: any): string[] => {
  if (!prop || typeof prop !== 'object') return [];
  const out: string[] = [];
  if (Array.isArray(prop.enum) && prop.enum.length) {
    out.push(`枚举:${prop.enum.length}项`);
  }
  if (typeof prop.pattern === 'string' && prop.pattern.length) {
    out.push('正则');
  }
  if (typeof prop.minimum === 'number' || typeof prop.maximum === 'number') {
    const min = prop.minimum === undefined ? '-∞' : String(prop.minimum);
    const max = prop.maximum === undefined ? '+∞' : String(prop.maximum);
    out.push(`${min}~${max}`);
  }
  if (typeof prop.minLength === 'number' || typeof prop.maxLength === 'number') {
    const mn = prop.minLength === undefined ? 0 : prop.minLength;
    const mx = prop.maxLength === undefined ? '∞' : prop.maxLength;
    out.push(`长${mn}-${mx}`);
  }
  return out.slice(0, 3);
};

const resolveIfRef = (node: any, refSchemaMap: Map<string, { schemaJson?: string | null }>, refVisited: Set<string>) => {
  if (node && typeof node === 'object' && typeof node.$ref === 'string' && node.$ref.startsWith(SCHEMA_REF_PREFIX)) {
    const refName = node.$ref.slice(SCHEMA_REF_PREFIX.length);
    if (refVisited.has(refName)) return null;
    const target = refSchemaMap.get(refName);
    if (!target) return null;
    let inner: any = null;
    try {
      inner = target.schemaJson ? JSON.parse(target.schemaJson) : null;
    } catch {
      inner = null;
    }
    if (!inner) return null;
    refVisited.add(refName);
    return inner;
  }
  return null;
};

export function collectTopLevelFields(
  rootSchema: any,
  refSchemaMap: Map<string, { schemaJson?: string | null }>,
  basePath = '',
  refVisited = new Set<string>(),
): TopLevelField[] {
  if (!rootSchema || typeof rootSchema !== 'object') return [];

  const getDisplayType = (n: any): string => {
    if (!n || typeof n !== 'object') return 'unknown';
    if (typeof n.$ref === 'string' && n.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      return `ref:${n.$ref.slice(SCHEMA_REF_PREFIX.length)}`;
    }
    return Array.isArray(n.type) ? n.type.join('|') : n.type || 'unknown';
  };

  const buildFieldFromProp = (name: string, prop: any, required: boolean, path: string): TopLevelField => {
    if (!prop || typeof prop !== 'object') {
      return { name, path, type: 'unknown', required };
    }
    // 引用
    if (typeof prop.$ref === 'string' && prop.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      const refName = prop.$ref.slice(SCHEMA_REF_PREFIX.length);
      const innerRefVisited = new Set(refVisited);
      const resolved = !refVisited.has(refName) ? resolveIfRef(prop, refSchemaMap, innerRefVisited) : null;
      return {
        name,
        path,
        type: `ref:${refName}`,
        required,
        description: prop.description,
        schemaRef: refName,
        constraints: buildConstraintTags(prop),
        innerSchemaForPreview: resolved ?? undefined,
      };
    }
    const type = Array.isArray(prop.type) ? prop.type.join('|') : prop.type || 'unknown';
    const itemType = type === 'array' && prop.items ? getDisplayType(prop.items) : undefined;

    // 判断这个字段是否可以「递归预览子内容」
    let innerSchemaForPreview: any = undefined;
    if (type === 'array' && prop.items && typeof prop.items === 'object') {
      const itemsNodeVisited = new Set(refVisited);
      let itemsInner = prop.items;
      if (typeof itemsInner.$ref === 'string' && itemsInner.$ref.startsWith(SCHEMA_REF_PREFIX)) {
        const r = resolveIfRef(itemsInner, refSchemaMap, itemsNodeVisited);
        if (r) itemsInner = r;
      }
      innerSchemaForPreview = itemsInner;
    } else if (prop.properties && typeof prop.properties === 'object') {
      innerSchemaForPreview = prop;
    } else if (prop.allOf || prop.anyOf || prop.oneOf) {
      innerSchemaForPreview = prop;
    }
    return {
      name,
      path,
      type,
      format: prop.format,
      required,
      description: prop.description,
      itemType,
      constraints: buildConstraintTags(prop),
      innerSchemaForPreview,
    };
  };

  const out: TopLevelField[] = [];
  const seen = new Set<string>();
  const pushIfNew = (f: TopLevelField) => {
    if (seen.has(f.path + '\x00' + f.name)) return;
    seen.add(f.path + '\x00' + f.name);
    out.push(f);
  };

  // 顶层引用：resolve 后再递归 collectTopLevelFields
  if (typeof rootSchema.$ref === 'string' && rootSchema.$ref.startsWith(SCHEMA_REF_PREFIX)) {
    const resolved = resolveIfRef(rootSchema, refSchemaMap, refVisited);
    if (resolved) return collectTopLevelFields(resolved, refSchemaMap, basePath, new Set(refVisited));
    // 无法解析（循环引用 / 未加载）返回空
    return out;
  }

  // 顶层组合：每段当作完整 schema 合并字段（重复路径忽略后面的）
  for (const kw of ['allOf', 'anyOf', 'oneOf'] as const) {
    if (Array.isArray(rootSchema[kw])) {
      rootSchema[kw].forEach((seg: any) => {
        const segFields = collectTopLevelFields(seg, refSchemaMap, basePath, new Set(refVisited));
        segFields.forEach(pushIfNew);
      });
    }
  }

  // 顶层 object：properties
  if (rootSchema.properties && typeof rootSchema.properties === 'object') {
    const requiredSet: Set<string> = new Set(Array.isArray(rootSchema.required) ? rootSchema.required : []);
    Object.entries(rootSchema.properties).forEach(([k, v]: [string, any]) => {
      const p = basePath ? `${basePath}.${k}` : k;
      pushIfNew(buildFieldFromProp(k, v, requiredSet.has(k), p));
    });
  }

  // 顶层 array：把 items 当 schema 递归提取，然后给路径加 `[]` 前缀
  if (rootSchema.type === 'array' && rootSchema.items && typeof rootSchema.items === 'object') {
    let itemsNode: any = rootSchema.items;
    const itemsVisited = new Set(refVisited);
    if (typeof itemsNode.$ref === 'string' && itemsNode.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      const r = resolveIfRef(itemsNode, refSchemaMap, itemsVisited);
      if (r) itemsNode = r;
    }
    const childPrefix = basePath ? `${basePath}[]` : '[]';
    const inner = collectTopLevelFields(itemsNode, refSchemaMap, childPrefix, itemsVisited);
    inner.forEach(pushIfNew);
  }

  return out;
}

/**
 * 将 JSON Schema 拍平为一张字段表格（含嵌套 properties、数组 items、以及 schema: 引用内的字段）。
 * refSchemaMap 用于把引用展开进入表格；若引用不在 map 里则显示为 ref:NAME。
 * 同一引用节点在同一条路径上只会展开一次（深度 visited 保护，防循环引用）。
 *
 * 入口从 root schema 整体递归，支持：
 *   - 顶层为 object+properties（最常见）
 *   - 顶层为 array（items 是 object/引用/allOf 等）
 *   - 顶层为 allOf/anyOf/oneOf 组合
 *   - 顶层本身是 $ref: schema:xxx
 *   - 任意深度的上述结构组合
 */
export function flattenSchemaFields(
  schema: any,
  refSchemaMap: Map<string, { schemaJson?: string | null }>,
  options: { expandRefs?: boolean } = {},
): FlatFieldInfo[] {
  const out: FlatFieldInfo[] = [];
  const { expandRefs = true } = options;

  const buildFieldInfo = (name: string, prop: any, required: boolean, path: string): FlatFieldInfo => {
    if (!prop || typeof prop !== 'object') {
      return { path, name, type: 'unknown', required };
    }
    if (typeof prop.$ref === 'string' && prop.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      const refName = prop.$ref.slice(SCHEMA_REF_PREFIX.length);
      return {
        path,
        name,
        type: `ref:${refName}`,
        required,
        description: prop.description,
        schemaRef: refName,
      };
    }
    const type: string = Array.isArray(prop.type) ? prop.type.join('|') : prop.type || 'unknown';
    const itemType = type === 'array' && prop.items ? getDisplayType(prop.items) : undefined;
    return {
      path,
      name,
      type,
      format: prop.format,
      required,
      description: prop.description,
      itemType,
      minimum: prop.minimum,
      maximum: prop.maximum,
      minLength: prop.minLength,
      maxLength: prop.maxLength,
      pattern: prop.pattern,
      enum: Array.isArray(prop.enum) ? prop.enum : undefined,
    };
  };

  const getDisplayType = (node: any): string => {
    if (!node || typeof node !== 'object') return 'unknown';
    if (typeof node.$ref === 'string' && node.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      return `ref:${node.$ref.slice(SCHEMA_REF_PREFIX.length)}`;
    }
    return Array.isArray(node.type) ? node.type.join('|') : node.type || 'unknown';
  };

  /**
   * 把 node.properties 里的所有字段推入 out 表格，然后对每个字段的值调 recurseField 继续下钻。
   * 仅当 node 本身是一个 object+properties 结构时才调用。
   */
  const pushProperties = (
    node: any,
    pathPrefix: string,
    visitedOnPath: Set<string>,
  ) => {
    if (!node || typeof node !== 'object') return;
    const props = node.properties;
    if (!props || typeof props !== 'object') return;
    const requiredSet: Set<string> = new Set(Array.isArray(node.required) ? node.required : []);
    Object.entries(props).forEach(([key, prop]: [string, any]) => {
      const childPath = pathPrefix ? `${pathPrefix}.${key}` : key;
      out.push(buildFieldInfo(key, prop, requiredSet.has(key), childPath));
      recurseFieldValue(prop, childPath, new Set(visitedOnPath));
    });
  };

  /**
   * 对一个字段的 schema 值递归深入：
   *   object → 推子 properties
   *   array → 深入 items
   *   $ref → 展开后当作完整 schema 递归 recurseWhole
   *   allOf/anyOf/oneOf → 递归每一项
   */
  const recurseFieldValue = (
    node: any,
    pathPrefix: string,
    visitedOnPath: Set<string>,
  ) => {
    if (!node || typeof node !== 'object') return;

    // 引用展开：字段本身就是 $ref
    if (typeof node.$ref === 'string' && node.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      if (!expandRefs) return;
      const refName = node.$ref.slice(SCHEMA_REF_PREFIX.length);
      if (visitedOnPath.has(refName)) return;
      visitedOnPath.add(refName);
      const target = refSchemaMap.get(refName);
      let inner: any = null;
      try {
        inner = target?.schemaJson ? JSON.parse(target.schemaJson) : null;
      } catch {
        inner = null;
      }
      if (inner) {
        // 引用内容本身可能是任意结构（object/array/allOf/ref），所以用 recurseWhole 完整处理
        recurseWhole(inner, pathPrefix, new Set(visitedOnPath));
      }
      return;
    }

    // 嵌套 object 有 properties → 推子字段
    if (node.properties) {
      pushProperties(node, pathPrefix, visitedOnPath);
    }
    // array items → 深入 items 结构
    if (node.items && typeof node.items === 'object') {
      const childPrefix = pathPrefix ? `${pathPrefix}[]` : '[]';
      recurseFieldValue(node.items, childPrefix, visitedOnPath);
    }
    // 组合关键字
    for (const kw of ['allOf', 'anyOf', 'oneOf'] as const) {
      if (Array.isArray(node[kw])) {
        node[kw].forEach((s: any, i: number) => {
          recurseFieldValue(s, `${pathPrefix}#${kw}[${i}]`, new Set(visitedOnPath));
        });
      }
    }
  };

  /**
   * 把 node 当作一个完整 schema 定义递归。
   * 支持顶层结构：引用 / object + properties / array(items) / allOf/anyOf/oneOf 组合 / 嵌套组合
   * pathPrefix 是这些字段添加到 out 表格时使用的根路径前缀。
   */
  const recurseWhole = (
    node: any,
    pathPrefix: string,
    visitedOnPath: Set<string>,
  ) => {
    if (!node || typeof node !== 'object') return;

    // 顶层本身就是引用 → 展开后再用 recurseWhole 全量处理
    if (typeof node.$ref === 'string' && node.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      if (!expandRefs) return;
      const refName = node.$ref.slice(SCHEMA_REF_PREFIX.length);
      if (visitedOnPath.has(refName)) return;
      visitedOnPath.add(refName);
      const target = refSchemaMap.get(refName);
      let inner: any = null;
      try {
        inner = target?.schemaJson ? JSON.parse(target.schemaJson) : null;
      } catch {
        inner = null;
      }
      if (inner) recurseWhole(inner, pathPrefix, new Set(visitedOnPath));
      return;
    }

    // 组合关键字：每一项都是完整 schema 片段，用 recurseWhole（因为片段可能自己又是 allOf/array/ref）
    for (const kw of ['allOf', 'anyOf', 'oneOf'] as const) {
      if (Array.isArray(node[kw])) {
        node[kw].forEach((s: any, i: number) => {
          recurseWhole(s, pathPrefix, new Set(visitedOnPath));
        });
      }
    }

    // 顶层 object：推 properties
    if (node.properties) {
      pushProperties(node, pathPrefix, visitedOnPath);
    }

    // 顶层 array：深入 items（items 本身可能是 object/ref/allOf）
    if (node.type === 'array' && node.items && typeof node.items === 'object') {
      const childPrefix = pathPrefix ? `${pathPrefix}[]` : '[]';
      recurseFieldValue(node.items, childPrefix, visitedOnPath);
    }
  };

  // 入口：把输入 schema 当作完整 schema 递归（支持顶层任意结构）
  recurseWhole(schema, '', new Set());
  return out;
}

/**
 * 将 JSON Schema 中所有 schema:xxx $ref 递归地就地替换为目标 schema 内容，返回一份「展开后的完整组合 Schema」。
 * refSchemaMap 必须已经包含所有引用的 schemaJson；若引用未提供，或遇到循环引用，则保留原 $ref 字符串。
 * 返回值永远是新对象，不会修改输入。
 */
export function dereferenceSchema<T = any>(schema: T, refSchemaMap: Map<string, { schemaJson?: string | null }>): T {
  if (!schema || typeof schema !== 'object') return schema;
  const clone = JSON.parse(JSON.stringify(schema));
  const visitedOnPath = new Set<string>();

  const replaceNode = (node: any, pathVisited: Set<string>): any => {
    if (!node || typeof node !== 'object') return node;
    if (Array.isArray(node)) {
      return node.map((item) => replaceNode(item, pathVisited));
    }
    // 处理引用
    if (typeof node.$ref === 'string' && node.$ref.startsWith(SCHEMA_REF_PREFIX)) {
      const refName = node.$ref.slice(SCHEMA_REF_PREFIX.length);
      if (pathVisited.has(refName)) {
        // 循环引用：保留原 $ref，并且写一段描述提醒
        return { ...node, _note: `(循环引用，已停止展开) schema:${refName}` } as any;
      }
      const target = refSchemaMap.get(refName);
      let inner: any = null;
      try {
        inner = target?.schemaJson ? JSON.parse(target.schemaJson) : null;
      } catch {
        inner = null;
      }
      if (!inner) {
        return { ...node, _note: `(未加载到引用详情) schema:${refName}` } as any;
      }
      const nextVisited = new Set(pathVisited);
      nextVisited.add(refName);
      const expanded = replaceNode(inner, nextVisited);
      // 如果原节点除了 $ref 外还有 description/title 等元信息，合并进去
      const { $ref, ...meta } = node;
      if (Object.keys(meta).length > 0 && typeof expanded === 'object' && !Array.isArray(expanded)) {
        return { ...meta, ...expanded } as any;
      }
      return expanded;
    }
    const out: any = {};
    for (const [k, v] of Object.entries(node)) {
      out[k] = replaceNode(v, pathVisited);
    }
    return out;
  };
  return replaceNode(clone, visitedOnPath);
}

/** 递归遍历任意 JSON 节点，调用 visitor 访问每一个 $ref 值。WeakSet 防循环引用 */
export function walkSchemaRefs(
  node: any,
  visitor: (ref: string) => void,
  visited: WeakSet<object> = new WeakSet(),
) {
  if (!node || typeof node !== 'object') return;
  if (visited.has(node)) return;
  visited.add(node);
  if (Array.isArray(node)) {
    node.forEach((item) => walkSchemaRefs(item, visitor, visited));
    return;
  }
  Object.entries(node).forEach(([key, value]) => {
    if (key === '$ref' && typeof value === 'string') {
      visitor(value);
    }
    walkSchemaRefs(value, visitor, visited);
  });
}

/**
 * 创建一个「宽容编译版」AJV 实例：
 * 1) 扫描 schema 中所有 schema: 前缀的 $ref
 * 2) 给每个引用通过 addSchema 注入一个空的占位 schema `{}`
 * 3) 这样 AJV compile 就不会抛 can't resolve reference schema:xxx from id # 错误
 *
 * 真实的引用有效性（目标 schema 是否存在、引用链是否合法）由：
 *   - 后端 WfSchemaService.validateSchemaReferences
 *   - 前端保存前的引用存在性检查
 * 共同负责。
 *
 * 返回：准备好的 ajv 实例 + 所有检测到的 schema:xxx 引用 URI 列表。
 */
export function createAjvWithSchemaRefPlaceholders(schema: any): {
  ajv: Ajv;
  refs: string[];
} {
  const refs = new Set<string>();
  walkSchemaRefs(schema, (ref) => {
    if (ref.startsWith(SCHEMA_REF_PREFIX)) {
      refs.add(ref);
    }
  });

  const ajv = new Ajv({
    allErrors: true,
    strict: false,
    strictTypes: false,
    strictTuples: false,
    strictRequired: false,
  });
  addFormats(ajv, { mode: 'fast' });

  refs.forEach((refUri) => {
    try {
      // 占位允许任意值：保证结构合法性校验不因外部引用缺失而中断
      ajv.addSchema({}, refUri);
    } catch {
      // 重复注册忽略（AJV 内部会用 Map 去重，这里 catch 保险起见）
    }
  });

  return { ajv, refs: Array.from(refs) };
}
