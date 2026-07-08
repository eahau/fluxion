// features/condition-function-test/ui/ConditionRjsfTemplates.tsx
// Condition/Filter Feature 专属 RJSF 模板（规则组合卡片 + 规则数组可滚动）

import React, { Fragment, useMemo } from 'react';
import {
  Space, Typography, Tooltip, Segmented, Select, Empty, Tag, Button,
} from 'antd';
import { InfoCircleOutlined, PlusOutlined } from '@ant-design/icons';
import CompactRulesEditor from '@/pages/function/components/widgets/CompactRulesEditor';
import JsonCodeEditor from '@/pages/workflow/components/widgets/JsonCodeEditor';
import DbSqlEditor from '@/pages/workflow/components/widgets/DbSqlEditor';
import DbTableSelect from '@/pages/workflow/components/widgets/DbTableSelect';
import DbColumnsSelect from '@/pages/workflow/components/widgets/DbColumnsSelect';
import DbColumnsKeyValue from '@/pages/workflow/components/widgets/DbColumnsKeyValue';
import BindingInput from '@/pages/workflow/components/widgets/BindingInput';
import RedisRawEditor from '@/pages/workflow/components/widgets/RedisRawEditor';
import SearchableSelectWidget from '@/pages/workflow/components/widgets/SearchableSelectWidget';
import FieldAutoComplete from '@/pages/workflow/components/widgets/FieldAutoComplete';
import OperatorSelectWidget from '@/pages/workflow/components/widgets/OperatorSelectWidget';
import JexlExpressionEditor from '@/pages/workflow/components/widgets/JexlExpressionEditor';

const { Text } = Typography;

export const LOGIC_OPTIONS = [
  { value: 'and', label: '全部满足 (AND)' },
  { value: 'or', label: '任一满足 (OR)' },
];

export const CONDITION_BASE_UI_SCHEMA = {
  params: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  data: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  where: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  config: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  script: { 'ui:widget': 'textarea', 'ui:options': { rows: 4 } },
};

export const RulesCombineAwareObjectFieldTemplate: React.FC<any> = (props) => {
  const { schema, formData, onChange, registry, idSchema, disabled, readonly, formContext } = props;
  const { TitleField, DescriptionField } = registry?.templates || {};
  const properties = (props as any).properties || [];
  const { Text: TextInner } = Typography;

  const combineKeys = ['logic', 'rulesGlobalLogicEnabled', 'rulesGlobalLogicOperator', 'rules'];
  const propsObj = (schema as any)?.properties || {};
  const hasCombineGroup =
    'logic' in propsObj && 'rulesGlobalLogicEnabled' in propsObj && 'rulesGlobalLogicOperator' in propsObj;

  const mode = formData?.rulesGlobalLogicEnabled ? 'uniform' : 'perRule';
  const defaultLogic = formData?.logic || 'and';
  const globalOp = formData?.rulesGlobalLogicOperator || defaultLogic;
  const ctxUpdater = (formContext as any)?.onUpdateObjectByPath as
    | ((id: string, leafPatch: Record<string, any>) => void)
    | undefined;
  const fieldId = idSchema?.$id as string | undefined;
  const updatePatch = (patch: any) => {
    if (ctxUpdater && typeof fieldId === 'string') {
      ctxUpdater(fieldId, patch);
      return;
    }
    if (typeof onChange === 'function') {
      try { onChange({ ...(formData || {}), ...patch }); } catch { /* ignore */ }
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
            display: 'flex', flexWrap: 'wrap', alignItems: 'center',
            justifyContent: 'space-between', gap: 6, padding: '6px 8px',
            border: '1px solid #e6f0ff', borderRadius: 6, background: '#fafcff', marginBottom: 10,
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
              size="small" disabled={disabled || readonly} value={mode}
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
                size="small" style={{ width: 104 }} disabled={disabled || readonly}
                value={globalOp} options={LOGIC_OPTIONS}
                onChange={(v) => updatePatch({ rulesGlobalLogicOperator: v })}
              />
            ) : (
              <Tooltip title="独立连接模式下，新增规则未单独声明逻辑时使用此默认值。">
                <Space size={4}>
                  <Text style={{ fontSize: 11, color: '#8c8c8c' }}>默认</Text>
                  <Select
                    size="small" style={{ width: 104 }} disabled={disabled || readonly}
                    value={defaultLogic} options={LOGIC_OPTIONS}
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

export const ScrollableRulesArrayFieldTemplate: React.FC<any> = (props) => {
  const { items, canAdd, onAddClick, schema, registry, idSchema, title, disabled, readonly } = props;
  const { TitleField, DescriptionField } = registry?.templates || {};
  const itemsArray = (items as any[]) || [];
  return (
    <fieldset className="rjsf-fieldset" style={{ border: 'none', padding: 0, margin: 0 }}>
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
          marginTop: 6, maxHeight: 340, overflowY: 'auto', overflowX: 'hidden',
          padding: '4px 2px 0 0', border: '1px solid #f0f0f0', borderRadius: 6,
          background: '#fcfcfd', scrollbarWidth: 'thin',
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
            <Fragment key={item?.key ?? `rule-${idx}`}>{item?.children}</Fragment>
          ))
        )}
        {canAdd && !disabled && !readonly && (
          <div
            style={{
              position: 'sticky', bottom: 0, background: '#fff', padding: '4px 0 2px',
              marginTop: 4, borderTop: '1px solid #f5f5f5', zIndex: 1,
            }}
          >
            <Button type="dashed" size="small" block icon={<PlusOutlined />} onClick={onAddClick}>
              添加规则
            </Button>
          </div>
        )}
      </div>
    </fieldset>
  );
};

