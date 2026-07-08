// features/generic-function-test/api/index.ts
// === Generic Function Test Feature 专属 API ===
// 通用脚本/外部服务等函数——通过 function/test 标准 HTTP 调用

import { apiPost } from '@/shared/api/request';
import type { SchemaObject } from '@/shared/lib/json-schema';
import { parseSchema } from '@/shared/lib/json-schema';
import { getFunction as loadFn } from '@/entities/function/api';

export async function testGenericFunction(
  functionId: string,
  payload: { inputs: Record<string, any> },
): Promise<any> {
  return apiPost(`/api/admin/functions/${encodeURIComponent(functionId)}/test`, payload);
}

export async function loadFunctionParamSchema(functionId: string): Promise<{
  paramSchema: SchemaObject | null;
  resultSchema: SchemaObject | null;
  functionMeta: { id?: string; name?: string; domain?: string } | null;
}> {
  const fn = await loadFn(functionId);
  if (!fn) return { paramSchema: null, resultSchema: null, functionMeta: null };
  const cfg = (fn as any)?.config ?? {};
  return {
    paramSchema: parseSchema(cfg.paramSchema ?? cfg.inputSchema),
    resultSchema: parseSchema(cfg.resultSchema ?? cfg.outputSchema),
    functionMeta: {
      id: fn.id ?? functionId,
      name: fn.name,
      domain: cfg.domain ?? cfg.category,
    },
  };
}
