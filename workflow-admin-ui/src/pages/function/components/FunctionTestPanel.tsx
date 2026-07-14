import { Fragment, useState, useMemo, useEffect, useRef, useCallback } from 'react';
import { Button, Spin, Tag, Alert, Select, Space, Typography, Empty, Segmented, Tooltip } from 'antd';
const { Text } = Typography;
import { PlayCircleOutlined, ThunderboltOutlined, InfoCircleOutlined } from '@ant-design/icons';
import Form from '@rjsf/antd';
import validator from '@rjsf/validator-ajv8';
import JsonEditor from '@/components/JsonEditor';
import JsonCodeEditor from '../../workflow/components/widgets/JsonCodeEditor';
import DbSqlEditor from '../../workflow/components/widgets/DbSqlEditor';
import DbTableSelect from '../../workflow/components/widgets/DbTableSelect';
import DbColumnsSelect from '../../workflow/components/widgets/DbColumnsSelect';
import DbColumnsKeyValue from '../../workflow/components/widgets/DbColumnsKeyValue';
import BindingInput from '../../workflow/components/widgets/BindingInput';
import RedisRawEditor from '../../workflow/components/widgets/RedisRawEditor';
import SearchableSelectWidget from '../../workflow/components/widgets/SearchableSelectWidget';
import FieldAutoComplete from '../../workflow/components/widgets/FieldAutoComplete';
import OperatorSelectWidget from '../../workflow/components/widgets/OperatorSelectWidget';
import CompactRulesEditor from './widgets/CompactRulesEditor';
import JexlExpressionEditor from '../../workflow/components/widgets/JexlExpressionEditor';
import { testFunction } from '@/services/function';
import { getSchemas, getSchema } from '@/services/schema';
import {
  resolveDynamicWidgets,
  resolveRedisCommandUiSchema,
  REDIS_STRUCTURED_FIELDS,
} from '@/utils/schemaUiResolver';
import {
  isConditionFunction,
  applyOperatorLabels,
  generateSampleFromSchema,
  extractSchemaFieldPaths,
  extractSchemaFieldTypes,
  extractSchemaFieldMeta,
  VALUE_LESS_OPERATORS,
  type FieldType,
  type FieldSchemaMeta,
} from '@/utils/conditionSchema';
import { useClickDebounce } from '@/utils/useClickDebounce';
import { filterOutBuiltinSchemas } from '@/utils/schemaFilter';
import { NODE_PARAM_SCHEMAS } from '@/constants/nodeParamSchemas';
import type { FunctionDefinition } from '@/types/function';
import type { SchemaDefinition } from '@/types/schema';

interface FunctionTestPanelProps {
  functionId: string;
  functionDefinition?: FunctionDefinition | null;
  mode?: 'function' | 'node';
  onExecuteTest?: (payload: { inputs: any }) => Promise<any>;
}

/**
 * 将本地 NODE_PARAM_SCHEMAS 合并到 API 返回的 paramSchema。
 *
 * 当后端未重启（API 返回旧 schema 结构）时，本地 schema 作为兜底，
 * 确保 rules / operator enum / required 等关键字段不丢失。
 * API schema 已有的属性优先保留。
 */
function mergeLocalConditionSchema(apiSchema: Record<string, any>, functionId: string): Record<string, any> {
  const localKey = functionId === 'builtin:filter' ? 'FILTER' : 'CONDITION_BRANCH';
  const localSchema = NODE_PARAM_SCHEMAS[localKey];
  if (!localSchema?.properties) return apiSchema;

  const result = { ...apiSchema };

  // 确保 type 和 required 存在
  if (!result.type) result.type = localSchema.type;
  if (!result.required && localSchema.required) result.required = localSchema.required;

  // 合并 properties（以 API 为主，本地兜底缺失字段）
  const apiProps = { ...(result.properties || {}) };
  for (const [key, localProp] of Object.entries(localSchema.properties) as [string, any][]) {
    if (!apiProps[key]) {
      // API 缺失该字段 → 使用本地定义
      apiProps[key] = localProp;
    } else {
      // API 已有该字段 → 递归合并数组 items
      const apiProp = apiProps[key] as Record<string, any>;
      if (localProp.items?.properties && apiProp.items) {
        const apiItems = { ...apiProp.items };
        const localItems = localProp.items;
        if (!apiItems.properties) apiItems.properties = {};
        for (const [itemKey, localItemProp] of Object.entries(localItems.properties) as [string, any][]) {
          if (!apiItems.properties[itemKey]) {
            apiItems.properties = { ...apiItems.properties, [itemKey]: localItemProp };
          } else if (localItemProp.items?.properties && apiItems.properties[itemKey]?.items) {
            // 深层合并：conditions[].rules[]
            const deepApi = { ...apiItems.properties[itemKey] };
            const deepApiItems = { ...(deepApi.items || {}) };
            if (!deepApiItems.properties) deepApiItems.properties = {};
            for (const [deepKey, deepLocalProp] of Object.entries(localItemProp.items.properties) as [string, any][]) {
              if (!deepApiItems.properties[deepKey]) {
                deepApiItems.properties = { ...deepApiItems.properties, [deepKey]: deepLocalProp };
              }
            }
            deepApi.items = deepApiItems;
            apiItems.properties = { ...apiItems.properties, [itemKey]: deepApi };
          }
        }
        apiProps[key] = { ...apiProp, items: apiItems };
      }
    }
  }
  result.properties = apiProps;
  return result;
}

const BASE_UI_SCHEMA = {
  params: {
    'ui:widget': 'jsonCode',
    'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 },
  },
  data: {
    'ui:widget': 'jsonCode',
    'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 },
  },
  where: {
    'ui:widget': 'jsonCode',
    'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 },
  },
  config: {
    'ui:widget': 'jsonCode',
    'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 },
  },
  script: {
    'ui:widget': 'textarea',
    'ui:options': {
      rows: 4,
    },
  },
  raw: {
    'ui:widget': 'redisRawEditor',
    'ui:options': { autoHeight: true, minHeight: 60, maxHeight: 180 },
  },
};

const LOGIC_OPTIONS = [
  { value: 'and', label: '全部满足 (AND)' },
  { value: 'or', label: '任一满足 (OR)' },
];

/**
 * RJSF 自定义 ObjectFieldTemplate：识别当对象包含「logic + rulesGlobalLogicEnabled +
 * rulesGlobalLogicOperator」三个字段时，将它们合并渲染为顶部的「规则组合模式」
 * 统一入口（Segmented + 对应逻辑下拉），再过滤掉这三个原字段的 DOM，其余字段
 * （rules / negate / condition / target 等）由 RJSF 默认 ObjectField 生成的子元素
 * 直接渲染，保证不出现 custom Field 手动调用 ObjectField 导致 props 不全使 rules 消失的问题。
 *
 * 使用：在 uiSchema 中设置 ui:ObjectFieldTemplate='RulesCombineAwareObjectFieldTemplate'。
 */
