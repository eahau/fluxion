/**
 * SQL 执行历史记录管理工具
 * 使用 localStorage 进行本地缓存，支持按 functionId 隔离
 */

const STORAGE_KEY = 'function_sql_history';
const MAX_HISTORY_PER_FUNCTION = 50;

export interface SqlHistoryItem {
  /** SQL 语句 */
  sql: string;
  /** 执行时间戳 */
  timestamp: number;
  /** 执行结果状态 */
  status: 'success' | 'error';
  /** 可选：执行结果摘要（如返回行数） */
  summary?: string;
}

/**
 * 获取指定函数的 SQL 历史记录
 * @param functionId 函数 ID，为空时使用全局 key
 */
export function getSqlHistory(functionId?: string): SqlHistoryItem[] {
  try {
    const key = functionId ? `${STORAGE_KEY}_${functionId}` : STORAGE_KEY;
    const data = localStorage.getItem(key);
    if (!data) return [];
    return JSON.parse(data) as SqlHistoryItem[];
  } catch (e) {
    console.error('读取 SQL 历史记录失败:', e);
    return [];
  }
}

/**
 * 保存 SQL 执行记录到历史记录
 * @param sql SQL 语句
 * @param status 执行状态
 * @param summary 结果摘要
 * @param functionId 函数 ID
 */
export function saveSqlHistory(
  sql: string,
  status: 'success' | 'error',
  summary?: string,
  functionId?: string,
): void {
  if (!sql.trim()) return;

  try {
    const key = functionId ? `${STORAGE_KEY}_${functionId}` : STORAGE_KEY;
    const history = getSqlHistory(functionId);

    // 添加新记录到开头
    const newItem: SqlHistoryItem = {
      sql: sql.trim(),
      timestamp: Date.now(),
      status,
      summary,
    };

    // 去重：如果最近一条记录 SQL 相同，则更新时间戳
    if (history.length > 0 && history[0].sql === newItem.sql) {
      history[0].timestamp = newItem.timestamp;
      history[0].status = newItem.status;
      history[0].summary = newItem.summary;
    } else {
      history.unshift(newItem);
    }

    // 限制记录数量
    const trimmedHistory = history.slice(0, MAX_HISTORY_PER_FUNCTION);

    localStorage.setItem(key, JSON.stringify(trimmedHistory));
  } catch (e) {
    console.error('保存 SQL 历史记录失败:', e);
  }
}

/**
 * 删除指定的历史记录
 * @param index 记录索引
 * @param functionId 函数 ID
 */
export function removeSqlHistory(index: number, functionId?: string): void {
  try {
    const key = functionId ? `${STORAGE_KEY}_${functionId}` : STORAGE_KEY;
    const history = getSqlHistory(functionId);
    history.splice(index, 1);
    localStorage.setItem(key, JSON.stringify(history));
  } catch (e) {
    console.error('删除 SQL 历史记录失败:', e);
  }
}

/**
 * 清空指定函数的所有历史记录
 * @param functionId 函数 ID
 */
export function clearSqlHistory(functionId?: string): void {
  try {
    const key = functionId ? `${STORAGE_KEY}_${functionId}` : STORAGE_KEY;
    localStorage.removeItem(key);
  } catch (e) {
    console.error('清空 SQL 历史记录失败:', e);
  }
}

/**
 * 格式化时间戳为可读字符串
 */
export function formatHistoryTime(timestamp: number): string {
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now.getTime() - timestamp;

  // 1 分钟内
  if (diff < 60 * 1000) {
    return '刚刚';
  }
  // 1 小时内
  if (diff < 60 * 60 * 1000) {
    return `${Math.floor(diff / 60000)} 分钟前`;
  }
  // 24 小时内
  if (diff < 24 * 60 * 60 * 1000) {
    return `${Math.floor(diff / 3600000)} 小时前`;
  }
  // 超过 24 小时显示日期时间
  return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
}
