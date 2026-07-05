export const NODE_TYPE_OPTIONS = [
  { value: 'PARAM_VALIDATE', label: '入参校验', icon: '✅', color: '#10b981' },
  { value: 'DYNAMIC_VALIDATE', label: '动态校验', icon: '🔍', color: '#34d399' },
  { value: 'DATA_QUERY', label: '数据查询', icon: '🔍', color: '#3b82f6' },
  { value: 'DATA_TRANSFORM', label: '数据转换', icon: '🔄', color: '#8b5cf6' },
  { value: 'ASSEMBLE_RESPONSE', label: '组装响应', icon: '📦', color: '#f59e0b' },
  { value: 'SCRIPT', label: '脚本执行', icon: '📝', color: '#ec4899' },
  { value: 'CUSTOM', label: '自定义节点', icon: '⚙️', color: '#64748b' },
  { value: 'CONDITION_BRANCH', label: '条件分支', icon: '🔀', color: '#06b6d4' },
  { value: 'FILTER', label: '条件过滤', icon: '🚦', color: '#14b8a6' },
  { value: 'PARALLEL', label: '并行执行', icon: '⏩', color: '#6366f1' },
  { value: 'SUB_WORKFLOW', label: '子工作流', icon: '🔗', color: '#8b5cf6' },
  { value: 'EIP_ROUTER', label: 'EIP 路由', icon: '📡', color: '#ef4444' },
  { value: 'LOOP', label: '循环节点', icon: '🔁', color: '#f97316' },
  { value: 'WAIT', label: '等待节点', icon: '⏳', color: '#a855f7' },
];

export const NODE_TYPE_MAP = Object.fromEntries(
  NODE_TYPE_OPTIONS.map((item) => [item.value, item]),
);

/**
 * 节点角色分组 — 三角色架构
 *
 * 触发器（Trigger）不在此列，属于 Adapter 层，在 WorkflowMetaPanel 配置。
 */
export const NODE_ROLE_GROUPS = [
  {
    role: 'CONTROLLER',
    label: '控制器',
    icon: '🔀',
    desc: '流程编排与路由控制',
    nodeTypes: ['CONDITION_BRANCH', 'FILTER', 'PARALLEL', 'EIP_ROUTER', 'SUB_WORKFLOW'],
  },
  {
    role: 'EXECUTOR',
    label: '执行器',
    icon: '⚙️',
    desc: '业务逻辑与数据处理',
    nodeTypes: ['PARAM_VALIDATE', 'DYNAMIC_VALIDATE', 'DATA_QUERY', 'DATA_TRANSFORM', 'ASSEMBLE_RESPONSE', 'SCRIPT', 'CUSTOM'],
  },
] as const;

/** 快速查找 nodeType → role */
export const NODE_TYPE_TO_ROLE: Record<string, string> = {};
NODE_ROLE_GROUPS.forEach(({ role, nodeTypes }) => {
  nodeTypes.forEach((t) => { NODE_TYPE_TO_ROLE[t] = role; });
});

// 状态颜色 — 使用 Tailwind 风格的现代色板
export const STATUS_COLORS = {
  SUCCESS: { main: '#10b981', light: '#d1fae5', dark: '#065f46' },
  FAILED: { main: '#ef4444', light: '#fee2e2', dark: '#991b1b' },
  FALLBACK: { main: '#f59e0b', light: '#fef3c7', dark: '#92400e' },
  SKIPPED: { main: '#94a3b8', light: '#f1f5f9', dark: '#475569' },
  RUNNING: { main: '#3b82f6', light: '#dbeafe', dark: '#1e40af' },
};
