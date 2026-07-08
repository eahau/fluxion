// features/condition-function-test/model/useConditionTest.ts
// === Condition/Filter 函数测试 Feature 用例 Hook ===
// 管理：Schema 选择 + 测试数据样本生成 + 字段 introspection + rules 校验/标准化

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { SchemaObject, FieldType, FieldSchemaMeta } from '../lib/utils';
import {
  parseSchema, sanitizeSchemaForRJSF, applyOperatorLabels, mergeLocalConditionSchema,
  isConditionFunction as isCondFn, validateConditionRules, normalizeRuleValues,
  extractSchemaFieldPaths, extractSchemaFieldTypes, extractSchemaFieldMeta,
} from '../lib/utils';
import {
  listSchemas, getSchemaObjectByName, generateSampleFromSchema as sampleFromSchema,
  testConditionFunction,
} from '../api';
import { getFunction as loadFn } from '@/entities/function/api';
import type { FunctionDefinition } from '@/types/function';
import { NODE_PARAM_SCHEMAS } from '@/constants/nodeParamSchemas';

export interface UseConditionTestInput {
  functionId: string;
  initialInput?: Record<string, any>;
  onOutput?: (out: any) => void;
  customExecuteTest?: (payload: { inputs: Record<string, any> }) => Promise<any>;
  /** 外部传入的 functionDefinition（优先用 props） */
  functionDefinition?: FunctionDefinition | null;
  /** 节点测试时若上游入参 schema 已确定，直接作为字段源与测试数据来源 */
  fixedInputSchema?: Record<string, any>;
}

export interface UseConditionTestResult {
  // 元数据
  paramSchema: SchemaObject | null;
  mergedParamSchema: SchemaObject | null;
  functionMeta: { id?: string; name?: string; domain?: string } | null;

  // Schema 选择（条件函数需要选一个业务对象 Schema 作为字段源）
  schemas: SchemaBrief[];
  schemasLoading: boolean;
  loadSchemas: (keyword?: string) => Promise<void>;

  selectedSchemaName: string | undefined;
  setSelectedSchemaName: (n: string | undefined) => void;
  testData: Record<string, any>;
  setTestData: (d: Record<string, any>) => void;

  schemaFields: string[];
  schemaFieldTypes: Record<string, FieldType>;
  schemaFieldMeta: Record<string, FieldSchemaMeta>;
  handleSchemaChange: (schemaName: string | undefined) => Promise<void>;

  // 输入 + 执行
  input: Record<string, any>;
  setInput: React.Dispatch<React.SetStateAction<Record<string, any>>>;
  loading: boolean;
  result: any;
  errorMessage: string | null;
  setResult: React.Dispatch<React.SetStateAction<any>>;
  setErrorMessage: React.Dispatch<React.SetStateAction<string | null>>;

  handleTest: () => Promise<void>;
}

type SchemaBrief = {
  id?: string; schemaName?: string; name?: string;
  description?: string; schemaType?: string;
};