export const RuleCardHorizontalObjectTemplate: React.FC<any> = (props) => {
  const { schema, registry, idSchema } = props;
  const properties = (props as any).properties || [];
  const { TitleField, DescriptionField } = registry?.templates || {};
  const { Text: TextInner } = Typography;

  const idStr: string = idSchema?.$id || '';
  const idxMatch = idStr.match(/_(\d+)(?:_|$)/);
  const index = idxMatch ? Number(idxMatch[1]) : 0;
  const isFirst = index === 0;

  const findProp = (name: string) =>
    properties.find((p: any) => p && typeof p === 'object' && p.name === name);
  const logicProp = findProp('logic');
  const fieldProp = findProp('field');
  const opProp = findProp('operator');
  const valueProp = findProp('value');
  const negateProp = findProp('negate');

  const coreNames = ['logic', 'field', 'operator', 'value', 'negate'];
  const extraProps = properties.filter(
    (p: any) => !(p && typeof p === 'object' && coreNames.includes(p.name)),
  );

  return (
    <div
      style={{
        border: '1px solid #eef1f6', borderRadius: 8, background: '#fff',
        padding: '6px 8px 8px', marginBottom: 8,
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
          display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8,
        }}
      >
        <div style={{ minWidth: 108, flex: '0 0 auto' }}>
          {isFirst ? (
            <Tooltip title={`规则 ${index + 1}，第 1 条规则逻辑从组合模式继承`}>
              <Tag color="blue" style={{ margin: 0 }}>
                <TextInner type="secondary" style={{ fontSize: 11 }}>
                  ① 规则 {index + 1}
                </TextInner>
              </Tag>
            </Tooltip>
          ) : logicProp?.content}
        </div>
        <div style={{ minWidth: 220, flex: '2 1 220px', maxWidth: '40%' }}>{fieldProp?.content}</div>
        <div style={{ minWidth: 148, flex: '1 1 148px', maxWidth: '26%' }}>{opProp?.content}</div>
        <div style={{ minWidth: 200, flex: '2 1 200px', maxWidth: '35%' }}>{valueProp?.content}</div>
        <div style={{ minWidth: 72, flex: '0 0 auto' }}>{negateProp?.content}</div>
      </div>

      {extraProps.length > 0 && (
        <div style={{ marginTop: 6 }}>
          {extraProps.map((p: any, idx: number) => (
            <Fragment key={idx}>{p?.content ?? p}</Fragment>
          ))}
        </div>
      )}

      {(props as any).children}
    </div>
  );
};

export const templates: Record<string, any> = {
  RulesCombineAwareObjectFieldTemplate,
  ScrollableRulesArrayFieldTemplate,
  RuleCardHorizontalObjectTemplate,
};

export const widgets: Record<string, any> = {
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