const RulesCombineAwareObjectFieldTemplate: React.FC<any> = (props) => {
  const {
    schema,
    formData,
    onChange,
    registry,
    idSchema,
    disabled,
    readonly,
    formContext,
  } = props;
  const { TitleField, DescriptionField } = registry?.templates || {};
  const properties = (props as any).properties || [];
  const { Text } = Typography;

  const combineKeys = ['logic', 'rulesGlobalLogicEnabled', 'rulesGlobalLogicOperator', 'rules'];
  const propsObj = (schema as any)?.properties || {};
  const hasCombineGroup =
    'logic' in propsObj && 'rulesGlobalLogicEnabled' in propsObj && 'rulesGlobalLogicOperator' in propsObj;

  const mode = formData?.rulesGlobalLogicEnabled ? 'uniform' : 'perRule';
  const defaultLogic = formData?.logic || 'and';
  const globalOp = formData?.rulesGlobalLogicOperator || defaultLogic;
  // ⚠️ 注意：RJSF 的 ObjectFieldTemplate 的 props.onChange 不可靠（常为 undefined 或非函数），
  // 必须通过 formContext.onUpdateObjectByPath($id, patch) 反推出嵌套路径后 setInput 更新。
  // idSchema.$id 形如 root_condition、root_items_0_filter，由 parseRjsfIdToPath 解析。
  const ctxUpdater = (formContext as any)?.onUpdateObjectByPath as
    | ((id: string, leafPatch: Record<string, any>) => void)
    | undefined;
  const fieldId = idSchema?.$id as string | undefined;
  const updatePatch = (patch: any) => {
    if (ctxUpdater && typeof fieldId === 'string') {
      ctxUpdater(fieldId, patch);
      return;
    }
    // 兜底：极个别情况（如顶级表单的 formContext 还没初始化完成）尝试 props.onChange
    if (typeof onChange === 'function') {
      try {
        onChange({ ...(formData || {}), ...patch });
      } catch (_e) {
        // ignore
      }
    }
  };

  const bindingPaths = (formContext as any)?.bindingPaths || [];
  const declaredDepPaths = (formContext as any)?.declaredDepPaths || [];
  const upstreamFieldTypes = (formContext as any)?.upstreamFieldTypes || {};
  const upstreamFieldMeta = (formContext as any)?.upstreamFieldMeta || {};
  const candidateFields = useMemo(() => {
    const set = new Set<string>([...bindingPaths, ...declaredDepPaths]);
    return Array.from(set);
  }, [bindingPaths, declaredDepPaths]);

  const filteredProperties = properties.filter(
    (p: any) => p == null || typeof p !== 'object' || !combineKeys.includes(p.name),
  );

  const renderableProps =
    filteredProperties.length > 0 ? filteredProperties : properties;

  return (
    <fieldset className="rjsf-fieldset" style={{ border: 'none', padding: 0, margin: 0 }}>
      {TitleField && schema?.title && (
        <TitleField
          id={`${idSchema?.$id || 'field'}-title`}
          title={schema.title}
          required={(schema as any)?.required?.length > 0}
        />
      )}
      {DescriptionField && schema?.description && (
        <DescriptionField
          id={`${idSchema?.$id || 'field'}-description`}
          description={schema.description}
        />
      )}

      {hasCombineGroup && (
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 6,
            padding: '6px 8px',
            border: '1px solid #e6f0ff',
            borderRadius: 6,
            background: '#fafcff',
            marginBottom: 10,
          }}
        >
          <Space size={6} wrap>
            <Text strong style={{ fontSize: 12 }}>规则组合模式</Text>
            <Tooltip title="统一强制：所有规则间统一使用 AND/OR（简单直观）；独立连接：每条规则之前可单独编辑连接符（灵活）。">
              <InfoCircleOutlined style={{ color: '#8c8c8c', fontSize: 11 }} />
            </Tooltip>
          </Space>
          <Space size={8} align="center">
            <Segmented
              size="small"
              disabled={disabled || readonly}
              value={mode}
              onChange={(v) => {
                const next = v as 'perRule' | 'uniform';
                updatePatch(
                  next === 'uniform'
                    ? { rulesGlobalLogicEnabled: true, rulesGlobalLogicOperator: globalOp || defaultLogic || 'and' }
                    : { rulesGlobalLogicEnabled: false, logic: defaultLogic || globalOp || 'and' }
                );
              }}
              options={[
                { label: '独立连接', value: 'perRule' },
                { label: '统一强制', value: 'uniform' },
              ]}
            />
            {mode === 'uniform' ? (
              <Select
                size="small"
                style={{ width: 104 }}
                disabled={disabled || readonly}
                value={globalOp}
                options={LOGIC_OPTIONS}
                onChange={(v) => updatePatch({ rulesGlobalLogicOperator: v })}
              />
            ) : (
              <Tooltip title="独立连接模式下，新增规则未单独声明逻辑时使用此默认值。">
                <Space size={4}>
                  <Text style={{ fontSize: 11, color: '#8c8c8c' }}>默认</Text>
                  <Select
                    size="small"
                    style={{ width: 104 }}
                    disabled={disabled || readonly}
                    value={defaultLogic}
                    options={LOGIC_OPTIONS}
                    onChange={(v) => updatePatch({ logic: v })}
                  />
                </Space>
              </Tooltip>
            )}
          </Space>
        </div>
      )}

      {hasCombineGroup && (
        <CompactRulesEditor
          value={formData?.rules || []}
          onChange={(nextRules: any) => updatePatch({ rules: nextRules })}
          fields={candidateFields}
          fieldTypes={upstreamFieldTypes}
          fieldMeta={upstreamFieldMeta}
          disabled={disabled}
          readonly={readonly}
          globalLogicEnabled={!!formData?.rulesGlobalLogicEnabled}
          globalLogicOperator={formData?.rulesGlobalLogicOperator}
          defaultLogic={formData?.logic || 'and'}
        />
      )}

      {renderableProps.map((p: any, idx: number) => (
        <Fragment key={idx}>{p?.content ?? p}</Fragment>
      ))}
    </fieldset>
  );
};

/**
 * RJSF 自定义 ArrayFieldTemplate：给规则列表（rules）加 maxHeight 独立滚动条
 * + sticky 底部"添加规则"按钮，避免规则多时撑爆页面。
 * 使用：在 uiSchema 中设置 ui:ArrayFieldTemplate='ScrollableRulesArrayFieldTemplate'。
 */