export function useConditionTest(opts: UseConditionTestInput): UseConditionTestResult {
  const { functionId, initialInput, onOutput, customExecuteTest, functionDefinition: fnDefFromProps, fixedInputSchema } = opts;

  const [schemas, setSchemas] = useState<SchemaBrief[]>([]);
  const [schemasLoading, setSchemasLoading] = useState(false);

  const [paramSchema, setParamSchema] = useState<SchemaObject | null>(null);
  const [functionMeta, setFunctionMeta] = useState<{ id?: string; name?: string; domain?: string } | null>(null);

  const [selectedSchemaName, setSelectedSchemaName] = useState<string | undefined>(undefined);
  const [testData, setTestData] = useState<Record<string, any>>({});
  const [schemaFields, setSchemaFields] = useState<string[]>([]);
  const [schemaFieldTypes, setSchemaFieldTypes] = useState<Record<string, FieldType>>({});
  const [schemaFieldMeta, setSchemaFieldMeta] = useState<Record<string, FieldSchemaMeta>>({});

  const [input, setInput] = useState<Record<string, any>>(initialInput ?? {});
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(undefined);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadedFnId = useRef<string | undefined>(undefined);

  const isConditionMode = useMemo(() => (functionId ? isCondFn(functionId) : false), [functionId]);

  const mergedParamSchema = useMemo<SchemaObject | null>(() => {
    let raw: any = null;
    try {
      const cfgSchema = fnDefFromProps?.config?.paramSchema;
      if (cfgSchema != null && cfgSchema !== '') {
        raw = typeof cfgSchema === 'string' ? JSON.parse(cfgSchema) : cfgSchema;
      }
    } catch (e) {
      console.warn('[useConditionTest] 解析 fnDefFromProps.config.paramSchema 失败:', e);
      raw = null;
    }
    if (!raw) raw = paramSchema;
    if (!raw) {
      try {
        const key = functionId === 'builtin:filter' ? 'FILTER' : 'CONDITION_BRANCH';
        const local = NODE_PARAM_SCHEMAS?.[key];
        return local ? applyOperatorLabels(sanitizeSchemaForRJSF(local)) : null;
      } catch (e) {
        console.warn('[useConditionTest] 取 NODE_PARAM_SCHEMAS 默认失败:', e);
        return null;
      }
    }
    try {
      const merged = isConditionMode ? mergeLocalConditionSchema(raw as any, functionId) : (raw as SchemaObject);
      return applyOperatorLabels(sanitizeSchemaForRJSF(merged));
    } catch (e) {
      console.error('[useConditionTest] merge/normalize paramSchema 失败:', e);
      return null;
    }
  }, [paramSchema, fnDefFromProps, isConditionMode, functionId]);

  const loadSchemas = useCallback(async (keyword?: string) => {
    setSchemasLoading(true);
    try {
      const raw = await listSchemas({ keyword });
      const arr = Array.isArray(raw) ? raw : [];
      console.warn('[useConditionTest:loadSchemas] keyword=', keyword, 'raw=', raw, 'len=', arr.length, 'firstItem=', arr[0]);
      setSchemas(arr as any);
      return arr;
    } catch (e: any) {
      console.error('[useConditionTest:loadSchemas] 异常:', e?.message ?? e);
      setSchemas([]);
      return [];
    } finally { setSchemasLoading(false); }
  }, []);

  const loadFunctionMetadata = useCallback(async (id: string) => {
    if (fnDefFromProps) {
      const cfg = (fnDefFromProps as any)?.config ?? {};
      setFunctionMeta({
        id: fnDefFromProps.id ?? id,
        name: fnDefFromProps.name,
        domain: cfg.domain ?? cfg.category,
      });
      setParamSchema(parseSchema(cfg.paramSchema ?? cfg.inputSchema) ?? {});
      return;
    }
    const fn = await loadFn(id);
    if (!fn) { setParamSchema(null); setFunctionMeta(null); return; }
    const cfg = (fn as any)?.config ?? {};
    setFunctionMeta({ id: fn.id ?? id, name: fn.name, domain: cfg.domain ?? cfg.category });
    setParamSchema(parseSchema(cfg.paramSchema ?? cfg.inputSchema) ?? {});
  }, [fnDefFromProps]);

  const handleSchemaChange = useCallback(async (schemaName: string | undefined) => {
    setSelectedSchemaName(schemaName);
    if (!schemaName) {
      setTestData({});
      setSchemaFields([]);
      setSchemaFieldTypes({});
      setSchemaFieldMeta({});
      return;
    }
    try {
      const schemaObj = (await getSchemaObjectByName(schemaName)) || {};
      setTestData(sampleFromSchema(schemaObj));
      setSchemaFields(extractSchemaFieldPaths(schemaObj));
      setSchemaFieldTypes(extractSchemaFieldTypes(schemaObj));
      setSchemaFieldMeta(extractSchemaFieldMeta(schemaObj));
    } catch (e) {
      console.error('[useConditionTest] 加载 schema 失败:', e);
      setTestData({});
      setSchemaFields([]);
      setSchemaFieldTypes({});
      setSchemaFieldMeta({});
    }
  }, []);

  useEffect(() => {
    console.warn('[useConditionTest:useEffect] 触发：functionId=', functionId, 'typeof=', typeof functionId, 'falsy=', !functionId);
    if (!functionId) return;
    loadedFnId.current = functionId;

    // 若上游入参 schema 已确定，直接用它作为字段源，不再请求列表
    if (fixedInputSchema && Object.keys(fixedInputSchema).length > 0) {
      setSelectedSchemaName('(当前节点入参)');
      setTestData(sampleFromSchema(fixedInputSchema));
      setSchemaFields(extractSchemaFieldPaths(fixedInputSchema));
      setSchemaFieldTypes(extractSchemaFieldTypes(fixedInputSchema));
      setSchemaFieldMeta(extractSchemaFieldMeta(fixedInputSchema));
      setSchemas([]);
    } else {
      (async () => {
        try {
          const res = await loadSchemas();
          console.warn('[useConditionTest:useEffect] loadSchemas 完成：len=', res.length);
        } catch (e) { console.error('[useConditionTest] loadSchemas fail:', e); }
      })();
    }
    (async () => {
      try { await loadFunctionMetadata(functionId); }
      catch (e) { console.error('[useConditionTest] loadFunctionMetadata fail:', e); }
    })();
  }, [functionId, loadSchemas, loadFunctionMetadata, fixedInputSchema]);

  const handleTest = useCallback(async () => {
    if (!functionId) return;
    setErrorMessage(null);
    setLoading(true);
    try {
      const ruleErr = validateConditionRules(input);
      if (ruleErr) { setErrorMessage(ruleErr); setLoading(false); return; }
      let payloadInput = normalizeRuleValues({ ...(input ?? {}) });
      (payloadInput as any).testData = testData;
      // 固定 schema 场景下 schema 名称对后端无意义，避免传特殊占位符
      if (selectedSchemaName && !fixedInputSchema) (payloadInput as any).schema = selectedSchemaName;

      const out = customExecuteTest
        ? await customExecuteTest({ inputs: payloadInput })
        : await testConditionFunction(functionId, { inputs: payloadInput });
      setResult(out);
      onOutput?.(out);
    } catch (e: any) {
      setErrorMessage(e?.message || String(e) || '执行失败');
    } finally { setLoading(false); }
  }, [functionId, input, testData, selectedSchemaName, customExecuteTest, onOutput]);

  return {
    paramSchema, mergedParamSchema, functionMeta,
    schemas, schemasLoading, loadSchemas,
    selectedSchemaName, setSelectedSchemaName,
    testData, setTestData,
    schemaFields, schemaFieldTypes, schemaFieldMeta,
    handleSchemaChange,
    input, setInput, loading, result, errorMessage, setResult, setErrorMessage,
    handleTest,
  };
}
