// features/generic-function-test/model/useGenericTest.ts
// === 通用函数测试 Feature 用例 Hook ===
// 适用于 Script（Groovy）、External（HTTP/OpenAPI） 等非 DB/Redis/Condition 的一般函数

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { SchemaObject } from '@/shared/lib/json-schema';
import { parseSchema, sanitizeSchemaForRJSF } from '@/shared/lib/json-schema';
import { loadFunctionParamSchema, testGenericFunction } from '../api';
import type { FunctionDefinition } from '@/types/function';

export interface UseGenericTestInput {
  functionId: string;
  initialInput?: Record<string, any>;
  onOutput?: (out: any) => void;
  customExecuteTest?: (payload: { inputs: Record<string, any> }) => Promise<any>;
  functionDefinition?: FunctionDefinition | null;
}

export interface UseGenericTestResult {
  paramSchema: SchemaObject | null;
  sanitizedParamSchema: SchemaObject | null;
  functionMeta: { id?: string; name?: string; domain?: string } | null;

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

export function useGenericTest(opts: UseGenericTestInput): UseGenericTestResult {
  const { functionId, initialInput, onOutput, customExecuteTest, functionDefinition: fnDefFromProps } = opts;

  const [paramSchema, setParamSchema] = useState<SchemaObject | null>(null);
  const [functionMeta, setFunctionMeta] = useState<{ id?: string; name?: string; domain?: string } | null>(null);

  const [input, setInput] = useState<Record<string, any>>(initialInput ?? {});
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(undefined);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadedFnId = useRef<string | undefined>(undefined);

  const finalParamSchema = useMemo<SchemaObject | null>(() => {
    const raw = fnDefFromProps?.config?.paramSchema
      ? (typeof fnDefFromProps.config.paramSchema === 'string'
          ? JSON.parse(fnDefFromProps.config.paramSchema)
          : fnDefFromProps.config.paramSchema)
      : paramSchema;
    if (!raw) return null;
    return typeof raw === 'object' ? raw : null;
  }, [paramSchema, fnDefFromProps]);

  const sanitizedParamSchema = useMemo(
    () => sanitizeSchemaForRJSF(finalParamSchema),
    [finalParamSchema],
  );

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
    const { paramSchema: ps, functionMeta: fm } = await loadFunctionParamSchema(id);
    setParamSchema(ps);
    setFunctionMeta(fm);
  }, [fnDefFromProps]);

  useEffect(() => {
    (async () => {
      if (!functionId || loadedFnId.current === functionId) return;
      loadedFnId.current = functionId;
      try { await loadFunctionMetadata(functionId); }
      catch (e) { console.error('[useGenericTest] init fail:', e); }
    })();
  }, [functionId, loadFunctionMetadata]);

  const handleTest = useCallback(async () => {
    if (!functionId) return;
    setErrorMessage(null);
    setLoading(true);
    try {
      const out = customExecuteTest
        ? await customExecuteTest({ inputs: input })
        : await testGenericFunction(functionId, { inputs: input });
      setResult(out);
      onOutput?.(out);
    } catch (e: any) {
      setErrorMessage(e?.message || String(e) || '执行失败');
    } finally { setLoading(false); }
  }, [functionId, input, customExecuteTest, onOutput]);

  return {
    paramSchema: finalParamSchema, sanitizedParamSchema, functionMeta,
    input, setInput,
    loading, result, errorMessage, setResult, setErrorMessage,
    handleTest, loadFunctionMetadata,
  };
}
