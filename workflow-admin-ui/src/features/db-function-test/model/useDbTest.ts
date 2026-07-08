// features/db-function-test/model/useDbTest.ts
// === DB 函数测试：Feature 内聚的用例 Hook ===
// - 状态：当前数据源、表列表、选中表的列、SQL 历史、input sql、执行结果
// - 动作：切换数据源→拉取表；切换表→拉列；点"执行"→调 testDbFunction 并写入历史

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { SqlHistoryEntry } from '@/shared/types/function-test-common';
import {
  listDatasources, listTables, listTableColumns, testDbFunction,
  getSqlHistory, saveSqlHistory,
} from '../api';
import type { DbDatasourceBrief, DbTableColumn } from './types';
import type { SchemaObject } from '@/shared/lib/json-schema';
import { parseSchema, sanitizeSchemaForRJSF } from '@/shared/lib/json-schema';
import { getFunction as loadFn } from '@/entities/function/api';

export interface UseDbTestInput {
  functionId: string;
  initialInput?: Record<string, any>;
  onOutput?: (out: any) => void;
  customExecuteTest?: (payload: { inputs: Record<string, any> }) => Promise<any>;
  /** 外部传入的 paramSchema（比如 props 里直接有的），优先于 HTTP 拉取 */
  paramSchemaFromProps?: Record<string, any> | null;
  /** 外部传入的 meta.domain（比如 props 里直接有的） */
  domainFromProps?: string;
}

export interface UseDbTestResult {
  // 元数据
  paramSchema: SchemaObject | null;
  filteredParamSchema: SchemaObject | null;
  functionMeta: { id?: string; name?: string; domain?: string } | null;

  // 数据源 & 表结构浏览
  datasources: DbDatasourceBrief[];
  datasourcesLoading: boolean;
  selectedDatasource: string | undefined;
  setSelectedDatasource: (v: string | undefined) => void;

  tableBrowserTable: string | undefined;
  setTableBrowserTable: (t: string | undefined) => void;
  tableBrowserTables: string[];
  tableBrowserLoading: boolean;
  tableBrowserCols: DbTableColumn[];

  // 输入 & 执行
  input: Record<string, any>;
  setInput: React.Dispatch<React.SetStateAction<Record<string, any>>>;
  loading: boolean;
  result: any;
  errorMessage: string | null;
  setResult: React.Dispatch<React.SetStateAction<any>>;
  setErrorMessage: React.Dispatch<React.SetStateAction<string | null>>;

  sqlHistory: SqlHistoryEntry[];
  loadSqlHistory: () => Promise<void>;

  handleTest: () => Promise<void>;
  loadFunctionMetadata: (id: string) => Promise<void>;
}

