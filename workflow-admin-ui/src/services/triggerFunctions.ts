import { client, unwrap, silentHeaders } from '@/sdk';
import type { RequestOptions } from './request';
import type { schemas } from '@/sdk';

export type TriggerFunctionMeta = schemas['TriggerFunctionMeta'];
export type TriggerFunctionParam = schemas['TriggerFunctionParam'];
export type WorkflowTrigger = schemas['WorkflowTrigger'];
export type TriggerType = schemas['TriggerType'];

/**
 * ═══════════════════════════════════════════════════════════════════
 * 内置 TriggerFunctionMeta 兜底（后端 SPI 未注册时 UI 不挂空状态）
 * ───────────────────────────────────────────────────────────────────
 * 合并规则：listTriggerFunctions() 先查后端，以后端结果为准（按 functionRef 去重）；
 * 后端返回空数组 / 请求失败 → 用兜底。
 * ═══════════════════════════════════════════════════════════════════
 */
export const BUILTIN_TRIGGER_METAS_FALLBACK: TriggerFunctionMeta[] = [
  {
    functionRef: 'trigger:httpInbound',
    legacyProtocol: 'HTTP',
    label: 'HTTP 入站',
    icon: '🔗',
    category: 'API',
    description: '监听 HTTP 请求进入函数集合执行（GET/POST/PUT/DELETE 全支持，支持路径参数）',
    paramSchema: [
      { name: 'method', type: 'ENUM', required: true, label: '请求方法', description: '匹配的 HTTP 方法，* 表示全匹配', defaultValue: 'POST', options: [['*', '全部 (ALL)'], ['GET', 'GET'], ['POST', 'POST'], ['PUT', 'PUT'], ['DELETE', 'DELETE'], ['PATCH', 'PATCH']] },
      { name: 'path', type: 'string', required: true, label: '路径 path', description: '以 / 开头，支持 {param} 形式路径参数', defaultValue: '/api/workflow/invoke' },
      { name: 'timeoutMs', type: 'integer', required: false, label: '超时(ms)', description: '请求处理超时时间，默认 30000', defaultValue: 30000 },
    ],
    outputSchemaRef: 'builtin:httpInbound:output',
    enabled: true,
    order: 0,
  } as TriggerFunctionMeta,
  {
    functionRef: 'trigger:kafkaConsumer',
    legacyProtocol: 'KAFKA',
    label: 'Kafka 消费',
    icon: '📨',
    category: 'EVENT',
    description: '订阅 Kafka 指定 topic 的消息，逐条进入函数集合处理',
    paramSchema: [
      { name: 'topic', type: 'string', required: true, label: 'Topic 名', description: '消费的 Kafka topic（支持通配符 .*）' },
      { name: 'groupId', type: 'string', required: false, label: '消费组', description: '默认使用函数集合名作为 groupId' },
      { name: 'concurrency', type: 'integer', required: false, label: '并发线程数', description: '每个 Worker 并发消费者数量', defaultValue: 4 },
    ],
    outputSchemaRef: 'builtin:kafkaConsumer:output',
    enabled: true,
    order: 1,
  } as TriggerFunctionMeta,
  {
    functionRef: 'trigger:scheduleCron',
    legacyProtocol: 'SCHEDULE',
    label: '定时任务 Cron',
    icon: '⏰',
    category: 'SCHEDULE',
    description: '按 Cron 表达式周期性触发（cron6/7 位兼容：秒 分 时 日 月 周 [年]）',
    paramSchema: [
      { name: 'cron', type: 'string', required: true, label: 'Cron 表达式', description: '6 位 cron (秒 分 时 日 月 周) 或 7 位带年份', defaultValue: '0 0 */5 * * ?' },
      { name: 'timezone', type: 'string', required: false, label: '时区', description: '默认 Asia/Shanghai', defaultValue: 'Asia/Shanghai' },
    ],
    outputSchemaRef: 'builtin:schedule:output',
    enabled: true,
    order: 2,
  } as TriggerFunctionMeta,
  {
    functionRef: 'trigger:redisStream',
    legacyProtocol: 'REDIS_STREAM',
    label: 'Redis Stream 消费',
    icon: '📬',
    category: 'EVENT',
    description: '订阅 Redis Stream 新消息（blocking consumer，xreadgroup 模式）',
    paramSchema: [
      { name: 'streamKey', type: 'string', required: true, label: 'Stream Key', description: 'Redis Stream 键名' },
      { name: 'groupName', type: 'string', required: false, label: '消费组名', description: '默认函数集合名' },
      { name: 'consumerName', type: 'string', required: false, label: '消费者名前缀', description: '默认 worker-，自动补实例后缀' },
    ],
    outputSchemaRef: 'builtin:redisStream:output',
    enabled: true,
    order: 3,
  } as TriggerFunctionMeta,
  {
    functionRef: 'trigger:dubboProvider',
    legacyProtocol: 'DUBBO',
    label: 'Dubbo 服务',
    icon: '🛰️',
    category: 'API',
    description: '以 Dubbo 接口形式暴露，服务消费者调用后进入函数集合执行',
    paramSchema: [
      { name: 'serviceKey', type: 'string', required: true, label: 'Service Key', description: 'Interface:Version:Group 三元组（Group 可省略）' },
      { name: 'methodName', type: 'string', required: true, label: '方法名', description: '暴露的方法名' },
      { name: 'retries', type: 'integer', required: false, label: '重试次数', description: '默认 0（建议幂等场景开 2 次）', defaultValue: 0 },
    ],
    outputSchemaRef: 'builtin:dubbo:output',
    enabled: true,
    order: 4,
  } as TriggerFunctionMeta,
];

function mergeTriggerMetas(serverMetas: TriggerFunctionMeta[] | null | undefined): TriggerFunctionMeta[] {
  if (!Array.isArray(serverMetas) || serverMetas.length === 0) {
    return [...BUILTIN_TRIGGER_METAS_FALLBACK];
  }
  // 以后端结果为准，functionRef 去重 + 兜底补后端没注册的 builtin
  const map = new Map<string, TriggerFunctionMeta>();
  BUILTIN_TRIGGER_METAS_FALLBACK.forEach((m) => map.set(m.functionRef, m));
  serverMetas.forEach((m) => {
    if (m?.functionRef) map.set(m.functionRef, m);
  });
  return Array.from(map.values()).sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
}

export async function listTriggerFunctions(options?: RequestOptions) {
  try {
    const serverMetas = unwrap(
      await client.GET('/api/admin/trigger-functions', {
        headers: silentHeaders(options),
      }),
    ) as TriggerFunctionMeta[];
    return mergeTriggerMetas(serverMetas);
  } catch (_e) {
    // 后端接口挂了（404/500）直接用兜底，不让 UI 空状态
    return mergeTriggerMetas(undefined);
  }
}

export function buildTriggerDefaults(meta: TriggerFunctionMeta): Record<string, any> {
  const cfg: Record<string, any> = {};
  for (const p of meta.paramSchema ?? []) {
    if (p.defaultValue !== undefined && p.defaultValue !== null) {
      cfg[p.name] = p.defaultValue;
    }
  }
  return cfg;
}

export function findByLegacyProtocol(
  metas: TriggerFunctionMeta[],
  protocol: string | undefined | null,
): TriggerFunctionMeta | undefined {
  if (!protocol) return undefined;
  return metas.find((m) => (m.legacyProtocol ?? '').toUpperCase() === protocol.toUpperCase());
}

