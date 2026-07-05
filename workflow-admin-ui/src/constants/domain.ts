/**
 * 函数领域（Domain）共享常量
 *
 * 后端 WfFunction.domain 字段值 → 前端展示元数据（标签、图标、颜色）
 * 供 function/list.tsx（函数管理列表）和 NodePalette.tsx（工作流节点面板）共同引用
 */

export interface DomainMeta {
  label: string;
  emoji: string;   // 用于轻量场景（如 NodePalette 纯文本图标）
  color: string;   // 领域主色
}

export const DOMAIN_META: Record<string, DomainMeta> = {
  all:    { label: '全部',    emoji: '📚',  color: '#595959' },
  db:     { label: '数据库',  emoji: '🗄️',  color: '#6366f1' },
  redis:  { label: 'Redis',   emoji: '⚡',  color: '#f5222d' },
  cache:  { label: '缓存',    emoji: '💾',  color: '#faad14' },
  http:   { label: 'HTTP',    emoji: '🌐',  color: '#13c2c2' },
  mq:     { label: '消息队列', emoji: '📨',  color: '#722ed1' },
  script: { label: '脚本',    emoji: '📝',  color: '#eb2f96' },
  common: { label: '通用',    emoji: '🔧',  color: '#8c8c8c' },
  other:  { label: '其他',    emoji: '📦',  color: '#bfbfbf' },
};

/** 排序优先级（未列出的排最后） */
export const DOMAIN_ORDER = ['all', 'db', 'redis', 'cache', 'http', 'mq', 'script', 'common', 'other'];

/** 获取领域元信息，未注册领域回退到 other */
export function getDomainMeta(domain?: string | null): DomainMeta {
  if (!domain) return DOMAIN_META.other;
  return DOMAIN_META[domain] ?? DOMAIN_META.other;
}

/** 按 DOMAIN_ORDER 对领域 key 数组排序 */
export function sortDomains(domains: string[]): string[] {
  return [...domains].sort(
    (a, b) => {
      const ia = DOMAIN_ORDER.indexOf(a);
      const ib = DOMAIN_ORDER.indexOf(b);
      return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib);
    },
  );
}
