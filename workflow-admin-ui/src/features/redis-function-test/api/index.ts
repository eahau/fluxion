// features/redis-function-test/api/index.ts
// === Redis Function Test Feature 专属 API ===
// 内嵌 Redis 命令专属接口（此 Feature 独用，其他 Feature 不依赖这里）

import { apiPost } from '@/shared/api/request';
import { REDIS_COMMANDS as DEFAULT_CMDS } from '@/pages/workflow/components/widgets/RedisRawEditor';

export interface RedisCommandBrief {
  cmd: string;
  summary?: string;
  complexity?: string;
  arity?: number;
}

/**
 * （可选）列举 Redis 支持的命令列表。目前直接用前端常量兜底；
 * 若后端新增了 /api/admin/redis/commands 接口，可在此处替换为 HTTP 请求。
 */
export async function listRedisCommands(): Promise<RedisCommandBrief[]> {
  try {
    const arr = (await import('@/constants/nodeParamSchemas')) as any;
    if (arr?.REDIS_COMMAND_LIST && Array.isArray(arr.REDIS_COMMAND_LIST)) return arr.REDIS_COMMAND_LIST;
  } catch { /* ignore */ }
  const base = DEFAULT_CMDS?.REDIS_COMMAND_LIST || DEFAULT_CMDS;
  return Array.isArray(base) ? base : [];
}

/**
 * 执行 Redis 函数测试（通过 function/test 通用入口，payload 专属 Redis 入参）
 */
export async function testRedisFunction(functionId: string, payload: { inputs: Record<string, any> }): Promise<any> {
  return apiPost(`/api/admin/functions/${encodeURIComponent(functionId)}/test`, payload);
}