const ScrollableRulesArrayFieldTemplate: React.FC<any> = (props) => {
  const {
    items,
    canAdd,
    onAddClick,
    schema,
    registry,
    idSchema,
    title,
    disabled,
    readonly,
  } = props;
  const { TitleField, DescriptionField } = registry?.templates || {};
  const itemsArray = (items as any[]) || [];
  return (
    <fieldset className="rjsf-fieldset" style={{ border: 'none', padding: 0, margin: 0 }}>
      <div
        style={{
          border: '1.5px solid #722ed1',
          borderRadius: 4,
          padding: '3px 8px',
          marginBottom: 6,
          background: '#f9f0ff',
        }}
      >
        <Typography.Text
          type="secondary"
          style={{ fontSize: 10, color: '#722ed1', fontWeight: 600 }}
        >
          🟪 模板: ScrollableRulesArrayFieldTemplate | items.length=
          <Text code>{itemsArray.length}</Text>
          {' | canAdd='}
          <Text code>{String(canAdd)}</Text>
          {' | disabled='}
          <Text code>{String(disabled)}</Text>
          {' | readonly='}
          <Text code>{String(readonly)}</Text>
          {' | schema.title='}
          <Text code>{String(title ?? '空')}</Text>
        </Typography.Text>
      </div>
      {TitleField && title != null && (
        <TitleField
          id={`${idSchema?.$id || 'arr'}-title`}
          title={title}
          required={(schema as any)?.minItems ? (schema as any).minItems > 0 : false}
        />
      )}
      {DescriptionField && (schema as any)?.description && (
        <DescriptionField
          id={`${idSchema?.$id || 'arr'}-description`}
          description={(schema as any).description}
        />
      )}
      <div
        style={{
          marginTop: 6,
          maxHeight: 340,
          overflowY: 'auto',
          overflowX: 'hidden',
          padding: '4px 2px 0 0',
          border: '1px solid #f0f0f0',
          borderRadius: 6,
          background: '#fcfcfd',
          scrollbarWidth: 'thin',
        }}
      >
        {itemsArray.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={<Text type="secondary" style={{ fontSize: 12 }}>暂无规则，点击下方「添加规则」开始</Text>}
            style={{ margin: '12px 0', padding: '12px 0' }}
          />
        ) : (
          itemsArray.map((item, idx) => (
            <Fragment key={item?.key ?? `rule-${idx}`}>
              {item?.children}
            </Fragment>
          ))
        )}
        {canAdd && !disabled && !readonly && (
          <div
            style={{
              position: 'sticky',
              bottom: 0,
              background: '#fff',
              padding: '4px 0 2px',
              marginTop: 4,
              borderTop: '1px solid #f5f5f5',
              zIndex: 1,
            }}
          >
            <Button
              type="dashed"
              size="small"
              block
              icon={<PlusOutlined />}
              onClick={onAddClick}
            >
              添加规则
            </Button>
          </div>
        )}
      </div>
    </fieldset>
  );
};

/**
 * RJSF 自定义 ObjectFieldTemplate（仅用于 rules.item，即「单条规则」卡片）。
 *
 * UIUX 专业建议：
 * - ❌ 不做「rules 列表横向滚动」——条件是有逻辑阅读顺序的（AND/OR 链条），纵向阅读 + 单条横向紧凑
 *   才符合业界（Zapier / Segment / Airtable Filter）的交互惯例。
 * - ✅ 改为「单条规则内部横向列布局」：
 *     logic连接符 | field字段 | operator操作符 | value目标值 | negate取反
 *   每条规则仅占 1 行高度（窄抽屉自动 wrap 成 2 行），10 条规则的总高度 ≈ 10 行默认卡片高度
 *   的 1/4~1/5，极大缩短页面长度。
 * - ✅ logic 连接符仅第 2 条起显示 AND/OR 下拉，第 1 条显示灰色序号占位，形成「条件链」视觉。
 * - ✅ 操作符/字段/值列固定 minWidth，保持对齐；negate 取反单独窄列。
 */
const RuleCardHorizontalObjectTemplate: React.FC<any> = (props) => {
  const {
    schema,
    registry,
    idSchema,
    uiSchema,
  } = props;
  const properties = (props as any).properties || [];
  const { TitleField, DescriptionField } = registry?.templates || {};
  const { Text } = Typography;

  // 从 idSchema.$id 里正则取当前规则在 rules 数组里的下标（如 root_rules_3 → 3）
  const idStr: string = idSchema?.$id || '';
  const idxMatch = idStr.match(/_(\d+)(?:_|$)/);
  const index = idxMatch ? Number(idxMatch[1]) : 0;
  const isFirst = index === 0;

  // 按 name 精确取出 5 个字段
  const findProp = (name: string) =>
    properties.find((p: any) => p && typeof p === 'object' && p.name === name);
  const logicProp = findProp('logic');
  const fieldProp = findProp('field');
  const opProp = findProp('operator');
  const valueProp = findProp('value');
  const negateProp = findProp('negate');

  // 把 properties 里的「其他字段」排后面兜底（兼容未来扩展字段）
  const coreNames = ['logic', 'field', 'operator', 'value', 'negate'];
  const extraProps = properties.filter(
    (p: any) => !(p && typeof p === 'object' && coreNames.includes(p.name)),
  );

  return (
    <div
      style={{
        border: '1px solid #eef1f6',
        borderRadius: 8,
        background: '#fff',
        padding: '6px 8px 8px',
        marginBottom: 8,
      }}
    >
      {TitleField && schema?.title && (
        <div style={{ marginBottom: 4 }}>
          <TitleField
            id={`${idStr}-title`}
            title={schema.title}
            required={(schema as any)?.required?.length > 0}
          />
        </div>
      )}
      {DescriptionField && schema?.description && (
        <div style={{ marginBottom: 6 }}>
          <DescriptionField
            id={`${idStr}-description`}
            description={schema.description}
          />
        </div>
      )}

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          gap: 8,
        }}
      >
        {/* L1: 逻辑连接符列（仅第 2..N 条显示 AND/OR 下拉，第 1 条显示灰色序号占位） */}
        <div style={{ minWidth: 108, flex: '0 0 auto' }}>
          {isFirst ? (
            <Tooltip title={`规则 ${index + 1}，第 1 条规则逻辑从组合模式继承`}>
              <Tag color="blue" style={{ margin: 0 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  ① 规则 {index + 1}
                </Text>
              </Tag>
            </Tooltip>
          ) : (
            logicProp?.content
          )}
        </div>

        {/* L2: 字段名列 */}
        <div
          style={{
            minWidth: 220,
            flex: '2 1 220px',
            maxWidth: '40%',
          }}
        >
          {fieldProp?.content}
        </div>

        {/* L3: 操作符列 */}
        <div
          style={{
            minWidth: 148,
            flex: '1 1 148px',
            maxWidth: '26%',
          }}
        >
          {opProp?.content}
        </div>

        {/* L4: 目标值列 */}
        <div
          style={{
            minWidth: 200,
            flex: '2 1 200px',
            maxWidth: '35%',
          }}
        >
          {valueProp?.content}
        </div>

        {/* L5: 取反列（窄） */}
        <div style={{ minWidth: 72, flex: '0 0 auto' }}>
          {negateProp?.content}
        </div>
      </div>

      {/* 兜底：未识别的扩展字段（如未来新增字段）按原顺序显示 */}
      {extraProps.length > 0 && (
        <div style={{ marginTop: 6 }}>
          {extraProps.map((p: any, idx: number) => (
            <Fragment key={idx}>{p?.content ?? p}</Fragment>
          ))}
        </div>
      )}

      {/* uiSchema 里可能注入的其他 children（如 array-level 的 remove 按钮），兜底显示 */}
      {(props as any).children}
    </div>
  );
};

