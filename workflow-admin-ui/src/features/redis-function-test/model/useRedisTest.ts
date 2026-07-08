// features/redis-function-test/model/useRedisTest.ts
// === Redis 函数测试 Feature 用例 Hook ===
// 管理 structured/raw 两种模式、草稿切换、执行与结果

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { SchemaObject } from '@/shared/lib/json-schema';
import { parseSchema, sanitizeSchemaForRJSF } from '@/shared/lib/json-schema';
import { getFunction as loadFn } from '@/entities/function/api';
import { testRedisFunction } from '../api';

export type RedisMode = 'structured' | 'raw';

export interface UseRedisTestInput {
  functionId: string;
  initialInput?: Record<string, any>;
  onOutput?: (out: any) => void;
  customExecuteTest?: (payload: { inputs: Record<string, any> }) => Promise<any>;
  paramSchemaFromProps?: Record<string, any> | null;
  domainFromProps?: string;
  structuredFields?: string[];
}

export interface UseRedisTestResult {
  paramSchema: SchemaObject | null;
  sanitizedParamSchema: SchemaObject | null;
  functionMeta: { id?: string; name?: string; domain?: string } | null;

  mode: RedisMode;
  setMode: (m: RedisMode) => void;

  input: Record<string, any>;
  setInput: React.Dispatch<React.SetStateAction<Record<string, any>>>;

  loading: boolean;
  result: any;
  errorMessage: string | null;
  setResult: React.Dispatch<React.SetStateAction<any>>;
  setErrorMessage: React.Dispatch<React.SetStateAction<string | null>>;

  handleTest: () => Promise<void>;
  loadFunctionMetadata: (id: string) => Promise<void>;
}

export function useRedisTest(opts: UseRedisTestInput): UseRedisTestResult {
  const {
    functionId, initialInput, onOutput, customExecuteTest,
    paramSchemaFromProps, domainFromProps, structuredFields,
  } = opts;

  const STRUCT_FIELDS = structuredFields || [
    'command', 'key', 'keys', 'field', 'fields', 'value', 'values',
    'member', 'members', 'score', 'scores', 'min', 'max', 'start', 'stop', 'offset', 'count',
    'index', 'length', 'expire', 'ttl', 'nx', 'xx', 'get', 'incr',
  ];

  const [paramSchema, setParamSchema] = useState<SchemaObject | null>(null);
  const [functionMeta, setFunctionMeta] = useState<{ id?: string; name?: string; domain?: string } | null>(null);

  const [input, setInput] = useState<Record<string, any>>(initialInput ?? {});
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(undefined);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [mode, setMode] = useState<RedisMode>('structured');
  const modeDrafts = useRef<{ structured: any; raw: any }>({ structured: {}, raw: {} });

  const loadedFnId = useRef<string | undefined>(undefined);

  const finalParamSchema = useMemo<SchemaObject | null>(() => {
    if (paramSchemaFromProps && Object.keys(paramSchemaFromProps).length > 0) return paramSchemaFromProps as SchemaObject;
    return paramSchema;
  }, [paramSchemaFromProps, paramSchema]);

  const sanitizedParamSchema = useMemo(() => sanitizeSchemaForRJSF(finalParamSchema), [finalParamSchema]);

  // —— structured <-> raw 切换时，保持草稿互相独立不丢失 ——
  const setModeWithDraft = useCallback((next: RedisMode) => {
    setInput((prev) => {
      if (mode === 'structured') {
        const draft: any = {};
        for (const f of STRUCT_FIELDS) if (prev[f] !== undefined) draft[f] = prev[f];
        modeDrafts.current.structured = draft;
      } else {
        modeDrafts.current.raw = prev.raw;
      }
      const out = { ...prev };
      if (next === 'structured') {
        delete out.raw;
        Object.assign(out, modeDrafts.current.structured || {});
      } else {
        for (const f of STRUCT_FIELDS) delete out[f];
        if (modeDrafts.current.raw !== undefined) out.raw = modeDrafts.current.raw;
      }
      return out;
    });
    setMode(next);
  }, [mode, STRUCT_FIELDS]);

  const loadFunctionMetadata = useCallback(async (id: string) => {
    const fn = await loadFn(id);
    if (!fn) { setParamSchema(null); setFunctionMeta(null); return; }
    const cfg = (fn as any)?.config ?? {};
    setFunctionMeta({
      id: fn.id ?? id, name: fn.name,
      domain: domainFromProps ?? cfg.domain ?? cfg.category,
    });
    setParamSchema(parseSchema(cfg.paramSchema ?? cfg.inputSchema) ?? {});
  }, [domainFromProps]);

  useEffect(() => {
    (async () => {
      if (!functionId || loadedFnId.current === functionId) return;
      loadedFnId.current = functionId;
      try { await loadFunctionMetadata(functionId); }
      catch (e) { console.error('[useRedisTest] init fail:', e); }
    })();
  }, [functionId, loadFunctionMetadata]);

  const handleTest = useCallback(async () => {
    if (!functionId) return;
    setErrorMessage(null);
    setLoading(true);
    try {
      const out = customExecuteTest
        ? await customExecuteTest({ inputs: input })
        : await testRedisFunction(functionId, { inputs: input });
      setResult(out);
      onOutput?.(out);
    } catch (e: any) {
      setErrorMessage(e?.message || String(e) || '执行失败');
    } finally { setLoading(false); }
  }, [functionId, input, customExecuteTest, onOutput]);

  return {
    paramSchema: finalParamSchema, sanitizedParamSchema, functionMeta,
    mode, setMode: setModeWithDraft,
    input, setInput,
    loading, result, errorMessage, setResult, setErrorMessage,
    handleTest, loadFunctionMetadata,
  };
}
