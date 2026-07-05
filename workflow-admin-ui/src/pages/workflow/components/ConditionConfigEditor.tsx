import { useMemo, useState } from 'react';
import { AutoComplete, Button, Collapse, Input, Select, Space, Switch, Tooltip, Typography, Tag } from 'antd';
import { useClickDebounce } from '@/utils/useClickDebounce';
import {
  DeleteOutlined,
  PlusOutlined,
  InfoCircleOutlined,
  CaretRightOutlined,
  CheckCircleOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import {
  getOperatorsForFieldType,
  VALUE_LESS_OPERATORS as GLOBAL_VALUE_LESS,
  type FieldType,
  type FieldSchemaMeta,
} from '@/utils/conditionSchema';
import { buildFieldOptionsWithMeta, type FieldMetaOption } from './widgets/FieldAutoComplete';

const { Text } = Typography;
const { Panel } = Collapse;

/**
 * 紧凑编辑器可用的操作符。
 *
 * 反向操作符（neq / notIn / isNotNull）已移除，通过对应基础操作符 + negate 实现。
 */
const ALL_OPERATORS: { value: string; label: string }[] = [
  { value: 'eq', label: 'eq (等于)' },
  { value: 'gt', label: 'gt (大于)' },
  { value: 'gte', label: 'gte (大于等于)' },
  { value: 'lt', label: 'lt (小于)' },
  { value: 'lte', label: 'lte (小于等于)' },
  { value: 'in', label: 'in (在列表中)' },
  { value: 'contains', label: 'contains (包含)' },
  { value: 'isNull', label: 'isNull (为空)' },
  { value: 'isEmpty', label: 'isEmpty (空集合)' },
  { value: 'sizeEq', label: 'sizeEq (长度等于)' },
  { value: 'sizeGt', label: 'sizeGt (长度大于)' },
  { value: 'sizeGte', label: 'sizeGte (长度大于等于)' },
  { value: 'sizeLt', label: 'sizeLt (长度小于)' },
  { value: 'sizeLte', label: 'sizeLte (长度小于等于)' },
  { value: 'startsWith', label: 'startsWith (以...开头)' },
  { value: 'endsWith', label: 'endsWith (以...结尾)' },
  { value: 'regex', label: 'regex (正则匹配)' },
];

/** 紧凑编辑器不需要 value 的操作符（与全局 VALUE_LESS_OPERATORS 保持一致） */
const VALUE_LESS_OPERATORS: Set<string> = new Set(GLOBAL_VALUE_LESS);

const LOGIC_OPTIONS = [
  { value: 'and', label: 'AND' },
  { value: 'or', label: 'OR' },
];

interface Rule {
  logic?: string;
  field?: string;
  operator?: string;
  value?: string;
  negate?: boolean;
}

interface ConditionItem {
  logic?: string;
  negate?: boolean;
  rulesGlobalLogicEnabled?: boolean;
  rulesGlobalLogicOperator?: string;
  rules?: Rule[];
  field?: string;
  operator?: string;
  value?: string;
  condition?: string;
  target?: string;
}

interface ConditionConfigEditorProps {
  mode: 'branch' | 'filter';
  value: any;
  onChange: (value: any) => void;
  upstreamFields?: string[];
  /** 字段路径 → 字段类型，用于按类型过滤操作符下拉 */
  fieldTypes?: Record<string, FieldType | string>;
  /** 字段路径 → 完整 schema 元信息，用于字段下拉 hover 提示 */
  fieldMeta?: Record<string, FieldSchemaMeta>;
}

/** 条件分支 / 条件过滤 紧凑编辑器 */
const ConditionConfigEditor: React.FC<ConditionConfigEditorProps> = ({
  mode, value, onChange, upstreamFields = [], fieldTypes = {}, fieldMeta,
}) => {
  const fieldOptions = useMemo<FieldMetaOption[]>(
    () => buildFieldOptionsWithMeta(upstreamFields, fieldMeta),
    [upstreamFields, fieldMeta],
  );

  const conditions: ConditionItem[] =
    mode === 'branch' ? (value?.conditions || []) : buildFilterConditions(value);
  const defaultTarget: string = value?.defaultTarget || '';

  // CONDITION_BRANCH 顶层全局逻辑控制（条件项之间）
  const globalLogicEnabled: boolean = value?.globalLogicEnabled || false;
  const globalLogicOperator: string = value?.globalLogicOperator || 'and';

  // 折叠控制：默认第 1 个展开，其余折叠。用户手动切换时写入本地 state。
  const [activeConditionKeys, setActiveConditionKeys] = useState<string[]>(() =>
    conditions.length > 0 ? ['0'] : [],
  );

  const updateTopLevelGlobalLogic = (patch: Partial<{ globalLogicEnabled: boolean; globalLogicOperator: string }>) => {
    if (mode !== 'branch') return;
    onChange({ ...value, ...patch });
  };

  // ─── 操作 ────────────────────────────────────
  const updateConditions = (newConds: ConditionItem[]) => {
    if (mode === 'branch') {
      onChange({ ...value, conditions: newConds, defaultTarget });
    } else {
      onChange(syncFilterFromConditions(newConds, value));
    }
  };

  const addCondition = useClickDebounce(() => {
    const idx = conditions.length;
    const next = [
      ...conditions,
      {
        logic: 'and',
        rulesGlobalLogicEnabled: false,
        rulesGlobalLogicOperator: 'and',
        rules: [{ logic: 'and', field: '', operator: 'eq', value: '' }],
      },
    ];
    updateConditions(next);
    // 新增项默认展开
    setActiveConditionKeys((prev) => Array.from(new Set([...prev, String(idx)])));
  });

  const removeCondition = useClickDebounce((idx: number) => {
    const next = conditions.filter((_, i) => i !== idx);
    updateConditions(next);
    setActiveConditionKeys((prev) =>
      prev.filter((k) => k !== String(idx)).map((k) => {
        const n = Number(k);
        return String(n > idx ? n - 1 : n);
      }),
    );
  });

  const updateCondition = (idx: number, patch: Partial<ConditionItem>) => {
    const copy = [...conditions];
    copy[idx] = { ...copy[idx], ...patch };
    updateConditions(copy);
  };

  // Rules 操作
  const addRule = useClickDebounce((condIdx: number) => {
    const cond = conditions[condIdx];
    const rules = [...(cond.rules || []), { logic: 'and', field: '', operator: 'eq', value: '' }];
    updateCondition(condIdx, { rules });
  });

  const removeRule = useClickDebounce((condIdx: number, ruleIdx: number) => {
    const cond = conditions[condIdx];
    const rules = (cond.rules || []).filter((_, i) => i !== ruleIdx);
    updateCondition(condIdx, { rules });
  });

  const updateRule = (condIdx: number, ruleIdx: number, patch: Partial<Rule>) => {
    const cond = conditions[condIdx];
    const rules = [...(cond.rules || [])];
    rules[ruleIdx] = { ...rules[ruleIdx], ...patch };
    updateCondition(condIdx, { rules });
  };

  // 按字段类型裁剪当前规则可用的操作符列表
  const getOperatorsForRule = (field: string, currentOp?: string) => {
    const t = fieldTypes?.[field];
    const allowed = new Set(getOperatorsForFieldType(t || null));
    if (!t) return ALL_OPERATORS;
    return ALL_OPERATORS.filter(
      (op) => allowed.has(op.value) || (currentOp && op.value === currentOp),
    );
  };

  // 给 Panel 显示条件项的摘要（规则数、target、逻辑、negate 状态）
  const summarizeCondition = (cond: ConditionItem, idx: number): React.ReactNode => {
    const ruleCount = cond.rules?.length ?? 0;
    const firstField = cond.rules?.[0]?.field || cond.field || '未设置字段';
    const firstOp = cond.rules?.[0]?.operator || cond.operator || '';
    const firstValue = cond.rules?.[0]?.value ?? cond.value ?? '';
    return (
      <Space size={8} align="center" style={{ width: '100%', paddingRight: 8 }}>
        <Tag color="blue" style={{ marginInlineEnd: 0 }}>
          #{idx + 1}
        </Tag>
        {cond.negate && <Tag color="red">NOT</Tag>}
        <Text type="secondary" style={{ fontSize: 12 }}>
          {ruleCount} 条规则
        </Text>
        <Text ellipsis={{ tooltip: `${firstField} ${firstOp} ${firstValue}` }} style={{ fontSize: 12, maxWidth: 480 }}>
          <Text code>{firstField}</Text> {firstOp}{' '}
          {VALUE_LESS_OPERATORS.has(firstOp || '') ? '' : <Text code>{String(firstValue || '')}</Text>}
        </Text>
        {mode === 'branch' && (
          <Tag style={{ marginLeft: 'auto' }} color={cond.target ? 'geekblue' : 'default'}>
            目标: {cond.target || '未设置'}
          </Tag>
        )}
      </Space>
    );
  };

  // ─── 渲染 ────────────────────────────────────
  return (
    <div>
      {/* CONDITION_BRANCH：条件列表顶部全局逻辑控制 */}
      {mode === 'branch' && conditions.length > 1 && (
        <div
          className="condition-editor-global-logic-bar"
          style={{
            padding: '6px 10px',
            marginBottom: 8,
            background: globalLogicEnabled ? '#f0f7ff' : '#fafafa',
            border: '1px dashed ' + (globalLogicEnabled ? '#1890ff' : '#d9d9d9'),
            borderRadius: 4,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
          }}
        >
          <Switch
            size="small"
            checked={globalLogicEnabled}
            onChange={(v) => updateTopLevelGlobalLogic({ globalLogicEnabled: v })}
          />
          <Text strong style={{ fontSize: 12 }}>
            所有条件（条件项之间）使用统一逻辑：
          </Text>
          <Select
            size="small"
            style={{ width: 100 }}
            value={globalLogicOperator}
            options={LOGIC_OPTIONS}
            disabled={!globalLogicEnabled}
            onChange={(v) => updateTopLevelGlobalLogic({ globalLogicOperator: v })}
          />
          <Tooltip title="开启后，各条件项之前的独立 AND/OR 选择器将被忽略，统一使用此处设置的逻辑运算符。">
            <InfoCircleOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
          </Tooltip>
          <Space style={{ marginLeft: 'auto' }} size={4}>
            <Button
              size="small"
              type="link"
              onClick={() => setActiveConditionKeys(conditions.map((_, i) => String(i)))}
            >
              展开全部
            </Button>
            <Button
              size="small"
              type="link"
              onClick={() => setActiveConditionKeys([])}
            >
              折叠全部
            </Button>
          </Space>
        </div>
      )}

      <Collapse
        activeKey={activeConditionKeys}
        onChange={(keys) =>
          setActiveConditionKeys(Array.isArray(keys) ? (keys as string[]) : [keys as string])
        }
        expandIcon={({ isActive }) => (
          <CaretRightOutlined rotate={isActive ? 90 : 0} />
        )}
        style={{ background: 'transparent' }}
        ghost
      >
        {conditions.map((cond, ci) => {
          const rulesGlobalEnabled = !!cond.rulesGlobalLogicEnabled;
          const rulesGlobalOp = cond.rulesGlobalLogicOperator || 'and';
          const showCondLogicSelector = mode === 'branch' && ci > 0 && !globalLogicEnabled;
          const condLogicDisabled = mode === 'branch' && ci > 0 && globalLogicEnabled;

          return (
            <Panel
              key={String(ci)}
              header={summarizeCondition(cond, ci)}
              className="condition-editor-collapse-panel"
              style={{ marginBottom: 6 }}
              extra={
                <span
                  onClick={(e) => e.stopPropagation()}
                  style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}
                >
                  <Tooltip title={cond.rulesGlobalLogicEnabled ? `规则全局 ${rulesGlobalOp.toUpperCase()}` : '规则独立逻辑'}>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {cond.rulesGlobalLogicEnabled ? 'GLB' : 'IND'}
                    </Text>
                  </Tooltip>
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => removeCondition(ci)}
                  />
                </span>
              }
            >
              <div className="condition-editor-item" style={{ paddingTop: 4 }}>
                {/* 条件项之间的 AND/OR 连接符（仅 branch，且第 2 条及以后显示） */}
                {mode === 'branch' && ci > 0 && (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      margin: '6px 0 6px 8px',
                      gap: 8,
                    }}
                  >
                    <div
                      style={{
                        width: 2,
                        height: 12,
                        background: condLogicDisabled ? '#bae7ff' : '#d9d9d9',
                      }}
                    />
                    {showCondLogicSelector ? (
                      <Select
                        size="small"
                        style={{ width: 80 }}
                        value={cond.logic || 'and'}
                        options={LOGIC_OPTIONS}
                        onChange={(v) => updateCondition(ci, { logic: v })}
                      />
                    ) : (
                      <TagLikeLogic label={globalLogicOperator} locked />
                    )}
                    <div
                      style={{
                        flex: 1,
                        height: 1,
                        background: condLogicDisabled ? '#bae7ff' : '#f0f0f0',
                      }}
                    />
                  </div>
                )}

                {/* Header: index + logic + negate + target */}
                <div className="condition-header">
                  <Text className="condition-index" type="secondary" style={{ fontSize: 12 }}>
                    条件 #{ci + 1}
                  </Text>
                  {mode === 'filter' && (
                    <Select
                      size="small"
                      style={{ width: 80 }}
                      value={cond.logic || 'and'}
                      options={LOGIC_OPTIONS}
                      onChange={(v) => updateCondition(ci, { logic: v })}
                    />
                  )}
                  <Switch
                    size="small"
                    checked={cond.negate || false}
                    checkedChildren="NOT"
                    unCheckedChildren="NOT"
                    title="整体取反"
                    onChange={(v) => updateCondition(ci, { negate: v })}
                  />
                  {mode === 'branch' && (
                    <Input
                      size="small"
                      placeholder="目标节点 ID"
                      value={cond.target || ''}
                      onChange={(e) => updateCondition(ci, { target: e.target.value })}
                      style={{ flex: 1, maxWidth: 180 }}
                    />
                  )}
                </div>

                {/* Rules + 规则级全局逻辑控制 */}
                <div className="condition-editor-section-label" style={{ marginTop: 6 }}>
                  <Space size={6}>
                    <span>规则</span>
                    {(cond.rules?.length ?? 0) > 1 && (
                      <Space
                        size={4}
                        style={{
                          marginLeft: 8,
                          padding: '2px 6px',
                          background: rulesGlobalEnabled ? '#f0f7ff' : 'transparent',
                          border: '1px dashed ' + (rulesGlobalEnabled ? '#1890ff' : 'transparent'),
                          borderRadius: 4,
                        }}
                      >
                        <Switch
                          size="small"
                          checked={rulesGlobalEnabled}
                          onChange={(v) => updateCondition(ci, { rulesGlobalLogicEnabled: v })}
                        />
                        <Text style={{ fontSize: 11 }}>全局</Text>
                        <Select
                          size="small"
                          style={{ width: 80 }}
                          value={rulesGlobalOp}
                          options={LOGIC_OPTIONS}
                          disabled={!rulesGlobalEnabled}
                          onChange={(v) => updateCondition(ci, { rulesGlobalLogicOperator: v })}
                        />
                        <Tooltip title="开启后，所有规则之间强制使用此处的 AND/OR；关闭时每条规则的连接符可单独设置。">
                          <InfoCircleOutlined style={{ color: '#8c8c8c', fontSize: 11 }} />
                        </Tooltip>
                      </Space>
                    )}
                  </Space>
                </div>

                {(cond.rules || []).map((rule, ri) => {
                  const hideValue = VALUE_LESS_OPERATORS.has(rule.operator || '');
                  const isFirstRule = ri === 0;
                  const showRuleLogicConnector = !isFirstRule;
                  const ruleLogicLocked = rulesGlobalEnabled;
                  const opList = getOperatorsForRule(rule.field || '', rule.operator);
                  const t = fieldTypes?.[rule.field || ''];

                  return (
                    <div key={ri}>
                      {showRuleLogicConnector && (
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            margin: '4px 0 4px 20px',
                            gap: 8,
                          }}
                        >
                          <div
                            style={{
                              width: 2,
                              height: 8,
                              background: ruleLogicLocked ? '#bae7ff' : '#d9d9d9',
                            }}
                          />
                          {ruleLogicLocked ? (
                            <TagLikeLogic label={rulesGlobalOp} locked />
                          ) : (
                            <Select
                              size="small"
                              style={{ width: 80 }}
                              value={rule.logic || 'and'}
                              options={LOGIC_OPTIONS}
                              onChange={(v) => updateRule(ci, ri, { logic: v })}
                            />
                          )}
                          <div
                            style={{
                              flex: 1,
                              height: 1,
                              background: ruleLogicLocked ? '#bae7ff' : '#f0f0f0',
                            }}
                          />
                        </div>
                      )}
                      <div className="condition-editor-rule-row">
                        <AutoComplete
                          size="small"
                          style={{ flex: 2, minWidth: 0 }}
                          value={rule.field || ''}
                          options={fieldOptions as any}
                          placeholder="字段名"
                          onChange={(v) => updateRule(ci, ri, { field: v })}
                          filterOption={(input, opt) => {
                            const v = (opt as any)?.path ?? (opt as any)?.value;
                            return (v as string)?.toLowerCase().includes(input.toLowerCase()) ?? false;
                          }}
                        />
                        <Select
                          size="small"
                          style={{ flex: '0 0 110px', minWidth: 0 }}
                          value={rule.operator || 'eq'}
                          options={opList}
                          onChange={(v) => updateRule(ci, ri, { operator: v })}
                        />
                        {!hideValue && (
                          <Input
                            size="small"
                            style={{ flex: 2, minWidth: 0 }}
                            placeholder={t ? `值 (${t})` : '值'}
                            value={rule.value || ''}
                            onChange={(e) => updateRule(ci, ri, { value: e.target.value })}
                          />
                        )}
                        <Switch
                          size="small"
                          checked={rule.negate || false}
                          checkedChildren="NOT"
                          unCheckedChildren="NOT"
                          title="取反"
                          onChange={(v) => updateRule(ci, ri, { negate: v })}
                        />
                        <Button
                          size="small"
                          type="text"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => removeRule(ci, ri)}
                          style={{ flex: 'none' }}
                        />
                      </div>
                    </div>
                  );
                })}
                <Button
                  type="dashed"
                  size="small"
                  icon={<PlusOutlined />}
                  onClick={() => addRule(ci)}
                  style={{ width: '100%', marginTop: 2 }}
                >
                  添加规则
                </Button>

                {/* AviatorScript 表达式（高级，折叠） */}
                <details style={{ marginTop: 6 }}>
                  <summary style={{ fontSize: 11, color: '#8c8c8c', cursor: 'pointer' }}>
                    AviatorScript 表达式（高级，完全兼容 JEXL / SpEL）
                  </summary>
                  <Input.TextArea
                    size="small"
                    rows={2}
                    placeholder="AviatorScript 表达式（例：status == 'active'，完全兼容 JEXL 语法）"
                    value={cond.condition || ''}
                    onChange={(e) => updateCondition(ci, { condition: e.target.value })}
                    style={{ marginTop: 4, fontSize: 12 }}
                  />
                  <Space size="small" style={{ marginTop: 6 }}>
                    <Tooltip title="当前表达式会在编辑时立即同步到配置数据中，无需手动保存">
                      <Button
                        type="primary"
                        ghost
                        size="small"
                        icon={<CheckCircleOutlined />}
                      >
                        立即生效（实时写入）
                      </Button>
                    </Tooltip>
                    <Tooltip title="完全兼容 JEXL：== 等于、!= 不等于、&&/and 与、||/or 或、!/not 非、in 包含于、contains 包含、+/* 运算、fn.xxx() 调用内置函数">
                      <Button
                        type="default"
                        size="small"
                        icon={<BulbOutlined />}
                      >
                        语法提示
                      </Button>
                    </Tooltip>
                  </Space>
                </details>
              </div>
            </Panel>
          );
        })}
      </Collapse>

      <Button
        type="dashed"
        icon={<PlusOutlined />}
        onClick={addCondition}
        style={{ width: '100%', marginBottom: 8, marginTop: 4 }}
      >
        {mode === 'branch' ? '添加条件分支' : '添加过滤规则'}
      </Button>

      {/* defaultTarget（仅 CONDITION_BRANCH） */}
      {mode === 'branch' && (
        <div style={{ marginTop: 4 }}>
          <Text className="condition-editor-section-label">默认目标节点</Text>
          <Input
            size="small"
            placeholder="所有条件未匹配时的跳转目标"
            value={defaultTarget}
            onChange={(e) => onChange({ ...value, conditions, defaultTarget: e.target.value })}
          />
        </div>
      )}
    </div>
  );
};