export function useDbTest(opts: UseDbTestInput): UseDbTestResult {
  const { functionId, initialInput, onOutput, customExecuteTest, paramSchemaFromProps, domainFromProps } = opts;

  const [datasources, setDatasources] = useState<DbDatasourceBrief[]>([]);
  const [datasourcesLoading, setDatasourcesLoading] = useState(false);
  const [selectedDatasource, setSelectedDatasource] = useState<string | undefined>(undefined);

  const [tableBrowserTable, setTableBrowserTable] = useState<string | undefined>(undefined);
  const [tableBrowserTables, setTableBrowserTables] = useState<string[]>([]);
  const [tableBrowserLoading, setTableBrowserLoading] = useState(false);
  const [tableBrowserCols, setTableBrowserCols] = useState<DbTableColumn[]>([]);

  const [paramSchema, setParamSchema] = useState<SchemaObject | null>(null);
  const [functionMeta, setFunctionMeta] = useState<{ id?: string; name?: string; domain?: string } | null>(null);

  const [input, setInput] = useState<Record<string, any>>(initialInput ?? {});
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(undefined);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [sqlHistory, setSqlHistory] = useState<SqlHistoryEntry[]>([]);

  const loadedFnId = useRef<string | undefined>(undefined);

  // —— 最终 paramSchema（props 优先） ——
  const finalParamSchema = useMemo<SchemaObject | null>(() => {
    if (paramSchemaFromProps && Object.keys(paramSchemaFromProps).length > 0) return paramSchemaFromProps as SchemaObject;
    return paramSchema;
  }, [paramSchemaFromProps, paramSchema]);

  // —— 过滤后的 paramSchema（隐藏 DB 内部字段，只让 UI 展示 sql）——
  const filteredParamSchema = useMemo<SchemaObject | null>(() => {
    if (!finalParamSchema) return null;
    try {
      const copy: any = JSON.parse(JSON.stringify(finalParamSchema));
      if (copy?.properties) {
        const props = copy.properties as Record<string, any>;
        ['params', 'dataSource', 'resultType', 'limit', 'compensateSql', 'compensateFunctionRef'].forEach(
          (f) => delete props[f],
        );
      }
      if (Array.isArray(copy?.required)) {
        copy.required = copy.required.filter(
          (f: string) => !['params', 'dataSource', 'resultType', 'limit', 'compensateSql', 'compensateFunctionRef'].includes(f),
        );
      }
      return sanitizeSchemaForRJSF(copy);
    } catch {
      return sanitizeSchemaForRJSF(finalParamSchema);
    }
  }, [finalParamSchema]);

  // —— 加载 SQL 历史 ——
  const loadSqlHistory = useCallback(async () => {
    try { setSqlHistory(getSqlHistory(functionId || 'global')); }
    catch { setSqlHistory([]); }
  }, [functionId]);

  // —— 加载函数元数据（入参 schema / domain）——
  const loadFunctionMetadata = useCallback(async (id: string) => {
    const fn = await loadFn(id);
    if (!fn) { setParamSchema(null); setFunctionMeta(null); return; }
    const cfg = (fn as any)?.config ?? {};
    const domain = domainFromProps ?? cfg.domain ?? cfg.category;
    setFunctionMeta({ id: fn.id ?? id, name: fn.name, domain });
    setParamSchema(parseSchema(cfg.paramSchema ?? cfg.inputSchema) ?? {});

    // 是 DB domain 或 SQL 字段 → 拉数据源
    if (!selectedDatasource && (domain === 'db' || hasSqlFieldInternal(parseSchema(cfg.paramSchema ?? cfg.inputSchema)))) {
      setDatasourcesLoading(true);
      try {
        const list = await listDatasources();
        setDatasources(list);
        if (list.length > 0) setSelectedDatasource(typeof list[0] === 'string' ? list[0] : list[0].id ?? list[0].name);
      } finally { setDatasourcesLoading(false); }
    }
  }, [selectedDatasource, domainFromProps]);

  // —— 初始化 ——
  useEffect(() => { loadSqlHistory(); }, [loadSqlHistory]);

  useEffect(() => {
    (async () => {
      if (!functionId || loadedFnId.current === functionId) return;
      loadedFnId.current = functionId;
      try { await loadFunctionMetadata(functionId); }
      catch (e) { console.error('[useDbTest] init fail:', e); }
    })();
  }, [functionId, loadFunctionMetadata]);

  // —— 选数据源 → 拉该数据源的表列表 ——
  useEffect(() => {
    if (!selectedDatasource) return;
    setInput((prev) => ({ ...(prev ?? {}), dataSource: selectedDatasource }));
    let active = true;
    (async () => {
      setTableBrowserLoading(true);
      setTableBrowserCols([]);
      setTableBrowserTable(undefined);
      try {
        const tables = await listTables(selectedDatasource);
        if (active) setTableBrowserTables(tables);
      } finally { if (active) setTableBrowserLoading(false); }
    })();
    return () => { active = false; };
  }, [selectedDatasource]);

  // —— 选表 → 拉该表的列 ——
  useEffect(() => {
    if (!selectedDatasource || !tableBrowserTable) { setTableBrowserCols([]); return; }
    let active = true;
    (async () => {
      setTableBrowserLoading((prev) => prev || true);
      try {
        const cols = await listTableColumns(tableBrowserTable, selectedDatasource);
        if (active) setTableBrowserCols(cols);
      } finally { if (active) setTableBrowserLoading(false); }
    })();
    return () => { active = false; };
  }, [selectedDatasource, tableBrowserTable]);

  // —— 执行测试 ——
  const handleTest = useCallback(async () => {
    if (!functionId) return;
    setErrorMessage(null);
    setLoading(true);
    try {
      const payloadInput = { dataSource: selectedDatasource, ...(input ?? {}) };
      const out = customExecuteTest
        ? await customExecuteTest({ inputs: payloadInput })
        : await testDbFunction(functionId, { inputs: payloadInput });
      setResult(out);
      onOutput?.(out);
      const sql = payloadInput?.sql;
      if (typeof sql === 'string' && sql.trim()) {
        const saved = saveSqlHistory(functionId, { sql, result: out, executedAt: Date.now() });
        setSqlHistory(saved);
      }
    } catch (e: any) {
      const msg = e?.message || String(e) || '执行失败';
      setErrorMessage(msg);
      const sql = input?.sql;
      if (typeof sql === 'string' && sql.trim()) {
        const saved = saveSqlHistory(functionId, { sql, result: undefined, error: msg, executedAt: Date.now() });
        setSqlHistory(saved);
      }
    } finally { setLoading(false); }
  }, [functionId, selectedDatasource, input, onOutput, customExecuteTest]);

  return {
    paramSchema: finalParamSchema, filteredParamSchema, functionMeta,
    datasources, datasourcesLoading, selectedDatasource, setSelectedDatasource,
    tableBrowserTable, setTableBrowserTable, tableBrowserTables, tableBrowserLoading, tableBrowserCols,
    input, setInput, loading, result, errorMessage, setResult, setErrorMessage,
    sqlHistory, loadSqlHistory, handleTest, loadFunctionMetadata,
  };
}

// 内部小工具（避免 import 循环）
function hasSqlFieldInternal(schema: SchemaObject | null): boolean {
  if (!schema) return false;
  const props = (schema as any)?.properties;
  return props && typeof props.sql === 'object';
}
