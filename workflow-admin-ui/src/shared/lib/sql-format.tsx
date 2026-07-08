// shared/lib/sql-format.tsx
// 跨 features 通用：SQL 高亮、历史摘要、结果裁剪（DB feature 用）

import React from 'react';
import type { SqlHistoryEntry } from '../types/function-test-common';
import { MAX_DISPLAY_ROWS, RESULT_MAX_JSON_BYTES } from '../types/function-test-common';

const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'LIKE', 'BETWEEN',
  'IS', 'NULL', 'ORDER', 'BY', 'ASC', 'DESC', 'GROUP', 'HAVING',
  'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'AS',
  'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE',
  'CREATE', 'TABLE', 'ALTER', 'DROP', 'TRUNCATE', 'INDEX', 'VIEW',
  'UNION', 'ALL', 'DISTINCT', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
  'LIMIT', 'OFFSET', 'WITH',
];
const SQL_KEYWORD_RE = new RegExp(`\\b(${SQL_KEYWORDS.join('|')})\\b`, 'gi');

export function highlightSqlKeywords(sql: string): React.ReactNode {
  if (!sql) return null;
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = SQL_KEYWORD_RE.exec(sql)) !== null) {
    if (m.index > lastIndex) parts.push(<span key={`t-${lastIndex}`}>{sql.slice(lastIndex, m.index)}</span>);
    parts.push(
      <span key={`k-${m.index}`} style={{ color: '#722ed1', fontWeight: 600, fontFamily: 'monospace' }}>
        {m[0]}
      </span>
    );
    lastIndex = m.index + m[0].length;
  }
  if (lastIndex < sql.length) parts.push(<span key={`t-${lastIndex}`}>{sql.slice(lastIndex)}</span>);
  return <>{parts}</>;
}

const pad2 = (n: number) => String(n).padStart(2, '0');
export function formatHistoryTime(ts: number): string {
  if (!ts) return '—';
  const d = new Date(ts);
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
}

export function summarizeHistoryResult(entry: SqlHistoryEntry): string | null {
  if (entry.error) {
    return `❌ 错误：${entry.error.length > 40 ? entry.error.slice(0, 40) + '...' : entry.error}`;
  }
  const r = entry.result;
  if (r === undefined || r === null) return null;
  if (Array.isArray(r)) {
    return `✅ 查询成功，返回 ${r.length} 行${entry.truncated ? '（已裁剪预览）' : ''}`;
  }
  if (r && typeof r === 'object') {
    const parts: string[] = [];
    if (typeof r.rowCount === 'number') parts.push(`返回 ${r.rowCount} 行`);
    else if (typeof r.rowsRowCount === 'number') parts.push(`返回 ${r.rowsRowCount} 行`);
    else if (typeof r.dataRowCount === 'number') parts.push(`返回 ${r.dataRowCount} 行`);
    if (typeof r.rowsAffected === 'number') parts.push(`影响 ${r.rowsAffected} 行`);
    else if (typeof r.affectedRows === 'number') parts.push(`影响 ${r.affectedRows} 行`);
    if (typeof r.insertId !== 'undefined') parts.push(`insertId=${r.insertId}`);
    if (entry.truncated) parts.push('（已裁剪预览）');
    return parts.length > 0 ? `✅ ${parts.join('，')}` : '✅ 执行成功';
  }
  return typeof r === 'string' && r.length > 40 ? `✅ ${r.slice(0, 40)}...` : `✅ ${String(r)}`;
}

export function shrinkForDisplay(output: any): any {
  if (!Array.isArray(output?.output ? output.output : output)) return output;
  const arr = Array.isArray(output?.output ? output.output : output) as any[];
  if (arr.length <= MAX_DISPLAY_ROWS) return output;
  const limited = arr.slice(0, MAX_DISPLAY_ROWS);
  const msg = `（共 ${arr.length} 行，仅展示前 ${MAX_DISPLAY_ROWS} 行）`;
  if (Array.isArray(output)) return limited;
  return { ...(output || {}), output: limited, _displayNote: msg };
}

export function shrinkResultForHistory(result: any): { result: any; truncated: boolean } {
  try {
    const s = JSON.stringify(result);
    if (s.length <= RESULT_MAX_JSON_BYTES) return { result, truncated: false };
    if (result && typeof result === 'object' && Array.isArray((result as any).output)) {
      const head = (result as any).output.slice(0, 10);
      return {
        result: { ...(result as any), output: head, _truncatedNote: `output 裁剪到 10 条（原 ${(result as any).output.length} 条）` },
        truncated: true,
      };
    }
    return { result: null, truncated: true };
  } catch {
    return { result, truncated: false };
  }
}
