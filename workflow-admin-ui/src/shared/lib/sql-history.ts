// shared/lib/sql-history.ts
// 跨 features 通用：SQL 历史记录 localStorage 读写（DB feature 用）

import type { SqlHistoryEntry } from '../types/function-test-common';
import { SQL_HISTORY_KEY, MAX_SQL_HISTORY } from '../types/function-test-common';
import { shrinkResultForHistory } from './sql-format';

type HistoryMap = Record<string, SqlHistoryEntry[]>;

function loadAll(): HistoryMap {
  try {
    const raw = localStorage.getItem(SQL_HISTORY_KEY);
    if (!raw) return {};
    const obj = JSON.parse(raw);
    return obj && typeof obj === 'object' ? obj : {};
  } catch {
    return {};
  }
}

function saveAll(map: HistoryMap) {
  try {
    localStorage.setItem(SQL_HISTORY_KEY, JSON.stringify(map));
  } catch {
    // ignore quota errors
  }
}

// 兼容旧版 string[] 存储
function normalizeEntries(arr: any[]): SqlHistoryEntry[] {
  if (!Array.isArray(arr)) return [];
  return arr.map((e) =>
    typeof e === 'string'
      ? { sql: e, executedAt: 0 }
      : e && typeof e === 'object' ? { sql: (e as any)?.sql || '', executedAt: (e as any)?.executedAt || 0, result: (e as any).result, error: (e as any).error, truncated: (e as any).truncated }
      : { sql: '', executedAt: 0 }
  );
}

export function getSqlHistory(functionId: string): SqlHistoryEntry[] {
  const all = loadAll();
  return normalizeEntries(all[functionId] || []);
}

export function saveSqlHistory(
  functionId: string,
  entry: Omit<SqlHistoryEntry, 'truncated'> & { truncated?: boolean },
): SqlHistoryEntry[] {
  const all = loadAll();
  const list = normalizeEntries(all[functionId] || []);
  const { result: shrunk, truncated } = entry.error
    ? { result: entry.result, truncated: entry.truncated ?? false }
    : shrinkResultForHistory(entry.result);
  list.unshift({ ...entry, result: shrunk, truncated: entry.truncated ?? truncated });
  const next = list.slice(0, MAX_SQL_HISTORY);
  all[functionId] = next;
  saveAll(all);
  return next;
}