// ─── 内部小组件：锁定状态的 AND/OR 标签 ───────────
const TagLikeLogic: React.FC<{ label: string; locked?: boolean }> = ({ label, locked }) => (
  <span
    style={{
      display: 'inline-block',
      padding: '0 8px',
      fontSize: 12,
      lineHeight: '22px',
      borderRadius: 4,
      border: '1px solid ' + (locked ? '#1890ff' : '#d9d9d9'),
      background: locked ? '#e6f7ff' : '#fafafa',
      color: locked ? '#096dd9' : '#595959',
      fontWeight: 600,
      userSelect: 'none',
    }}
    title={locked ? '全局逻辑模式：已使用统一设置' : undefined}
  >
    {label.toUpperCase()}
    {locked ? ' ▪' : ''}
  </span>
);

// ─── Filter 模式转换 ──────────────────────────────
// FILTER 顶层是 {logic, rules, field, operator, value, condition, rulesGlobalLogicEnabled, rulesGlobalLogicOperator}
// 转换为 ConditionItem[] 统一渲染

function buildFilterConditions(value: any): ConditionItem[] {
  if (!value) return [];
  if (value._filterConditions) return value._filterConditions as ConditionItem[];

  const cond: ConditionItem = {};
  cond.rulesGlobalLogicEnabled = !!value.rulesGlobalLogicEnabled;
  cond.rulesGlobalLogicOperator = value.rulesGlobalLogicOperator || 'and';

  if (value.rules && Array.isArray(value.rules) && value.rules.length > 0) {
    cond.logic = value.logic || 'and';
    cond.rules = value.rules;
  } else if (value.field || value.operator) {
    cond.field = value.field;
    cond.operator = value.operator;
    cond.value = value.value;
    cond.logic = 'and';
    cond.rules = [{ logic: 'and', field: value.field, operator: value.operator, value: value.value }];
  }
  if (value.condition) {
    cond.condition = value.condition;
  }
  if (value.negate !== undefined) {
    cond.negate = !!value.negate;
  }

  if (Object.keys(cond).length === 0) return [];
  return [cond];
}

function syncFilterFromConditions(conds: ConditionItem[], value: any): any {
  if (conds.length === 0) {
    return {};
  }
  const cond = conds[0];
  const result: any = { _filterConditions: conds };

  result.rulesGlobalLogicEnabled = !!cond.rulesGlobalLogicEnabled;
  result.rulesGlobalLogicOperator = cond.rulesGlobalLogicOperator || 'and';

  result.negate = cond.negate || false;
  if (cond.rules && cond.rules.length > 0) {
    result.logic = cond.logic || 'and';
    result.rules = cond.rules;
  }
  if (cond.condition) {
    result.condition = cond.condition;
  }

  return result;
}

export default ConditionConfigEditor;