// 三个 RJSF 自定义模板全部注册，分别对应三个层级：
//   1. 外层条件配置对象 → 合并 3 个逻辑控件为顶部 Segmented 合并段
//   2. rules 数组级 → 340px 滚动容器 + sticky 底部添加按钮
//   3. rules.item 单条规则 → 1 行 5 列横向布局（logic | field | op | value | negate）
const templates: Record<string, any> = {
  RulesCombineAwareObjectFieldTemplate,
  ScrollableRulesArrayFieldTemplate,
  RuleCardHorizontalObjectTemplate,
};

const widgets = {
  jsonCode: JsonCodeEditor,
  dbSqlEditor: DbSqlEditor,
  dbTableSelect: DbTableSelect,
  dbColumnsSelect: DbColumnsSelect,
  dbColumnsKeyValue: DbColumnsKeyValue,
  binding: BindingInput,
  redisRawEditor: RedisRawEditor,
  searchableSelect: SearchableSelectWidget,
  fieldAutoComplete: FieldAutoComplete,
  operatorSelect: OperatorSelectWidget,
  expressionEditor: JexlExpressionEditor,
  jexlExpression: JexlExpressionEditor,
};

const parseSchema = (raw: unknown): object | null => {
  if (!raw) return null;
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }
  if (typeof raw === 'object') return raw as object;
  return null;
};

/**
 * RJSF 防御性补丁：为 schema 中缺少 type 的 property 自动补充 type: "string"。
 * 兼容数据库中旧版 paramSchema（如条件函数的 value 字段未声明 type）。
 */
const sanitizeSchemaForRJSF = (schema: any): any => {
  if (!schema || typeof schema !== 'object') return schema;
  const result = { ...schema };

  if (result.properties && typeof result.properties === 'object') {
    const patched: Record<string, any> = {};
    let changed = false;
    for (const [key, prop] of Object.entries(result.properties)) {
      const p = prop as Record<string, any>;
      if (p && typeof p === 'object' && !p.type && !p.$ref && !p.oneOf && !p.anyOf && !p.allOf) {
        patched[key] = { ...p, type: 'string' };
        changed = true;
      } else {
        patched[key] = p;
      }
    }
    if (changed) result.properties = patched;
  }

  // 递归处理 items（数组元素）
  if (result.items && typeof result.items === 'object') {
    result.items = sanitizeSchemaForRJSF(result.items);
  }

  // 递归处理嵌套 properties 中的 items
  if (result.properties && typeof result.properties === 'object') {
    const deepPatched: Record<string, any> = {};
    let deepChanged = false;
    for (const [key, prop] of Object.entries(result.properties)) {
      const p = prop as Record<string, any>;
      if (p?.items && typeof p.items === 'object') {
        const sanitized = sanitizeSchemaForRJSF(p.items);
        if (sanitized !== p.items) {
          deepPatched[key] = { ...p, items: sanitized };
          deepChanged = true;
        } else {
          deepPatched[key] = p;
        }
      } else {
        deepPatched[key] = p;
      }
    }
    if (deepChanged) result.properties = { ...result.properties, ...deepPatched };
  }

  return result;
};

/**
 * 校验条件规则数据完整性。
 *
 * 检查每条规则的 field 和 operator 必填，value 在非 isNull 操作符时必填。
 * 返回第一个错误信息，通过则返回 null。
 */
const validateConditionRules = (data: any): string | null => {
  const checkRules = (rules: any[], context: string): string | null => {
    if (!Array.isArray(rules)) return null;
    for (let i = 0; i < rules.length; i++) {
      const r = rules[i];
      if (!r || typeof r !== 'object') continue;
      if (!r.field) return `${context} 第 ${i + 1} 条规则：字段名不能为空`;
      if (!r.operator) return `${context} 第 ${i + 1} 条规则：操作符不能为空`;
      if (!VALUE_LESS_OPERATORS.has(r.operator) && (r.value == null || r.value === '')) {
        return `${context} 第 ${i + 1} 条规则：操作符 ${r.operator} 需要填写值`;
      }
    }
    return null;
  };

  // FILTER 顶层 rules
  if (data.rules) {
    const err = checkRules(data.rules, '过滤规则');
    if (err) return err;
  }
  // CONDITION_BRANCH conditions[].rules
  if (Array.isArray(data.conditions)) {
    for (let ci = 0; ci < data.conditions.length; ci++) {
      const cond = data.conditions[ci];
      if (cond?.rules) {
        const err = checkRules(cond.rules, `条件 #${ci + 1}`);
        if (err) return err;
      }
    }
  }
  return null;
};

const hasSqlField = (schema: object | null): boolean => {
  if (!schema) return false;
  const props = (schema as any)?.properties;
  return props && typeof props.sql === 'object';
};

/**
 * 递归修复条件规则中被 RJSF 剥离的空字符串 value 字段。
 *
 * RJSF 在序列化 formData 时会将空字符串 "" 转为 undefined 并从对象中移除，
 * 导致后端 rule["value"] 为 null，进而使 "" eq "" 返回 false。
 * 此函数在提交前将规则对象中缺失的 value 恢复为 ""。
 */
const normalizeRuleValues = (obj: any): any => {
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return obj;

  const result = { ...obj };

  // 规则对象（含 field + operator）：确保 value 存在
  if ('field' in result && 'operator' in result) {
    if (result.value == null) result.value = '';
  }

  // 递归处理数组（如 rules[], conditions[]）
  for (const key of Object.keys(result)) {
    const val = result[key];
    if (Array.isArray(val)) {
      result[key] = val.map((item) => normalizeRuleValues(item));
    } else if (val && typeof val === 'object' && !Array.isArray(val)) {
      result[key] = normalizeRuleValues(val);
    }
  }

  return result;
};

const isRedisCommandSchema = (schema: object | null): boolean => {
  if (!schema) return false;
  const props = (schema as any)?.properties;
  return props && typeof props.command === 'object' && typeof props.raw === 'object';
};

const SQL_HISTORY_KEY = 'function_sql_history';

