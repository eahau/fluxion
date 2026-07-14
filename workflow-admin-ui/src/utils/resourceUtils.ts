export const DB_REDIS_FUNCTION_REF_PREFIXES = [
  'builtin:mysql:',
  'builtin:postgresql:',
  'builtin:oracle:',
  'builtin:sqlserver:',
  'builtin:h2:',
  'builtin:clickhouse:',
  'builtin:redis:',
  'builtin:db:',
  'db:',
  'redis:',
];

export const DB_REDIS_CATEGORIES = new Set<string>([
  'DB',
  'DATABASE',
  'REDIS',
  'DATA_SOURCE',
  'DATA_ACCESS',
  'MYSQL',
  'POSTGRESQL',
]);

/**
 * 判断某函数或函数节点是否需要「已选 App」作为前置条件
 * （数据源与 App 绑定，DB/Redis 类函数必须在选了 App 之后才能配置）
 */
export function isDataAccessFunction(input: {
  functionRef?: string | null;
  category?: string | null;
}): boolean {
  const ref = (input.functionRef || '').toString().trim().toLowerCase();
  if (ref && DB_REDIS_FUNCTION_REF_PREFIXES.some((p) => ref.startsWith(p.toLowerCase()))) {
    return true;
  }
  const cat = (input.category || '').toString().trim().toUpperCase();
  if (cat && DB_REDIS_CATEGORIES.has(cat)) {
    return true;
  }
  if (ref && (ref.includes(':mysql') || ref.includes(':postgres') || ref.includes(':redis') || ref.includes(':oracle') || ref.includes(':db:'))) {
    return true;
  }
  return false;
}

/**
 * 数据源类型是否属于数据库类（需要 host/port/username/password/database 等表单）
 */
export function isDbLikeResourceType(resourceType?: string | null): boolean {
  const t = (resourceType || '').toString().trim().toUpperCase();
  return ['MYSQL', 'POSTGRESQL', 'ORACLE', 'SQLSERVER', 'H2', 'CLICKHOUSE', 'MARIADB'].includes(t);
}

/**
 * 数据源类型是否属于 Redis 类（需要 host/port/dbIndex/password 等表单）
 */
export function isRedisResourceType(resourceType?: string | null): boolean {
  const t = (resourceType || '').toString().trim().toUpperCase();
  return t === 'REDIS';
}
