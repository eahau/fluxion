import { useMemo, useCallback } from 'react';
import {
  AutoComplete,
  Button,
  Input,
  InputNumber,
  Segmented,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  PlusOutlined,
  CloseOutlined,
  PlusCircleOutlined,
  ApartmentOutlined,
  PlusSquareOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import {
  getOperatorsForFieldType,
  VALUE_LESS_OPERATORS,
  SIZE_OPERATORS,
  type FieldType,
  type FieldSchemaMeta,
} from '@/utils/conditionSchema';
import { buildFieldOptionsWithMeta } from '@/pages/workflow/components/widgets/FieldAutoComplete';

const { Text } = Typography;

export interface Rule {
  name?: string;
  logic?: string;
  field?: string;
  operator?: string;
  value?: any;
  negate?: boolean;
  /** 对象数组 contains 时的匹配模式：any = 任一对象匹配，all = 所有对象匹配 */
  matchMode?: 'any' | 'all';
  /** 对象数组 contains 时指定的子字段路径 */
  subField?: string;
  /** 嵌套规则组：若存在且为数组，则本条为规则组而非叶子规则 */
  rules?: Rule[];
  /** 规则组内部是否启用统一强制逻辑 */
  globalLogicEnabled?: boolean;
  /** 规则组内部统一强制逻辑操作符 */
  globalLogicOperator?: string;
}

/** 判断一条 Rule 是否为规则组 */
export function isRuleGroup(rule?: Rule): rule is Rule & { rules: Rule[] } {
  return !!rule && Array.isArray(rule.rules);
}

interface CompactRulesEditorProps {
  value: Rule[];
  onChange: (rules: Rule[]) => void;
  /** 候选字段路径 */
  fields?: string[];
  /** 字段路径 → 字段类型 */
  fieldTypes?: Record<string, FieldType | string>;
  /** 字段路径 → 原始字段 schema（用于判断 enum / array items 等） */
  fieldSchemas?: Record<string, Record<string, any>>;
  fieldMeta?: Record<string, FieldSchemaMeta>;
  disabled?: boolean;
  readonly?: boolean;
  /** 容器级是否启用统一逻辑 */
  globalLogicEnabled?: boolean;
  /** 容器级统一逻辑操作符 */
  globalLogicOperator?: string;
  /** 容器级默认逻辑（独立连接模式下的默认值） */
  defaultLogic?: string;
}

const LOGIC_OPTIONS = [
  { value: 'and', label: 'AND' },
  { value: 'or', label: 'OR' },
];

const MATCH_MODE_OPTIONS = [
  { value: 'any', label: '任一匹配' },
  { value: 'all', label: '全部匹配' },
];

/** 最大嵌套深度（root 为 0） */
const MAX_DEPTH = 3;

const GROUP_COLORS = ['#1677ff', '#52c41a', '#fa8c16', '#722ed1'];

const OPERATOR_SHORT_LABEL: Record<string, string> = {
  eq: 'eq (等于)',
  gt: 'gt (大于)',
  gte: 'gte (大于等于)',
  lt: 'lt (小于)',
  lte: 'lte (小于等于)',
  in: 'in (在列表中)',
  contains: 'contains (包含)',
  isNull: 'isNull (为空)',
  isEmpty: 'isEmpty (空集合)',
  sizeEq: 'sizeEq (长度等于)',
  sizeGt: 'sizeGt (长度大于)',
  sizeGte: 'sizeGte (长度大于等于)',
  sizeLt: 'sizeLt (长度小于)',
  sizeLte: 'sizeLte (长度小于等于)',
  startsWith: 'startsWith (以...开头)',
  endsWith: 'endsWith (以...结尾)',
  regex: 'regex (正则匹配)',
};

/** 判断字段 schema 是否为枚举类型 */
function isEnumSchema(schema?: Record<string, any>): boolean {
  return !!schema && Array.isArray(schema.enum) && schema.enum.length > 0;
}

/** 判断字段 schema 是否为数组且元素是 primitive（非 object） */
function isPrimitiveArraySchema(schema?: Record<string, any>): boolean {
  if (!schema || schema.type !== 'array') return false;
  const items = schema.items;
  if (!items || typeof items !== 'object') return false;
  const itemType = Array.isArray(items.type) ? items.type : [items.type];
  return itemType.includes('string') || itemType.includes('number') || itemType.includes('integer') || itemType.includes('boolean');
}

/** 判断字段 schema 是否为数组且元素是 object */
function isObjectArraySchema(schema?: Record<string, any>): boolean {
  if (!schema || schema.type !== 'array') return false;
  const items = schema.items;
  if (!items || typeof items !== 'object') return false;
  const itemType = Array.isArray(items.type) ? items.type : [items.type];
  return itemType.includes('object') || !!items.properties;
}

/** 获取数组元素的 schema */
function getArrayItemSchema(schema?: Record<string, any>): Record<string, any> | undefined {
  if (!schema || schema.type !== 'array') return undefined;
  const items = schema.items;
  return items && typeof items === 'object' ? items : undefined;
}

/** 获取对象数组的子字段路径列表 */
function getObjectArraySubFields(schema?: Record<string, any>): string[] {
  const items = getArrayItemSchema(schema);
  if (!items) return [];
  const props = items.properties;
  if (!props || typeof props !== 'object') return [];
  return Object.keys(props);
}

/** 判断 schema 是否为布尔类型 */
function isBooleanSchema(schema?: Record<string, any>): boolean {
  if (!schema) return false;
  const types = Array.isArray(schema.type) ? schema.type : [schema.type];
  return types.includes('boolean');
}

/** 判断 schema 是否为数值类型 */
function isNumberSchema(schema?: Record<string, any>): boolean {
  if (!schema) return false;
  const types = Array.isArray(schema.type) ? schema.type : [schema.type];
  return types.includes('number') || types.includes('integer');
}

const formatRuleValue = (value: any): string => {
  if (value === undefined || value === null || value === '') return 'value';
  if (typeof value === 'string') return value;
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
};

const buildLeafRuleName = (rule: Partial<Rule>, fallbackIndex = 0): string => {
  const field = (rule.field || '').trim() || `规则${fallbackIndex + 1}`;
  const operator = (rule.operator || 'eq').toUpperCase();
  const value = formatRuleValue(rule.value);
  return `${field} ${operator} ${value}`.trim();
};

const buildGroupName = (rule: Partial<Rule>, fallbackIndex = 0): string => {
  const childRules = Array.isArray(rule.rules) ? rule.rules : [];
  if (childRules.length === 0) return `条件组${fallbackIndex + 1}`;
  const names = childRules.map((child, idx) => child.name || buildLeafRuleName(child, idx));
  const logic = (rule.logic || 'and').toUpperCase();
  return `${names.join(` ${logic} `)}`;
};

const createEmptyRule = (logic: string = 'and'): Rule => ({
  name: buildLeafRuleName({ logic, field: '', operator: 'eq', value: '' }),
  logic,
  field: '',
  operator: 'eq',
  value: '',
  negate: false,
});

const createEmptyGroup = (logic: string = 'and'): Rule => ({
  name: buildGroupName({ logic, rules: [createEmptyRule(logic)] }),
  logic,
  globalLogicEnabled: false,
  globalLogicOperator: 'and',
  rules: [createEmptyRule(logic)],
});

/**
 * 紧凑规则编辑器（支持嵌套规则组）。
 *
 * 相比 RJSF 默认表单：
 * - 每条规则压缩为一行，去掉冗余 label，空间利用率更高。
 * - 支持规则组嵌套，最多 3 层，可实现 (A && B) || (C && D) 等复杂逻辑。
 * - 根据字段类型动态过滤操作符。
 * - 根据字段类型 + 操作符动态切换 value 输入控件。
 */
const CompactRulesEditor: React.FC<CompactRulesEditorProps> = ({
  value = [],
  onChange,
  fields = [],
  fieldTypes = {},
  fieldSchemas = {},
  fieldMeta = {},
  disabled,
  readonly,
  globalLogicEnabled,
  globalLogicOperator,
  defaultLogic,
}) => {
  const fieldOptions = useMemo(
    () => buildFieldOptionsWithMeta(fields, fieldMeta) as any,
    [fields, fieldMeta],
  );

  const fallbackLogic = globalLogicEnabled
    ? globalLogicOperator || defaultLogic || 'and'
    : defaultLogic || 'and';

  const updateItem = useCallback(
    (idx: number, patch: Partial<Rule>) => {
      const next = value.map((r, i) => {
        if (i !== idx) return r;
        const merged = { ...r, ...patch };
        if (!patch.name && !r.name && !('name' in patch)) {
          merged.name = buildLeafRuleName(merged, idx);
        }
        return merged;
      });
      onChange(next);
    },
    [value, onChange],
  );

  const removeItem = useCallback(
    (idx: number) => {
      const next = value.filter((_, i) => i !== idx);
      onChange(next);
    },
    [value, onChange],
  );

  const addRule = useCallback(() => {
    const lastLogic = value.length > 0 ? value[value.length - 1]?.logic : undefined;
    onChange([...value, createEmptyRule(lastLogic || fallbackLogic)]);
  }, [value, onChange, fallbackLogic]);

  const addGroup = useCallback(() => {
    const lastLogic = value.length > 0 ? value[value.length - 1]?.logic : undefined;
    onChange([...value, createEmptyGroup(lastLogic || fallbackLogic)]);
  }, [value, onChange, fallbackLogic]);

  return (
    <div className="compact-rules-editor" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {value.length === 0 && (
        <div
          style={{
            padding: '24px 0',
            textAlign: 'center',
            color: '#8c8c8c',
            fontSize: 12,
            border: '1px dashed #d9d9d9',
            borderRadius: 6,
            background: '#fafafa',
          }}
        >
          暂无规则，点击「添加规则」开始
        </div>
      )}

      {value.map((rule, idx) => {
        const isFirst = idx === 0;
        const logicLocked = !!globalLogicEnabled;
        const effectiveLogic =
          (globalLogicEnabled ? globalLogicOperator : rule.logic || defaultLogic) || 'and';

        if (isRuleGroup(rule)) {
          return (
            <RuleGroupNode
              key={`group-${idx}`}
              value={rule}
              onChange={(next) => updateItem(idx, next)}
              onRemove={() => removeItem(idx)}
              depth={0}
              maxDepth={MAX_DEPTH}
              fields={fields}
              fieldTypes={fieldTypes}
              fieldSchemas={fieldSchemas}
              fieldMeta={fieldMeta}
              disabled={disabled}
              readonly={readonly}
            />
          );
        }

        // 单条叶子规则：横向卡片布局，奇偶行不同浅色背景，视觉区分
        const zebra = idx % 2 === 0 ? '#f7faff' : '#fffdf5';
        const accent = '#1677ff';
        return (
          <div
            key={`rule-${idx}`}
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              gap: '8px 8px',
              padding: '8px 10px',
              borderRadius: 8,
              border: `1px solid ${accent}22`,
              borderLeft: `3px solid ${accent}`,
              background: zebra,
              boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
            }}
          >
            {/* 1. 连接符 / IF Tag */}
            <div style={{ flex: '0 0 72px', minWidth: 64 }}>
              <div style={{ paddingTop: 2, textAlign: 'center' }}>
                {isFirst ? (
                  <Tag color="blue" style={{ margin: 0, fontSize: 12, fontWeight: 600 }}>
                    IF
                  </Tag>
                ) : logicLocked ? (
                  <Tooltip title={`统一强制 ${effectiveLogic.toUpperCase()}`}>
                    <Tag
                      color={effectiveLogic === 'or' ? 'orange' : 'success'}
                      style={{ margin: 0, fontSize: 12, fontWeight: 600 }}
                    >
                      {effectiveLogic.toUpperCase()}
                    </Tag>
                  </Tooltip>
                ) : (
                  <Select
                    size="small"
                    value={rule.logic || effectiveLogic}
                    options={LOGIC_OPTIONS}
                    onChange={(v) => updateItem(idx, { logic: v })}
                    disabled={disabled || readonly}
                    style={{ width: '100%' }}
                  />
                )}
              </div>
            </div>

            {/* 2. 规则名称 / 字段路径 */}
            <div style={{ flex: '1 1 220px', minWidth: 180, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <Input
                size="small"
                value={rule.name || ''}
                placeholder={buildLeafRuleName(rule, idx)}
                onChange={(e) => updateItem(idx, { name: e.target.value })}
                disabled={disabled || readonly}
              />
              <AutoComplete
                size="small"
                value={rule.field || ''}
                options={fieldOptions}
                placeholder="字段路径"
                onChange={(nextField) => {
                  const nextType = (fieldTypes[nextField] as FieldType) || 'unknown';
                  const nextSchema = fieldSchemas[nextField] || {};
                  const nextOps = getOperatorsForFieldType(nextType);
                  let nextOperator = rule.operator || '';
                  if (!nextOps.includes(nextOperator)) nextOperator = nextOps[0] || 'eq';
                  if (isEnumSchema(nextSchema)) {
                    const enumOps = nextOps.filter((op) => ['eq', 'in', 'isNull'].includes(op));
                    if (!enumOps.includes(nextOperator)) nextOperator = enumOps[0] || 'eq';
                  }
                  updateItem(idx, {
                    field: nextField,
                    operator: nextOperator,
                    value: undefined,
                    subField: undefined,
                    matchMode: undefined,
                    name: rule.name || buildLeafRuleName({ ...rule, field: nextField, operator: nextOperator }, idx),
                  });
                }}
                disabled={disabled || readonly}
                style={{ width: '100%' }}
                filterOption={(input, opt) => ((opt as any)?.path ?? (opt as any)?.value)?.toLowerCase().includes(input.toLowerCase()) ?? false}
              />
            </div>

            {/* 2. 字段路径 */}
            <div style={{ flex: '1 1 180px', minWidth: 140, display: 'none' }}>
              <AutoComplete
                size="small"
                value={rule.field || ''}
                options={fieldOptions}
                placeholder="字段路径"
                onChange={(nextField) => {
                  const nextType = (fieldTypes[nextField] as FieldType) || 'unknown';
                  const nextSchema = fieldSchemas[nextField] || {};
                  const nextOps = getOperatorsForFieldType(nextType);
                  let nextOperator = rule.operator || '';
                  if (!nextOps.includes(nextOperator)) nextOperator = nextOps[0] || 'eq';
                  if (isEnumSchema(nextSchema)) {
                    const enumOps = nextOps.filter((op) => ['eq', 'in', 'isNull'].includes(op));
                    if (!enumOps.includes(nextOperator)) nextOperator = enumOps[0] || 'eq';
                  }
                  updateItem(idx, {
                    field: nextField,
                    operator: nextOperator,
                    value: undefined,
                    subField: undefined,
                    matchMode: undefined,
                  });
                }}
                disabled={disabled || readonly}
                style={{ width: '100%' }}
                filterOption={(input, opt) => ((opt as any)?.path ?? (opt as any)?.value)?.toLowerCase().includes(input.toLowerCase()) ?? false}
              />
            </div>

            {/* 3. 操作符 */}
            <div style={{ flex: '0 0 120px', minWidth: 100 }}>
              {(() => {
                const f = rule.field || '';
                const ft = (fieldTypes[f] as FieldType) || 'unknown';
                const fs = fieldSchemas[f] || {};
                let ops = getOperatorsForFieldType(ft);
                if (isEnumSchema(fs)) ops = ops.filter((op) => ['eq', 'in', 'isNull'].includes(op));
                if (isObjectArraySchema(fs))
                  ops = ops.filter((op) =>
                    ['contains', 'isNull', 'isEmpty', 'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte'].includes(op),
                  );
                if (isPrimitiveArraySchema(fs))
                  ops = ops.filter((op) =>
                    ['in', 'contains', 'isNull', 'isEmpty', 'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte'].includes(op),
                  );
                const opOptions = ops.map((op) => ({
                  value: op,
                  label: OPERATOR_SHORT_LABEL[op] || op,
                }));
                const curOp = rule.operator || '';
                const oldVt = (() => {
                  if (VALUE_LESS_OPERATORS.has(curOp) || !curOp) return 'none';
                  if (SIZE_OPERATORS.has(curOp)) return 'number';
                  if (curOp === 'in') {
                    if (isEnumSchema(fs) || isBooleanSchema(fs)) return 'enum-multi';
                    return 'tags';
                  }
                  if (isEnumSchema(fs)) return 'enum';
                  if (isBooleanSchema(fs)) return 'boolean';
                  if (isNumberSchema(fs)) return 'number';
                  if (isPrimitiveArraySchema(fs) && curOp === 'contains') return 'tags';
                  if (isObjectArraySchema(fs) && curOp === 'contains') return 'object-array-contains';
                  return 'text';
                })();
                return (
                  <Select
                    size="small"
                    value={curOp}
                    options={opOptions}
                    placeholder="操作符"
                    onChange={(nextOperator: string) => {
                      const patch: any = { operator: nextOperator };
                      const newVt = (() => {
                        if (VALUE_LESS_OPERATORS.has(nextOperator) || !nextOperator) return 'none';
                        if (SIZE_OPERATORS.has(nextOperator)) return 'number';
                        if (nextOperator === 'in') {
                          if (isEnumSchema(fs) || isBooleanSchema(fs)) return 'enum-multi';
                          return 'tags';
                        }
                        if (isEnumSchema(fs)) return 'enum';
                        if (isBooleanSchema(fs)) return 'boolean';
                        if (isNumberSchema(fs)) return 'number';
                        if (isPrimitiveArraySchema(fs) && nextOperator === 'contains') return 'tags';
                        if (isObjectArraySchema(fs) && nextOperator === 'contains') return 'object-array-contains';
                        return 'text';
                      })();
                      if (oldVt !== newVt) patch.value = undefined;
                      updateItem(idx, patch);
                    }}
                    disabled={disabled || readonly}
                    style={{ width: '100%' }}
                  />
                );
              })()}
            </div>

            {/* 4. 值 (含子字段/匹配模式) */}
            <div style={{ flex: '2 1 200px', minWidth: 140 }}>
              <RuleValueInput
                rule={rule}
                fieldSchema={fieldSchemas[rule.field || ''] || {}}
                fieldType={(fieldTypes[rule.field || ''] as FieldType) || 'unknown'}
                disabled={disabled}
                readonly={readonly}
                onChange={(patch) => updateItem(idx, patch)}
              />
            </div>

            {/* 5. NOT 取反 + 6. 删除 (右对齐区) */}
            <div
              style={{
                flex: '0 0 auto',
                marginLeft: 'auto',
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                paddingLeft: 4,
              }}
            >
              <Tooltip title="NOT 取反">
                <Switch
                  size="small"
                  checked={!!rule.negate}
                  checkedChildren="NOT"
                  unCheckedChildren="NOT"
                  disabled={disabled || readonly}
                  onChange={(v) => updateItem(idx, { negate: v })}
                />
              </Tooltip>
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                disabled={disabled || readonly}
                onClick={() => removeItem(idx)}
              />
            </div>
          </div>
        );
      })}

      {!readonly && (
        <Space.Compact style={{ width: '100%', marginTop: 6 }}>
          <Button
            type="dashed"
            size="small"
            icon={<PlusCircleOutlined />}
            onClick={addRule}
            disabled={disabled}
            style={{ width: '50%', fontWeight: 600, height: 30 }}
          >
            添加规则
          </Button>
          <Button
            type="default"
            size="small"
            icon={<ApartmentOutlined />}
            onClick={addGroup}
            disabled={disabled}
            style={{ width: '50%', fontWeight: 600, height: 30, color: '#1677ff' }}
          >
            添加条件组
          </Button>
        </Space.Compact>
      )}
    </div>
  );
};

interface RuleGroupNodeProps {
  value: Rule;
  onChange: (group: Rule) => void;
  onRemove?: () => void;
  depth: number;
  maxDepth: number;
  fields?: string[];
  fieldTypes?: Record<string, FieldType | string>;
  fieldSchemas?: Record<string, Record<string, any>>;
  fieldMeta?: Record<string, FieldSchemaMeta>;
  disabled?: boolean;
  readonly?: boolean;
}

const RuleGroupNode: React.FC<RuleGroupNodeProps> = ({
  value,
  onChange,
  onRemove,
  depth,
  maxDepth,
  fields = [],
  fieldTypes = {},
  fieldSchemas = {},
  fieldMeta = {},
  disabled,
  readonly,
}) => {
  const fieldOptions = useMemo(
    () => buildFieldOptionsWithMeta(fields, fieldMeta) as any,
    [fields, fieldMeta],
  );

  const groupLogic = value.logic || 'and';
  const groupGlobalEnabled = !!value.globalLogicEnabled;
  const groupGlobalOperator = value.globalLogicOperator || groupLogic || 'and';
  const fallbackLogic = groupGlobalEnabled
    ? groupGlobalOperator
    : groupLogic || 'and';

  const rules = value.rules || [];
  const canAddSubGroup = depth < maxDepth;
  const borderColor = GROUP_COLORS[depth % GROUP_COLORS.length];

  const updateChild = useCallback(
    (idx: number, patch: Partial<Rule>) => {
      const nextRules = rules.map((r, i) => (i === idx ? { ...r, ...patch } : r));
      onChange({ ...value, rules: nextRules });
    },
    [rules, value, onChange],
  );

  const removeChild = useCallback(
    (idx: number) => {
      const nextRules = rules.filter((_, i) => i !== idx);
      onChange({ ...value, rules: nextRules });
    },
    [rules, value, onChange],
  );

  const addRule = useCallback(() => {
    const lastLogic = rules.length > 0 ? rules[rules.length - 1]?.logic : undefined;
    onChange({ ...value, rules: [...rules, createEmptyRule(lastLogic || fallbackLogic)] });
  }, [rules, value, onChange, fallbackLogic]);

  const addGroup = useCallback(() => {
    const lastLogic = rules.length > 0 ? rules[rules.length - 1]?.logic : undefined;
    onChange({ ...value, rules: [...rules, createEmptyGroup(lastLogic || fallbackLogic)] });
  }, [rules, value, onChange, fallbackLogic]);

  return (
    <div
      style={{
        marginBottom: 4,
        border: `1px solid ${borderColor}55`,
        borderLeft: `4px solid ${borderColor}`,
        borderRadius: 10,
        background: '#fff',
        boxShadow: '0 1px 4px rgba(0,0,0,0.04)',
        overflow: 'hidden',
      }}
    >
      {/* 组头部：大色块 + 显眼的 AND/OR 逻辑切换 */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          padding: '10px 12px',
          background: `linear-gradient(90deg, ${borderColor}14 0%, ${borderColor}06 100%)`,
          borderBottom: `1px solid ${borderColor}22`,
        }}
      >
        <Space size={10} wrap align="center">
          <Tag
            color={borderColor}
            style={{
              margin: 0,
              fontSize: 12,
              fontWeight: 700,
              padding: '2px 10px',
              borderRadius: 4,
            }}
          >
            {depth === 0 ? '📦 顶层条件组' : `🗂️ 条件组 L${depth}`}
          </Tag>

          {/* 显眼的 AND/OR 逻辑选择 */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '3px 4px',
              background: groupGlobalEnabled ? `${borderColor}12` : '#fafafa',
              border: `1px solid ${groupGlobalEnabled ? borderColor : '#e5e7eb'}`,
              borderRadius: 6,
            }}
          >
            <Text strong style={{ fontSize: 12, color: '#4b5563' }}>
              组合逻辑
            </Text>
            {groupGlobalEnabled ? (
              <Tooltip title="已启用「统一强制」，全局使用此逻辑">
                <Segmented
                  size="small"
                  value={groupGlobalOperator}
                  options={[
                    {
                      value: 'and',
                      label: (
                        <span
                          style={{
                            fontWeight: 700,
                            color:
                              groupGlobalOperator === 'and' ? '#1677ff' : undefined,
                          }}
                        >
                          AND
                        </span>
                      ),
                    },
                    {
                      value: 'or',
                      label: (
                        <span
                          style={{
                            fontWeight: 700,
                            color:
                              groupGlobalOperator === 'or' ? '#fa8c16' : undefined,
                          }}
                        >
                          OR
                        </span>
                      ),
                    },
                  ]}
                  disabled={disabled || readonly}
                  onChange={(v) => onChange({ ...value, globalLogicOperator: v })}
                />
              </Tooltip>
            ) : (
              <Select
                size="small"
                value={groupLogic}
                options={[
                  {
                    value: 'and',
                    label: (
                      <span style={{ fontWeight: 700, color: '#1677ff' }}>AND</span>
                    ),
                  },
                  {
                    value: 'or',
                    label: (
                      <span style={{ fontWeight: 700, color: '#fa8c16' }}>OR</span>
                    ),
                  },
                ]}
                disabled={disabled || readonly}
                onChange={(v) => onChange({ ...value, logic: v })}
                style={{ width: 96 }}
                tagRender={(props) => (
                  <Tag
                    color={props.value === 'and' ? 'blue' : 'orange'}
                    style={{ margin: 0, fontWeight: 700 }}
                  >
                    {props.label}
                  </Tag>
                )}
              />
            )}
          </div>

          <Space size={4} align="center">
            <Switch
              size="small"
              checked={groupGlobalEnabled}
              disabled={disabled || readonly}
              onChange={(checked) =>
                onChange({
                  ...value,
                  globalLogicEnabled: checked,
                  globalLogicOperator: checked
                    ? value.globalLogicOperator || value.logic || 'and'
                    : value.globalLogicOperator,
                })
              }
            />
            <Text
              style={{
                fontSize: 12,
                color: groupGlobalEnabled ? borderColor : '#8c8c8c',
                fontWeight: groupGlobalEnabled ? 600 : 400,
              }}
            >
              统一强制
            </Text>
          </Space>
        </Space>

        <Space size={6} wrap>
          {!readonly && (
            <>
              <Button
                type="dashed"
                size="small"
                icon={<PlusSquareOutlined />}
                disabled={disabled}
                onClick={addRule}
                style={{ fontWeight: 600, height: 30 }}
              >
                添加条件
              </Button>
              <Button
                type="default"
                size="small"
                icon={<ApartmentOutlined />}
                disabled={disabled || !canAddSubGroup}
                onClick={addGroup}
                style={{ fontWeight: 600, height: 30, color: '#1677ff' }}
              >
                添加子组
              </Button>
            </>
          )}
          {onRemove && !readonly && (
            <Button
              type="text"
              size="small"
              danger
              icon={<CloseOutlined />}
              disabled={disabled}
              onClick={onRemove}
            >
              删除组
            </Button>
          )}
        </Space>
      </div>

      {/* 子规则/子组列表 - 横向卡片 + 交替色 */}
      <div style={{ padding: '10px 10px 4px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {rules.length === 0 && (
          <div
            style={{
              padding: '16px 0',
              textAlign: 'center',
              color: '#8c8c8c',
              fontSize: 12,
              border: '1px dashed #d9d9d9',
              borderRadius: 6,
              background: '#fafafa',
              marginBottom: 4,
            }}
          >
            条件组为空，点击「添加条件」或「添加子组」
          </div>
        )}

        {rules.map((rule, idx) => {
          const isFirst = idx === 0;
          const logicLocked = groupGlobalEnabled;
          const effectiveLogic =
            (groupGlobalEnabled ? groupGlobalOperator : rule.logic || groupLogic) || 'and';

          if (isRuleGroup(rule)) {
            return (
              <RuleGroupNode
                key={`group-${depth}-${idx}`}
                value={rule}
                onChange={(next) => updateChild(idx, next)}
                onRemove={() => removeChild(idx)}
                depth={depth + 1}
                maxDepth={maxDepth}
                fields={fields}
                fieldTypes={fieldTypes}
                fieldSchemas={fieldSchemas}
                fieldMeta={fieldMeta}
                disabled={disabled}
                readonly={readonly}
              />
            );
          }

          // 单条叶子规则：与顶层渲染一致 - 横向卡片 + 交替背景色
          const zebra = idx % 2 === 0 ? `${borderColor}0a` : '#fffdf5';
          return (
            <div
              key={`rule-${depth}-${idx}`}
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                gap: '8px 8px',
                padding: '8px 10px',
                borderRadius: 8,
                border: `1px solid ${borderColor}33`,
                borderLeft: `3px solid ${borderColor}`,
                background: zebra,
                boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
              }}
            >
              {/* 1. 连接符 / IF Tag */}
              <div style={{ flex: '0 0 72px', minWidth: 64 }}>
                <div style={{ paddingTop: 2, textAlign: 'center' }}>
                  {isFirst ? (
                    <Tag color={borderColor} style={{ margin: 0, fontSize: 12, fontWeight: 600 }}>
                      IF
                    </Tag>
                  ) : logicLocked ? (
                    <Tooltip title={`统一强制 ${effectiveLogic.toUpperCase()}`}>
                      <Tag
                        color={effectiveLogic === 'or' ? 'orange' : 'success'}
                        style={{ margin: 0, fontSize: 12, fontWeight: 600 }}
                      >
                        {effectiveLogic.toUpperCase()}
                      </Tag>
                    </Tooltip>
                  ) : (
                    <Select
                      size="small"
                      value={rule.logic || effectiveLogic}
                      options={LOGIC_OPTIONS}
                      onChange={(v) => updateChild(idx, { logic: v })}
                      disabled={disabled || readonly}
                      style={{ width: '100%' }}
                    />
                  )}
                </div>
              </div>

              {/* 2. 规则名称 / 字段路径 */}
              <div style={{ flex: '1 1 220px', minWidth: 180, display: 'flex', flexDirection: 'column', gap: 4 }}>
                <Input
                  size="small"
                  value={rule.name || ''}
                  placeholder={buildLeafRuleName(rule, idx)}
                  onChange={(e) => updateChild(idx, { name: e.target.value })}
                  disabled={disabled || readonly}
                />
                <AutoComplete
                  size="small"
                  value={rule.field || ''}
                  options={fieldOptions}
                  placeholder="字段路径"
                  onChange={(nextField: string) => {
                    const nextType = (fieldTypes[nextField] as FieldType) || 'unknown';
                    const nextSchema = fieldSchemas[nextField] || {};
                    const nextOps = getOperatorsForFieldType(nextType);
                    let nextOperator = rule.operator || '';
                    if (!nextOps.includes(nextOperator)) nextOperator = nextOps[0] || 'eq';
                    if (isEnumSchema(nextSchema)) {
                      const enumOps = nextOps.filter((op) => ['eq', 'in', 'isNull'].includes(op));
                      if (!enumOps.includes(nextOperator)) nextOperator = enumOps[0] || 'eq';
                    }
                    updateChild(idx, {
                      field: nextField,
                      operator: nextOperator,
                      value: undefined,
                      subField: undefined,
                      matchMode: undefined,
                      name: rule.name || buildLeafRuleName({ ...rule, field: nextField, operator: nextOperator }, idx),
                    });
                  }}
                  disabled={disabled || readonly}
                  style={{ width: '100%' }}
                  filterOption={(input, opt) => ((opt as any)?.path ?? (opt as any)?.value)?.toLowerCase().includes(input.toLowerCase()) ?? false}
                />
              </div>

              {/* 2. 字段路径 */}
              <div style={{ flex: '1 1 180px', minWidth: 140, display: 'none' }}>
                <AutoComplete
                  size="small"
                  value={rule.field || ''}
                  options={fieldOptions}
                  placeholder="字段路径"
                  onChange={(nextField: string) => {
                    const nextType = (fieldTypes[nextField] as FieldType) || 'unknown';
                    const nextSchema = fieldSchemas[nextField] || {};
                    const nextOps = getOperatorsForFieldType(nextType);
                    let nextOperator = rule.operator || '';
                    if (!nextOps.includes(nextOperator)) nextOperator = nextOps[0] || 'eq';
                    if (isEnumSchema(nextSchema)) {
                      const enumOps = nextOps.filter((op) => ['eq', 'in', 'isNull'].includes(op));
                      if (!enumOps.includes(nextOperator)) nextOperator = enumOps[0] || 'eq';
                    }
                    updateChild(idx, {
                      field: nextField,
                      operator: nextOperator,
                      value: undefined,
                      subField: undefined,
                      matchMode: undefined,
                    });
                  }}
                  disabled={disabled || readonly}
                  style={{ width: '100%' }}
                  filterOption={(input, opt) => ((opt as any)?.path ?? (opt as any)?.value)?.toLowerCase().includes(input.toLowerCase()) ?? false}
                />
              </div>

              {/* 3. 操作符 */}
              <div style={{ flex: '0 0 120px', minWidth: 100 }}>
                {(() => {
                  const f = rule.field || '';
                  const ft = (fieldTypes[f] as FieldType) || 'unknown';
                  const fs = fieldSchemas[f] || {};
                  let ops = getOperatorsForFieldType(ft);
                  if (isEnumSchema(fs)) ops = ops.filter((op) => ['eq', 'in', 'isNull'].includes(op));
                  if (isObjectArraySchema(fs))
                    ops = ops.filter((op) =>
                      ['contains', 'isNull', 'isEmpty', 'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte'].includes(op),
                    );
                  if (isPrimitiveArraySchema(fs))
                    ops = ops.filter((op) =>
                      ['in', 'contains', 'isNull', 'isEmpty', 'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte'].includes(op),
                    );
                  const opOptions = ops.map((op) => ({
                    value: op,
                    label: OPERATOR_SHORT_LABEL[op] || op,
                  }));
                  const curOp = rule.operator || '';
                  const getVT = (op: string) => {
                    if (VALUE_LESS_OPERATORS.has(op) || !op) return 'none';
                    if (SIZE_OPERATORS.has(op)) return 'number';
                    if (op === 'in') {
                      if (isEnumSchema(fs) || isBooleanSchema(fs)) return 'enum-multi';
                      return 'tags';
                    }
                    if (isEnumSchema(fs)) return 'enum';
                    if (isBooleanSchema(fs)) return 'boolean';
                    if (isNumberSchema(fs)) return 'number';
                    if (isPrimitiveArraySchema(fs) && op === 'contains') return 'tags';
                    if (isObjectArraySchema(fs) && op === 'contains') return 'object-array-contains';
                    return 'text';
                  };
                  const oldVt = getVT(curOp);
                  return (
                    <Select
                      size="small"
                      value={curOp}
                      options={opOptions}
                      placeholder="操作符"
                      onChange={(nextOp: string) => {
                        const patch: any = { operator: nextOp };
                        if (oldVt !== getVT(nextOp)) patch.value = undefined;
                        updateChild(idx, patch);
                      }}
                      disabled={disabled || readonly}
                      style={{ width: '100%' }}
                    />
                  );
                })()}
              </div>

              {/* 4. 值 + 子字段/匹配模式 */}
              <div style={{ flex: '2 1 200px', minWidth: 140 }}>
                <RuleValueInput
                  rule={rule}
                  fieldSchema={fieldSchemas[rule.field || ''] || {}}
                  fieldType={(fieldTypes[rule.field || ''] as FieldType) || 'unknown'}
                  disabled={disabled}
                  readonly={readonly}
                  onChange={(patch) => updateChild(idx, patch)}
                />
              </div>

              {/* 5. NOT + 6. 删除 (右对齐) */}
              <div
                style={{
                  flex: '0 0 auto',
                  marginLeft: 'auto',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  paddingLeft: 4,
                }}
              >
                <Tooltip title="NOT 取反">
                  <Switch
                    size="small"
                    checked={!!rule.negate}
                    checkedChildren="NOT"
                    unCheckedChildren="NOT"
                    disabled={disabled || readonly}
                    onChange={(v) => updateChild(idx, { negate: v })}
                  />
                </Tooltip>
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  disabled={disabled || readonly}
                  onClick={() => removeChild(idx)}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

interface CompactRuleRowProps {
  index: number;
  rule: Rule;
  isFirst: boolean;
  logicLocked: boolean;
  effectiveLogic: string;
  fieldOptions: { value: string; label: string }[];
  fieldTypes: Record<string, FieldType | string>;
  fieldSchemas: Record<string, Record<string, any>>;
  disabled?: boolean;
  readonly?: boolean;
  onChange: (idx: number, patch: Partial<Rule>) => void;
  onRemove: (idx: number) => void;
}

const CompactRuleRow: React.FC<CompactRuleRowProps> = ({
  index,
  rule,
  isFirst,
  logicLocked,
  effectiveLogic,
  fieldOptions,
  fieldTypes,
  fieldSchemas,
  disabled,
  readonly,
  onChange,
  onRemove,
}) => {
  const field = rule.field || '';
  const fieldType = (fieldTypes[field] as FieldType) || 'unknown';
  const fieldSchema = fieldSchemas[field] || {};
  const operator = rule.operator || '';

  const allowedOperators = useMemo(() => {
    let ops = getOperatorsForFieldType(fieldType);
    // 枚举字段：去掉字符串特化的 contains/startsWith/endsWith/regex，保留 in/eq
    if (isEnumSchema(fieldSchema)) {
      ops = ops.filter((op) => ['eq', 'in', 'isNull'].includes(op));
    }
    // 对象数组：contains 语义扩展为子字段匹配
    if (isObjectArraySchema(fieldSchema)) {
      ops = ops.filter((op) => ['contains', 'isNull', 'isEmpty', 'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte'].includes(op));
    }
    // 基础类型数组：只保留 in/contains/isNull/isEmpty/size 系列
    if (isPrimitiveArraySchema(fieldSchema)) {
      ops = ops.filter((op) => ['in', 'contains', 'isNull', 'isEmpty', 'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte'].includes(op));
    }
    return ops.map((op) => ({ value: op, label: OPERATOR_SHORT_LABEL[op] || op }));
  }, [fieldType, fieldSchema]);

  const handleFieldChange = useCallback(
    (nextField: string) => {
      const nextType = (fieldTypes[nextField] as FieldType) || 'unknown';
      const nextSchema = fieldSchemas[nextField] || {};
      const nextOps = getOperatorsForFieldType(nextType);
      let nextOperator = rule.operator || '';

      // 如果当前操作符在新字段类型下不可用，则自动切换为第一个可用操作符
      if (!nextOps.includes(nextOperator)) {
        nextOperator = nextOps[0] || 'eq';
      }

      // 枚举字段额外过滤
      if (isEnumSchema(nextSchema)) {
        const enumOps = nextOps.filter((op) => ['eq', 'in', 'isNull'].includes(op));
        if (!enumOps.includes(nextOperator)) nextOperator = enumOps[0] || 'eq';
      }

      // 切换字段时清空 value，避免旧值类型不匹配
      onChange(index, {
        field: nextField,
        operator: nextOperator,
        value: undefined,
        subField: undefined,
        matchMode: undefined,
      });
    },
    [fieldTypes, fieldSchemas, rule.operator, index, onChange],
  );

  const handleOperatorChange = useCallback(
    (nextOperator: string) => {
      const patch: Partial<Rule> = { operator: nextOperator };
      // 切换操作符时，如果新旧操作符的 value 类型差异大，也重置 value
      const oldValueType = getValueInputType(operator, fieldSchema);
      const newValueType = getValueInputType(nextOperator, fieldSchema);
      if (oldValueType !== newValueType) {
        patch.value = undefined;
      }
      onChange(index, patch);
    },
    [operator, fieldSchema, index, onChange],
  );

  return (
    <>
      {/* 连接符 */}
      <div style={{ paddingTop: 5, textAlign: 'center' }}>
        {isFirst ? (
          <Tag color="blue" style={{ margin: 0, fontSize: 11 }}>
            IF
          </Tag>
        ) : logicLocked ? (
          <Tooltip title={`统一强制 ${effectiveLogic.toUpperCase()}`}>
            <Tag
              color={effectiveLogic === 'or' ? 'orange' : 'success'}
              style={{ margin: 0, fontSize: 11 }}
            >
              {effectiveLogic.toUpperCase()}
            </Tag>
          </Tooltip>
        ) : (
          <Select
            size="small"
            value={rule.logic || effectiveLogic}
            options={LOGIC_OPTIONS}
            onChange={(v) => onChange(index, { logic: v })}
            disabled={disabled || readonly}
            style={{ width: '100%' }}
          />
        )}
      </div>

      {/* 字段 */}
      <div>
        <AutoComplete
          size="small"
          value={field}
          options={fieldOptions}
          placeholder="字段路径"
          onChange={handleFieldChange}
          disabled={disabled || readonly}
          style={{ width: '100%' }}
          filterOption={(input, opt) =>
            (opt?.value as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
          }
        />
      </div>

      {/* 操作符 */}
      <div>
        <Select
          size="small"
          value={operator}
          options={allowedOperators}
          placeholder="操作符"
          onChange={handleOperatorChange}
          disabled={disabled || readonly}
          style={{ width: '100%' }}
        />
      </div>

      {/* 值 + 子字段/匹配模式 */}
      <div>
        <RuleValueInput
          rule={rule}
          fieldSchema={fieldSchema}
          fieldType={fieldType}
          disabled={disabled}
          readonly={readonly}
          onChange={(patch) => onChange(index, patch)}
        />
      </div>

      {/* 取反 */}
      <div style={{ paddingTop: 2, textAlign: 'center' }}>
        <Tooltip title="NOT 取反">
          <Switch
            size="small"
            checked={!!rule.negate}
            checkedChildren="NOT"
            unCheckedChildren="NOT"
            disabled={disabled || readonly}
            onChange={(v) => onChange(index, { negate: v })}
          />
        </Tooltip>
      </div>

      {/* 删除 */}
      <div style={{ paddingTop: 3, textAlign: 'center' }}>
        <Button
          type="text"
          size="small"
          danger
          icon={<DeleteOutlined />}
          disabled={disabled || readonly}
          onClick={() => onRemove(index)}
        />
      </div>
    </>
  );
};

/** 判断某个操作符 + 字段类型应该使用哪种 value 输入 */
function getValueInputType(operator: string, fieldSchema: Record<string, any>): string {
  if (VALUE_LESS_OPERATORS.has(operator) || !operator) return 'none';
  if (SIZE_OPERATORS.has(operator)) return 'number';
  // in 操作符统一使用多值输入
  if (operator === 'in') {
    if (isEnumSchema(fieldSchema)) return 'enum-multi';
    if (isBooleanSchema(fieldSchema)) return 'enum-multi';
    return 'tags';
  }
  if (isEnumSchema(fieldSchema)) return 'enum';
  if (isBooleanSchema(fieldSchema)) return 'boolean';
  if (isNumberSchema(fieldSchema)) return 'number';
  if (isPrimitiveArraySchema(fieldSchema) && operator === 'contains') return 'tags';
  if (isObjectArraySchema(fieldSchema) && operator === 'contains') return 'object-array-contains';
  return 'text';
}

interface RuleValueInputProps {
  rule: Rule;
  fieldSchema: Record<string, any>;
  fieldType: FieldType;
  disabled?: boolean;
  readonly?: boolean;
  onChange: (patch: Partial<Rule>) => void;
}

const RuleValueInput: React.FC<RuleValueInputProps> = ({
  rule,
  fieldSchema,
  fieldType,
  disabled,
  readonly,
  onChange,
}) => {
  const operator = rule.operator || '';
  const inputType = getValueInputType(operator, fieldSchema);

  if (inputType === 'none') {
    return (
      <Text type="secondary" style={{ fontSize: 12, lineHeight: '24px' }}>
        无需填写
      </Text>
    );
  }

  if (inputType === 'number') {
    return (
      <InputNumber
        size="small"
        style={{ width: '100%' }}
        value={rule.value}
        placeholder={SIZE_OPERATORS.has(operator) ? '长度' : '数值'}
        disabled={disabled || readonly}
        onChange={(v) => onChange({ value: v })}
      />
    );
  }

  if (inputType === 'boolean') {
    return (
      <Select
        size="small"
        style={{ width: '100%' }}
        value={rule.value}
        options={[
          { value: true, label: 'true' },
          { value: false, label: 'false' },
        ]}
        placeholder="选择布尔值"
        disabled={disabled || readonly}
        onChange={(v) => onChange({ value: v })}
      />
    );
  }

  if (inputType === 'enum' || inputType === 'enum-multi') {
    const options = (fieldSchema.enum || []).map((v: any) => ({ value: v, label: String(v) }));
    return (
      <Select
        size="small"
        style={{ width: '100%' }}
        value={rule.value}
        mode={inputType === 'enum-multi' ? 'multiple' : undefined}
        options={options}
        placeholder="选择枚举值"
        disabled={disabled || readonly}
        onChange={(v) => onChange({ value: v })}
      />
    );
  }

  if (inputType === 'tags') {
    const itemSchema = getArrayItemSchema(fieldSchema);
    const options = isEnumSchema(itemSchema)
      ? itemSchema.enum.map((v: any) => ({ value: v, label: String(v) }))
      : [];
    return (
      <Select
        size="small"
        style={{ width: '100%' }}
        value={Array.isArray(rule.value) ? rule.value : rule.value ? [rule.value] : []}
        mode="tags"
        options={options}
        placeholder="输入多个值，回车确认"
        disabled={disabled || readonly}
        onChange={(v) => onChange({ value: v })}
      />
    );
  }

  if (inputType === 'object-array-contains') {
    const subFields = getObjectArraySubFields(fieldSchema);
    const subFieldSchema = subFields.includes(rule.subField || '')
      ? fieldSchema.items.properties[rule.subField || '']
      : undefined;

    return (
      <Space.Compact style={{ width: '100%' }}>
        <Select
          size="small"
          value={rule.subField}
          options={subFields.map((f) => ({ value: f, label: f }))}
          placeholder="子字段"
          disabled={disabled || readonly}
          onChange={(v) => onChange({ subField: v })}
          style={{ width: '38%' }}
        />
        <Select
          size="small"
          value={rule.matchMode || 'any'}
          options={MATCH_MODE_OPTIONS}
          disabled={disabled || readonly}
          onChange={(v) => onChange({ matchMode: v })}
          style={{ width: '34%' }}
        />
        <RuleSubValueInput
          value={rule.value}
          subFieldSchema={subFieldSchema}
          disabled={disabled}
          readonly={readonly}
          onChange={(v) => onChange({ value: v })}
        />
      </Space.Compact>
    );
  }

  // 默认文本输入
  return (
    <Input
      size="small"
      value={rule.value}
      placeholder="值"
      disabled={disabled || readonly}
      onChange={(e) => onChange({ value: e.target.value })}
    />
  );
};

interface RuleSubValueInputProps {
  value?: any;
  subFieldSchema?: Record<string, any>;
  disabled?: boolean;
  readonly?: boolean;
  onChange: (value: any) => void;
}

const RuleSubValueInput: React.FC<RuleSubValueInputProps> = ({
  value,
  subFieldSchema,
  disabled,
  readonly,
  onChange,
}) => {
  if (isBooleanSchema(subFieldSchema)) {
    return (
      <Select
        size="small"
        value={value}
        options={[
          { value: true, label: 'true' },
          { value: false, label: 'false' },
        ]}
        placeholder="值"
        disabled={disabled || readonly}
        onChange={(v) => onChange(v)}
        style={{ width: '28%' }}
      />
    );
  }
  if (isEnumSchema(subFieldSchema)) {
    return (
      <Select
        size="small"
        value={value}
        options={subFieldSchema.enum.map((v: any) => ({ value: v, label: String(v) }))}
        placeholder="值"
        disabled={disabled || readonly}
        onChange={(v) => onChange(v)}
        style={{ width: '28%' }}
      />
    );
  }
  if (isNumberSchema(subFieldSchema)) {
    return (
      <InputNumber
        size="small"
        value={value}
        placeholder="值"
        disabled={disabled || readonly}
        onChange={(v) => onChange(v)}
        style={{ width: '28%' }}
      />
    );
  }
  return (
    <Input
      size="small"
      value={value}
      placeholder="值"
      disabled={disabled || readonly}
      onChange={(e) => onChange(e.target.value)}
      style={{ width: '28%' }}
    />
  );
};

export default CompactRulesEditor;