const getSqlHistory = (functionId: string): string[] => {
  try {
    const all = JSON.parse(localStorage.getItem(SQL_HISTORY_KEY) || '{}');
    return all[functionId] || [];
  } catch {
    return [];
  }
};

const saveSqlHistory = (functionId: string, sql: string) => {
  const trimmed = sql.trim();
  if (!trimmed) return;
  try {
    const all = JSON.parse(localStorage.getItem(SQL_HISTORY_KEY) || '{}');
    const list = all[functionId] || [];
    const newList = [trimmed, ...list.filter((s: string) => s !== trimmed)].slice(0, 10);
    all[functionId] = newList;
    localStorage.setItem(SQL_HISTORY_KEY, JSON.stringify(all));
  } catch {
    // ignore
  }
};

const FunctionTestPanel: React.FC<FunctionTestPanelProps> = ({ functionId, functionDefinition, mode = 'function', onExecuteTest }) => {
  const conditionMode = useMemo(() => isConditionFunction(functionId), [functionId]);

  const paramSchema = useMemo(() => {
    const raw = functionDefinition?.config?.paramSchema;
    const parsed = parseSchema(raw);
    const sanitizedBase = parsed ? sanitizeSchemaForRJSF(parsed) : {};
    const sanitized =
      sanitizedBase && typeof sanitizedBase === 'object' ? sanitizedBase : {};
    if (!conditionMode) return Object.keys(sanitized).length > 0 ? sanitized : null;

    const merged = mergeLocalConditionSchema(sanitized as Record<string, any>, functionId);
    const mergedProps = (merged?.properties as Record<string, any>) || {};

    const keyForLocal = functionId === 'builtin:filter' ? 'FILTER' : 'CONDITION_BRANCH';
    const localFull = NODE_PARAM_SCHEMAS[keyForLocal];
    const hasRules = !!mergedProps.rules;
    const hasConditions = !!mergedProps.conditions;
    if (localFull?.properties && (keyForLocal === 'FILTER' ? !hasRules : !hasConditions)) {
      const fallback = mergeLocalConditionSchema(
        JSON.parse(JSON.stringify(localFull)),
        functionId,
      );
      return applyOperatorLabels(fallback);
    }

    return applyOperatorLabels(merged);
  }, [functionDefinition, conditionMode, functionId]);

  const domain = useMemo(() => {
    return functionDefinition?.config?.domain as string | undefined;
  }, [functionDefinition]);

  const dataSource = useMemo(() => {
    return functionDefinition?.config?.dataSource as string | undefined;
  }, [functionDefinition]);

  const isDbDomain = domain === 'db' || hasSqlField(paramSchema);
  const redisCommandMode = isRedisCommandSchema(paramSchema);

  // 🔍 调试信息：用于快速定位「builtin:filter rules/logic 又没了」到底卡在哪一层
  const debugSchemaInfo = useMemo(() => {
    const raw = functionDefinition?.config?.paramSchema;
    const parsed = parseSchema(raw);
    const sanitizedBase = parsed ? sanitizeSchemaForRJSF(parsed) : {};
    const sanitized =
      sanitizedBase && typeof sanitizedBase === 'object' ? sanitizedBase : {};
    const merged = conditionMode
      ? mergeLocalConditionSchema(sanitized as Record<string, any>, functionId)
      : null;
    const mergedProps = (merged?.properties as Record<string, any>) || {};
    const keyForLocal = functionId === 'builtin:filter' ? 'FILTER' : 'CONDITION_BRANCH';
    const localFull = NODE_PARAM_SCHEMAS[keyForLocal];
    const shouldFallback =
      conditionMode &&
      !!localFull?.properties &&
      (keyForLocal === 'FILTER' ? !mergedProps.rules : !mergedProps.conditions);
    const finalSchema = paramSchema as Record<string, any> | null;
    const finalProps = Object.keys((finalSchema?.properties as Record<string, any>) || {});
    return {
      functionId,
      conditionMode,
      rawIsNull: raw == null,
      rawIsString: typeof raw === 'string',
      rawLen: typeof raw === 'string' ? raw.length : -1,
      parsedIsNull: parsed == null,
      sanitizedKeys: Object.keys(sanitized),
      mergedKeys: Object.keys(mergedProps),
      shouldFallback,
      finalType: finalSchema?.type ?? null,
      finalPropsKeys: finalProps,
      finalPropsCount: finalProps.length,
      isDbDomain,
      redisCommandMode,
    };
  }, [functionDefinition, conditionMode, functionId, paramSchema, isDbDomain, redisCommandMode]);

  const [input, setInput] = useState<any>({});
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sqlHistory, setSqlHistory] = useState<string[]>(() => getSqlHistory(functionId));
  const [redisMode, setRedisMode] = useState<'structured' | 'raw'>('structured');
  const redisDrafts = useRef<{ structured: any; raw: any }>({ structured: {}, raw: {} });

  // 条件函数测试：schema 选择与示例数据
  const [schemas, setSchemas] = useState<SchemaDefinition[]>([]);
  const [selectedSchemaName, setSelectedSchemaName] = useState<string | undefined>();
  const [testData, setTestData] = useState<any>({});
  const [schemaFields, setSchemaFields] = useState<string[]>([]);
  const [schemaFieldTypes, setSchemaFieldTypes] = useState<Record<string, FieldType>>({});
  const [schemaFieldMeta, setSchemaFieldMeta] = useState<Record<string, FieldSchemaMeta>>({});

  const [ctxWorkflowSchema, setCtxWorkflowSchema] = useState<string | undefined>();

  useEffect(() => {
    const history = getSqlHistory(functionId);
    setSqlHistory(history);
    if (history.length > 0 && !input.sql) {
      setInput((prev: any) => ({ ...prev, sql: history[0] }));
    }
  }, [functionId]);

  // 加载全量 schema 列表（条件函数的 rules + 三上下文的 Schema Select 都要用到）
  // —— 函数测试输入 Schema 过滤规则：builtin:* 的系统 Schema 不展示（由系统自动注入，
  //    只允许用户选择自定义 / 平台通用 Schema，避免测试输入脏数据
  useEffect(() => {
    getSchemas({ pageSize: 1000 })
      .then((res) => {
        const rawList = res?.list || [];
        // 过滤 builtin:*，且保留 scope=PLATFORM 的用户自定义冻结 Schema
        const list: SchemaDefinition[] = filterOutBuiltinSchemas(rawList);
        // DEBUG: 打印返回的 schemas 结构，定位 UI 中显示 undefined 的原因
        // 在控制台查看：长度、前 5 项属性快照
        try {
          // eslint-disable-next-line no-console
          console.debug('getSchemas.result.count=', list.length, 'sample=', list.slice(0, 5), 'builtin filtered=', rawList.length - list.length);
        } catch (_e) {}
        setSchemas(list);
        // 条件模式才维护条件 schema 的测试数据 / fields 映射
        if (!conditionMode) {
          setSelectedSchemaName(undefined);
          setTestData({});
          setSchemaFields([]);
          setSchemaFieldTypes({});
          setSchemaFieldMeta({});
        } else {
          // 条件模式下如果当前选中的 schema 是 builtin（比如从路由历史中带过来），清掉
          if (selectedSchemaName && list.every((s) => s.schemaName !== selectedSchemaName && s.name !== selectedSchemaName)) {
            setSelectedSchemaName(undefined);
            setTestData({});
            setSchemaFields([]);
            setSchemaFieldTypes({});
            setSchemaFieldMeta({});
          }
        }
        const inputSchema = (functionDefinition?.config as any)?.inputSchema as string | undefined;
        if (inputSchema && !ctxWorkflowSchema) {
          const exists = list.some((s) => s.name === inputSchema || s.schemaName === inputSchema);
          if (exists) setCtxWorkflowSchema(inputSchema);
        }
      })
      .catch(() => setSchemas([]));
    // 仅在组件挂载 / 切换函数时触发
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conditionMode, functionId, functionDefinition]);

  // 非条件函数：参数 schema 存在且输入为空时，自动生成示例入参
  useEffect(() => {
    if (conditionMode || !paramSchema) return;
    const hasInput = input && Object.keys(input).length > 0;
    if (hasInput) return;
    const sample = generateSampleFromSchema(paramSchema as Record<string, any>);
    if (sample && typeof sample === 'object' && Object.keys(sample).length > 0) {
      setInput(sample);
    }
    // 仅在 paramSchema 或 functionId 变化时触发，避免覆盖用户手动修改
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paramSchema, functionId, conditionMode]);

  const handleSchemaChange = async (schemaName: string | undefined) => {
    setSelectedSchemaName(schemaName);
    if (!schemaName) {
      setTestData({});
      setSchemaFields([]);
      setSchemaFieldTypes({});
      setSchemaFieldMeta({});
      return;
    }
    try {
      const schemaDef = await getSchema(schemaName);
      const schema =
        schemaDef?.schema || (schemaDef?.schemaJson ? JSON.parse(schemaDef.schemaJson) : null);
      const schemaObj = schema || {};
      setTestData(generateSampleFromSchema(schemaObj));
      setSchemaFields(extractSchemaFieldPaths(schemaObj));
      setSchemaFieldTypes(extractSchemaFieldTypes(schemaObj));
      setSchemaFieldMeta(extractSchemaFieldMeta(schemaObj));
    } catch (e) {
      console.error('加载 schema 失败:', e);
      setTestData({});
      setSchemaFields([]);
      setSchemaFieldTypes({});
      setSchemaFieldMeta({});
    }
  };

  const dynamicWidgets = useMemo(() => resolveDynamicWidgets(paramSchema || {}), [paramSchema]);

  const applyExpressionWidgets = (schema: any, uiObj: Record<string, any>) => {
    if (!schema || typeof schema !== 'object') return;
    const properties = schema.properties || {};
    for (const [key, prop] of Object.entries(properties) as [string, any][]) {
      if (['condition', 'expression', 'expr'].includes(key)) {
        uiObj[key] = { ...(uiObj[key] || {}), 'ui:widget': 'expressionEditor', 'ui:options': { minLines: 3, maxLines: 8 } };
      }
      if (prop?.type === 'object' && prop.properties) {
        applyExpressionWidgets(prop, uiObj[key] || (uiObj[key] = {}));
      }
      if (prop?.type === 'array' && prop.items) {
        applyExpressionWidgets(prop.items, uiObj[key]?.items || (uiObj[key] = { ...uiObj[key], items: {} }).items);
      }
    }
  };

  const redisHiddenFields = useMemo(() => {
    if (!redisCommandMode) return {};
    return resolveRedisCommandUiSchema(paramSchema as Record<string, any>, input, redisMode);
  }, [redisCommandMode, paramSchema, input, redisMode]);

  const uiSchema = useMemo(() => {
    const base: Record<string, any> = { ...BASE_UI_SCHEMA, ...dynamicWidgets, ...redisHiddenFields };
    applyExpressionWidgets(paramSchema || {}, base);
    if (redisCommandMode) {
      // raw 与 command 互斥：仅在对应模式下指定 widget，避免覆盖 redisHiddenFields 的隐藏配置
      if (redisMode === 'raw') {
        base.raw = { 'ui:widget': 'redisRawEditor', 'ui:options': { autoHeight: true, minHeight: 60, maxHeight: 180 } };
      } else {
        base.command = { 'ui:widget': 'searchableSelect' };
        if (base.commands?.items) {
          base.commands.items = { ...(base.commands.items || {}), command: { 'ui:widget': 'searchableSelect' } };
        }
      }
    }

    const combineKeys = ['logic', 'rulesGlobalLogicEnabled', 'rulesGlobalLogicOperator'];
    const ruleItemUiSchema = {
      operator: { 'ui:widget': 'operatorSelect' },
      field: { 'ui:widget': 'fieldAutoComplete' },
    };

    let foundConditionObjects = 0;
    const patchConditionObjects = (objSchema: any, uiObj: Record<string, any>) => {
      if (!objSchema || typeof objSchema !== 'object') return;
      const properties = objSchema.properties || {};
      const keys = Object.keys(properties);
      const hasCombineGroup =
        combineKeys.every((k) => keys.includes(k)) && keys.includes('rules');

      if (hasCombineGroup) {
        foundConditionObjects += 1;
        uiObj['ui:ObjectFieldTemplate'] = RulesCombineAwareObjectFieldTemplate;
      }

      for (const key of Object.keys(properties)) {
        const propSchema = properties[key];
        if (!uiObj[key]) uiObj[key] = {};
        if ((propSchema as any)?.type === 'array' && (propSchema as any)?.items) {
          if (!uiObj[key].items) uiObj[key].items = {};
          patchConditionObjects((propSchema as any).items, uiObj[key].items);
        }
        patchConditionObjects(propSchema, uiObj[key]);
      }
    };
    patchConditionObjects(paramSchema || {}, base);

    // 澄清 RJSF 表单默认 Submit 按钮的用途（用户反馈此按钮位于 AviatorScript 表达式区下方，用途不明）
    // 整个测试输入表单的 onChange 已经实时 setInput，该按钮仅用于：1) 触发 ajv 校验  2) 视觉上确认表达式/配置输入完成
    base['ui:submitButtonOptions'] = {
      norender: false,
      submitText: '✔ 应用测试输入 & 校验（所有字段修改已实时生效，点击触发表单校验）',
      props: {
        type: 'primary' as const,
        size: 'middle' as const,
        style: { fontWeight: 600, marginTop: 12 },
      },
    };

    // 条件对象存在时才启用 formContext 内的 operatorSelect/fieldAutoComplete 数据源
    const hasConditionObjects = foundConditionObjects > 0;
    (base as any).__hasConditionObjects = hasConditionObjects;

    if (!isDbDomain) return base;
    return {
      ...base,
      sql: { 'ui:widget': 'dbSqlEditor', 'ui:options': { functionId, compact: true, autoHeight: true, minHeight: 80, maxHeight: 240 } },
    };
  }, [dynamicWidgets, redisHiddenFields, redisCommandMode, redisMode, isDbDomain, functionId, paramSchema]);

  // uiSchema 中是否包含条件配置对象 → 决定 formContext 给 operatorSelect/fieldAutoComplete 的数据
  const hasConditionObjects = !!(uiSchema as any).__hasConditionObjects || conditionMode;

  // ── RJSF ObjectFieldTemplate 顶层对象更新辅助（解决 ObjectFieldTemplate 自身 onChange 为 undefined 问题）───────
  // RJSF 的 ObjectFieldTemplate 通常只负责渲染子字段，不保证 props.onChange 是“整体替换对象”的函数。
  // 因此我们通过 formContext 显式提供一个 onUpdateObjectByPath($id, patch)，模板里用 $id 反推出 formData 中的嵌套路径，
  // 构造 setInput 的深拷贝更新。CompactRulesEditor 的 addRule/addGroup → updatePatch → 此回调可正确触发表单回写。
  const parseRjsfIdToPath = useCallback((id: string): string[] => {
    if (!id) return [];
    if (id === 'root') return [];
    const raw = id.startsWith('root_') ? id.slice('root_'.length) : id;
    if (!raw) return [];
    const parts = raw.includes('.') ? raw.split('.') : raw.split('_');
    return parts.filter((p) => p.length > 0);
  }, []);

  const updateByPath = useCallback(
    (root: any, path: string[], leafPatch: Record<string, any>): any => {
      if (!path || path.length === 0) return { ...(root || {}), ...leafPatch };
      const [head, ...rest] = path;
      const isIndex = /^\d+$/.test(head);
      const cur: any = isIndex ? [...((Array.isArray(root) ? root : []) as any[])] : { ...(root || {}) };
      if (rest.length === 0) {
        cur[head as any] = { ...(cur[head as any] || {}), ...leafPatch };
      } else {
        cur[head as any] = updateByPath(cur[head as any], rest, leafPatch);
      }
      return cur;
    },
    [],
  );

  const onUpdateObjectByPath = useCallback(
    (id: string, patch: Record<string, any>) => {
      setInput((prev: any) => {
        const path = parseRjsfIdToPath(id);
        return updateByPath(prev, path, patch) || {};
      });
    },
    [parseRjsfIdToPath, updateByPath],
  );

  const formContext = useMemo(
    () => ({
      params: input,
      dataSource: input.dataSource || dataSource,
      table: input.table,
      inputSchemaFields: [],
      upstreamFields: hasConditionObjects ? schemaFields : [],
      upstreamFieldTypes: hasConditionObjects ? schemaFieldTypes : {},
      upstreamFieldMeta: hasConditionObjects ? schemaFieldMeta : {},
      bindingPaths: hasConditionObjects ? schemaFields : [],
      declaredDepPaths: [],
      onUpdateObjectByPath,
    }),
    [input, dataSource, hasConditionObjects, schemaFields, schemaFieldTypes, schemaFieldMeta, onUpdateObjectByPath],
  );

  const handleRedisModeChange = (mode: 'structured' | 'raw') => {
    setInput((prev: any) => {
      // 保存当前模式草稿
      if (redisMode === 'structured') {
        const draft: any = {};
        for (const f of REDIS_STRUCTURED_FIELDS) {
          if (prev[f] !== undefined) draft[f] = prev[f];
        }
        redisDrafts.current.structured = draft;
      } else {
        redisDrafts.current.raw = prev.raw;
      }

      const next = { ...prev };
      if (mode === 'structured') {
        delete next.raw;
        Object.assign(next, redisDrafts.current.structured);
      } else {
        for (const f of REDIS_STRUCTURED_FIELDS) {
          delete next[f];
        }
        if (redisDrafts.current.raw !== undefined) {
          next.raw = redisDrafts.current.raw;
        }
      }
      return next;
    });
    setRedisMode(mode);
  };

  const handleGenerateSample = useClickDebounce(() => {
    if (!paramSchema || conditionMode) return;
    const sample = generateSampleFromSchema(paramSchema as Record<string, any>);
    setInput(sample && typeof sample === 'object' ? sample : {});
  });

  const handleTest = useClickDebounce(async () => {
    // 测试入参统一为函数的 nodeParams，即 paramSchema 对应的实例数据
    let testInput = input;
    // 条件函数：将 schema 生成的测试数据合并到输入中，作为规则求值所需输入
    if (conditionMode) {
      testInput = { ...testData, ...testInput };
      // RJSF 会将空字符串值序列化为 undefined 并剥离，导致后端 rule.value 为 null。
      // 此处将结构化规则中缺失的 value 恢复为空字符串，保证 "" eq "" → true。
      testInput = normalizeRuleValues(testInput);

      // 校验规则完整性（field / operator 必填，value 按需必填）
      const validationError = validateConditionRules(testInput);
      if (validationError) {
        setError(validationError);
        setResult(null);
        return;
      }
    }
    if (isDbDomain && testInput.sql) {
      saveSqlHistory(functionId, testInput.sql);
      setSqlHistory(getSqlHistory(functionId));
    }
    setLoading(true);
    setError(null);
    try {
      const res = onExecuteTest
        ? await onExecuteTest({ inputs: testInput })
        : await testFunction(functionId, { inputs: testInput });
      setResult(res);
    } catch (e: any) {
      console.error('函数测试失败:', e);
      setError(e?.message || '请求失败');
    } finally {
      setLoading(false);
    }
  });

  const renderInputForm = () => {
    if (paramSchema) {
      return (
        <>
          {isDbDomain && sqlHistory.length > 0 && (
            <Select
              value={input.sql || undefined}
              onChange={(sql) => setInput((prev: any) => ({ ...prev, sql }))}
              options={sqlHistory.map((sql, index) => ({
                value: sql,
                label: `最近 #${index + 1} ${sql.length > 40 ? `${sql.slice(0, 40)}...` : sql}`,
              }))}
              placeholder="选择最近执行的 SQL"
              style={{ width: '100%', marginBottom: 8 }}
              allowClear
              size="small"
            />
          )}

          <Form
            schema={paramSchema as any}
            uiSchema={uiSchema}
            validator={validator}
            widgets={widgets}
            templates={templates}
            formData={input}
            formContext={formContext}
            onChange={(e) => setInput(e.formData || {})}
          />
        </>
      );
    }

    if (isDbDomain) {
      return (
        <div style={{ marginTop: 4, marginBottom: 4 }}>
          <Space direction="vertical" size={6} style={{ width: '100%' }}>
            {sqlHistory.length > 0 && (
              <Select
                value={input.sql || undefined}
                onChange={(sql) => setInput((prev: any) => ({ ...prev, sql }))}
                options={sqlHistory.map((sql, index) => ({
                  value: sql,
                  label: `最近 #${index + 1} ${sql.length > 40 ? `${sql.slice(0, 40)}...` : sql}`,
                }))}
                placeholder="选择最近执行的 SQL"
                style={{ width: '100%' }}
                allowClear
                size="small"
              />
            )}
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>SQL 语句</Typography.Text>
            <DbSqlEditor
              value={input.sql || ''}
              onChange={(sql) => setInput((prev: any) => ({ ...prev, sql }))}
              disabled={false}
              readonly={false}
              formContext={formContext}
              options={{ functionId, compact: true, autoHeight: true, minHeight: 80, maxHeight: 240 }}
            />
          </Space>
        </div>
      );
    }

    return (
      <div style={{ marginTop: 4, marginBottom: 4 }}>
        <Space direction="vertical" size={6} style={{ width: '100%' }}>
          {!functionDefinition?.config?.paramSchema && (
            <Alert
              type="info"
              showIcon
              message="当前函数未配置入参 Schema，请直接填写 JSON 参数。"
              size="small"
            />
          )}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>输入参数（JSON）</Typography.Text>
          <JsonEditor value={input} onChange={setInput} autoHeight minHeight={80} maxHeight={240} />
        </Space>
      </div>
    );
  };

  const hasResult = result || error;

  return (
    <Spin spinning={loading}>
      <div className="function-test-panel">
        {redisCommandMode && (
          <Segmented
            value={redisMode}
            onChange={(v) => handleRedisModeChange(v as 'structured' | 'raw')}
            options={[
              { label: '结构化命令', value: 'structured' },
              { label: '原始命令行', value: 'raw' },
            ]}
            size="small"
            className="function-test-mode-switch"
          />
        )}
        <div className="function-test-columns">
          {/* 输入区 */}
          <div className="function-test-col function-test-input-col">
            <div className="function-test-col-header">
              <span className="function-test-col-title">测试输入</span>
              <Space size={8}>
                {!conditionMode && paramSchema && (
                  <Button
                    icon={<ThunderboltOutlined />}
                    size="small"
                    onClick={handleGenerateSample}
                  >
                    生成示例
                  </Button>
                )}
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  size="small"
                  onClick={handleTest}
                >
                  执行测试
                </Button>
              </Space>
            </div>
            <div className="function-test-col-body">
              {conditionMode && (
                <Space direction="vertical" size={6} style={{ width: '100%', marginBottom: 12 }}>
                  <Select
                    value={selectedSchemaName}
                    onChange={handleSchemaChange}
                    options={schemas.map((s) => ({
                      value: s.schemaName || s.name || '',
                      label: `${s.schemaName || s.name || '未命名'}${s.description ? ` - ${s.description}` : ''}${s.schemaType ? ` [${s.schemaType}]` : ''}`,
                    }))}
                    placeholder="选择测试 Schema（可选）"
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    size="small"
                    style={{ width: '100%' }}
                  />
                  {schemaFields.length > 0 && (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      已加载字段：{schemaFields.slice(0, 8).join(', ')}
                      {schemaFields.length > 8 ? ` 等 ${schemaFields.length} 个字段` : ''}
                    </Typography.Text>
                  )}
                  <div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>测试数据（JSON）</Typography.Text>
                    <JsonEditor
                      value={testData}
                      onChange={setTestData}
                      autoHeight
                      minHeight={80}
                      maxHeight={200}
                    />
                  </div>
                </Space>
              )}
              {renderInputForm()}
            </div>
          </div>

          {/* 结果区 */}
          <div className="function-test-col function-test-result-col">
            <div className="function-test-col-header">
              <span className="function-test-col-title">测试结果</span>
              {result && (
                <Tag color={result.success ? 'success' : 'error'} size="small">
                  {result.success ? '执行成功' : '执行失败'} · {result.durationMs}ms
                </Tag>
              )}
            </div>
            <div className="function-test-col-body">
              {error && (
                <div className="function-test-error">
                  <Tag color="error" size="small">请求失败</Tag>
                  <div className="function-test-error-message">{error}</div>
                </div>
              )}
              {result ? (
                <div className="function-test-result-content">
                  {result.success === false && (
                    <div className="function-test-result-section">
                      <Alert
                        type="error"
                        showIcon
                        message={
                          <Space size={8}>
                            <span>函数执行失败</span>
                            {result.errorType && <Tag color="error" size="small">{result.errorType}</Tag>}
                          </Space>
                        }
                        description={result.error}
                      />
                      {result.errorStack && (
                        <Collapse ghost size="small" style={{ marginTop: 8 }}>
                          <Collapse.Panel header="异常堆栈" key="stack">
                            <pre style={{ fontSize: 11, maxHeight: 240, overflow: 'auto', background: '#fff2f0', padding: 8, borderRadius: 4 }}>
                              {result.errorStack}
                            </pre>
                          </Collapse.Panel>
                        </Collapse>
                      )}
                    </div>
                  )}
                  <div className="function-test-result-section">
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>输出</Typography.Text>
                    <JsonEditor value={result.output} readOnly autoHeight minHeight={80} maxHeight={360} />
                  </div>
                  {functionDefinition?.config?.outputSchema && result.outputValid !== undefined && (
                    <div className="function-test-result-section">
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>出参 Schema 校验</Typography.Text>
                      <div style={{ marginTop: 4 }}>
                        <Tag color={result.outputValid ? 'success' : 'error'} size="small">
                          {result.outputValid ? '校验通过' : '校验失败'}
                        </Tag>
                        {result.validationErrors && result.validationErrors.length > 0 && (
                          <ul style={{ margin: '8px 0 0', paddingLeft: 16, fontSize: 12, color: '#cf1322' }}>
                            {result.validationErrors.map((err: string, idx: number) => (
                              <li key={idx}>{err}</li>
                            ))}
                          </ul>
                        )}
                      </div>
                    </div>
                  )}
                  {result.logs && result.logs.length > 0 && (
                    <div className="function-test-result-section">
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>日志</Typography.Text>
                      <pre className="function-test-logs">{result.logs.join('\n')}</pre>
                    </div>
                  )}
                </div>
              ) : !error ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="执行测试后在此查看结果"
                  className="function-test-empty"
                />
              ) : null}
            </div>
          </div>
        </div>
      </div>
    </Spin>
  );
};

export default FunctionTestPanel;
