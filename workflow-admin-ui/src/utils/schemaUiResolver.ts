/**
 * Schema 驱动的 UI 解析工具。
 *
 * 将后端 Schema 中的 x-widget / x-commands 等元数据转换为 @rjsf 可用的 uiSchema，
 * 供节点参数表单、函数测试面板等场景复用，避免各组件重复硬编码。
 */

/** Redis 命令参数签名 → 需要展示的结构化字段 */
export const REDIS_ARGS_VISIBLE: Record<string, string[]> = {
  K: ['key'],
  KV: ['key', 'args'],
  K_FV: ['key', 'args'],
  K_F: ['key', 'args'],
  K_SM: ['key', 'args'],
  K_R: ['key', 'args'],
  K_N: ['key', 'args'],
  K_T: ['key', 'args'],
  P: ['args'],
  D: ['args'],
  S: ['script', 'keys', 'args'],
  N: [],
  X: ['commands'],
};

/** 原始命令行模式下需要隐藏的结构化字段 */
export const REDIS_RAW_HIDDEN_FIELDS = ['command', 'key', 'args', 'script', 'keys', 'commands'];

/** Redis 结构化命令包含的字段集合（用于模式切换时清理/保留草稿） */
export const REDIS_STRUCTURED_FIELDS = REDIS_RAW_HIDDEN_FIELDS;

/**
 * 根据 Redis 命令的 args 签名（K/KV/K_FV/...）以及 command/raw 互斥模式决定字段显隐。
 *
 * @param mode 显式指定模式；不传时按 value.raw 是否有值自动推断（保持 NodeParamForm 原行为）。
 */
export function resolveRedisCommandUiSchema(
  schema: Record<string, any>,
  value: Record<string, any> | undefined,
  mode?: 'structured' | 'raw',
): Record<string, any> {
  const rawValue = value?.raw;

  // 显式 raw 模式，或按值自动推断的 raw 模式（NodeParamForm 兼容行为）
  const isRawMode =
    mode === 'raw' || (mode == null && typeof rawValue === 'string' && rawValue.trim().length > 0);

  if (isRawMode) {
    const hidden: Record<string, any> = {};
    for (const f of REDIS_RAW_HIDDEN_FIELDS) {
      hidden[f] = { 'ui:widget': 'hidden' };
    }
    return hidden;
  }

  // 结构化模式：仅当显式指定 mode 时才隐藏 raw（FunctionTestPanel 的互斥切换需求）
  const hidden: Record<string, any> = mode === 'structured' ? { raw: { 'ui:widget': 'hidden' } } : {};

  const commandValue = (value?.command as string | undefined)?.trim();
  const commandProp = schema?.properties?.command;
  const xCommands: Record<string, any> = commandProp?.['x-commands'];

  // 尚未选择命令时，先把 EVAL/PIPELINE 专用字段收起来，避免界面同时出现 command 和 script
  if (!commandValue) {
    hidden.script = { 'ui:widget': 'hidden' };
    hidden.keys = { 'ui:widget': 'hidden' };
    hidden.commands = { 'ui:widget': 'hidden' };
    return hidden;
  }

  const cmdMeta = xCommands?.[commandValue];
  const upperCommand = commandValue.toUpperCase();

  // 有 x-commands 元数据时按签名精确显隐
  if (cmdMeta?.args) {
    const argsSignature = cmdMeta.args as string;
    const visible = REDIS_ARGS_VISIBLE[argsSignature];
    if (visible) {
      const allFields = ['key', 'args', 'script', 'keys', 'commands'];
      for (const f of allFields) {
        if (!visible.includes(f)) {
          hidden[f] = { 'ui:widget': 'hidden' };
        }
      }
    }
    return hidden;
  }

  // 兜底：本地 schema 未携带 x-commands 时，按命令名做粗粒度显隐
  if (upperCommand === 'PIPELINE') {
    hidden.script = { 'ui:widget': 'hidden' };
    hidden.keys = { 'ui:widget': 'hidden' };
    hidden.key = { 'ui:widget': 'hidden' };
    hidden.args = { 'ui:widget': 'hidden' };
  } else if (upperCommand.startsWith('EVAL')) {
    // EVAL 家族使用 script/keys/args，隐藏普通 key 和 pipeline 列表
    hidden.key = { 'ui:widget': 'hidden' };
    hidden.commands = { 'ui:widget': 'hidden' };
  } else {
    // 普通 Redis 命令不显示 EVAL/Pipeline 专用字段
    hidden.script = { 'ui:widget': 'hidden' };
    hidden.keys = { 'ui:widget': 'hidden' };
    hidden.commands = { 'ui:widget': 'hidden' };
  }
  return hidden;
}

/**
 * 遍历 schema properties，为标记了 x-widget 的字段自动生成 uiSchema 条目。
 * 支持嵌套对象和数组子项，完全由后端 Schema 驱动，前端零硬编码。
 */
export function resolveDynamicWidgets(schema: Record<string, any>): Record<string, any> {
  const result: Record<string, any> = {};
  const properties = schema.properties || {};

  for (const [key, prop] of Object.entries(properties) as [string, any][]) {
    if (prop['x-widget']) {
      result[key] = { 'ui:widget': prop['x-widget'] };
    }
    // 嵌套对象：递归展开
    if (prop.type === 'object' && prop.properties) {
      const nested = resolveDynamicWidgets(prop);
      if (Object.keys(nested).length > 0) {
        result[key] = { ...(result[key] || {}), ...nested };
      }
    }
    // 数组子项：处理 items
    if (prop.type === 'array' && prop.items) {
      if (prop.items['x-widget']) {
        result[key] = { ...(result[key] || {}), items: { 'ui:widget': prop.items['x-widget'] } };
      }
      if (prop.items.type === 'object' && prop.items.properties) {
        const nested = resolveDynamicWidgets(prop.items);
        if (Object.keys(nested).length > 0) {
          result[key] = { ...(result[key] || {}), items: { ...(result[key]?.items || {}), ...nested } };
        }
      }
    }
  }

  return result;
}
