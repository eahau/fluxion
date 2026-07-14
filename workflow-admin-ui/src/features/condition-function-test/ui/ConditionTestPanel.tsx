// features/condition-function-test/ui/ConditionTestPanel.tsx
// === Condition/Filter 函数测试 Feature 完整独立 UI 面板 ===
// 职责：Schema 选择 + 测试数据 JSON + 条件规则编辑 + 结果展示
// 对于 filter 节点：使用 CompactRulesEditor 编辑规则，支持从 nodeParams 预填充

import React, { useCallback, useMemo } from 'react';
import { Alert, Button, Collapse, Empty, Input, Select, Space, Spin, Switch, Tag, Typography } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import Form from '@rjsf/antd';
import validator from '@rjsf/validator-ajv8';
import JsonEditor from '@/components/JsonEditor';
import { useConditionTest } from '../model/useConditionTest';
import { ResultTableOutput } from '@/shared';
import {
  CONDITION_BASE_UI_SCHEMA,
  templates,
  widgets,
  RulesCombineAwareObjectFieldTemplate,
  ScrollableRulesArrayFieldTemplate,
  RuleCardHorizontalObjectTemplate,
} from './ConditionRjsfTemplates';
import { resolveDynamicWidgets } from '@/utils/schemaUiResolver';
import CompactRulesEditor from '@/pages/function/components/widgets/CompactRulesEditor';

export interface ConditionTestPanelProps {
  functionId: string;
  functionDefinition?: { config?: { paramSchema?: any; domain?: string; outputSchema?: any } } | null;
  /** 节点测试时若上游入参 schema 已确定，则锁定字段源并禁用 schema 选择 */
  fixedInputSchema?: Record<string, any>;
  onExecuteTest?: (payload: { inputs: any }) => Promise<any>;
  /** 节点当前配置的参数（如 filter 的 rules），用于预填充测试面板 */
  nodeParams?: Record<string, any>;
  _canExecute?: boolean;
}

function patchConditionObjects(
  objSchema: any,
  uiObj: Record<string, any>,
  combineKeys: string[],
  foundCountRef: { count: number } = { count: 0 },
) {
  if (!objSchema || typeof objSchema !== 'object') return;
  const properties = objSchema.properties || {};
  const keys = Object.keys(properties);
  const hasCombineGroup = combineKeys.every((k) => keys.includes(k)) && keys.includes('rules');
  if (hasCombineGroup) {
    foundCountRef.count += 1;
    uiObj['ui:ObjectFieldTemplate'] = RulesCombineAwareObjectFieldTemplate;
    if (!uiObj['rules']) uiObj['rules'] = {};
    uiObj['rules']['ui:ArrayFieldTemplate'] = ScrollableRulesArrayFieldTemplate;
    if (!uiObj['rules'].items) uiObj['rules'].items = {};
    uiObj['rules'].items['ui:ObjectFieldTemplate'] = RuleCardHorizontalObjectTemplate;
  }
  for (const key of Object.keys(properties)) {
    const propSchema = properties[key];
    if (!uiObj[key]) uiObj[key] = {};
    if ((propSchema as any)?.type === 'array' && (propSchema as any)?.items) {
      if (!uiObj[key].items) uiObj[key].items = {};
      patchConditionObjects((propSchema as any).items, uiObj[key].items, combineKeys, foundCountRef);
    }
    patchConditionObjects(propSchema, uiObj[key], combineKeys, foundCountRef);
  }
  (uiObj as any).__hasConditionObjects = foundCountRef.count > 0;
}

const parseRjsfIdToPath = (id: string): string[] => {
  if (!id) return [];
  if (id === 'root') return [];
  const raw = id.startsWith('root_') ? id.slice('root_'.length) : id;
  if (!raw) return [];
  const parts = raw.includes('.') ? raw.split('.') : raw.split('_');
  return parts.filter((p) => p.length > 0);
};
const updateByPath = (root: any, path: string[], leafPatch: Record<string, any>): any => {
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
};

const ConditionTestPanel: React.FC<ConditionTestPanelProps> = ({ functionId, functionDefinition, fixedInputSchema, onExecuteTest, nodeParams, _canExecute }) => {
  // 是否为 filter 节点测试（使用 CompactRulesEditor 而非 RJSF）
  const isFilterNode = functionId === 'builtin:filter';

  const testDisabled = _canExecute === false;

  // 预填充节点配置的参数（如 filter 的 rules）
  const initialInput = useMemo(() => {
    if (nodeParams && typeof nodeParams === 'object' && Object.keys(nodeParams).length > 0) {
      return { ...nodeParams };
    }
    return {};
  }, [nodeParams]);

  const uc = useConditionTest({
    functionId,
    initialInput,
    customExecuteTest: onExecuteTest,
    functionDefinition: functionDefinition as any,
    fixedInputSchema,
  });

  const schemasForSelect = useMemo(() => uc.schemas, [uc.schemas]);
  const hasConditionObjects = !!(uc.mergedParamSchema && (
    (uc.mergedParamSchema as any).properties?.rules ||
    (uc.mergedParamSchema as any).properties?.conditions
  ));

  const dynamicWidgets = useMemo(
    () => (uc.mergedParamSchema ? resolveDynamicWidgets(uc.mergedParamSchema as Record<string, any>) : {}),
    [uc.mergedParamSchema],
  );

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

  const uiSchema = useMemo(() => {
    const base: Record<string, any> = { ...CONDITION_BASE_UI_SCHEMA, ...dynamicWidgets };
    applyExpressionWidgets(uc.mergedParamSchema || {}, base);
    const combineKeys = ['logic', 'rulesGlobalLogicEnabled', 'rulesGlobalLogicOperator'];
    patchConditionObjects(uc.mergedParamSchema || {}, base, combineKeys);
    base['ui:submitButtonOptions'] = { norender: true };
    return base;
  }, [dynamicWidgets, uc.mergedParamSchema]);

  const onUpdateObjectByPath = useCallback(
    (id: string, patch: Record<string, any>) => {
      uc.setInput((prev: any) => {
        const path = parseRjsfIdToPath(id);
        return updateByPath(prev, path, patch) || {};
      });
    },
    [uc],
  );

  const formContext = useMemo(
    () => ({
      params: uc.input,
      dataSource: undefined as string | undefined,
      table: undefined as string | undefined,
      inputSchemaFields: [] as string[],
      upstreamFields: hasConditionObjects ? uc.schemaFields : [],
      upstreamFieldTypes: hasConditionObjects ? uc.schemaFieldTypes : {},
      upstreamFieldMeta: hasConditionObjects ? uc.schemaFieldMeta : {},
      bindingPaths: hasConditionObjects ? uc.schemaFields : [],
      declaredDepPaths: [] as string[],
      onUpdateObjectByPath,
    }),
    [uc.input, uc.schemaFields, uc.schemaFieldTypes, uc.schemaFieldMeta, hasConditionObjects, onUpdateObjectByPath],
  );

  // filter 节点测试：使用 CompactRulesEditor + 单独控件
  const renderFilterRulesEditor = () => {
    const current = uc.input || {};
    const rules = Array.isArray(current.rules) ? current.rules : [];
    return (
      <div style={{ marginBottom: 8 }}>
        <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8, color: '#595959' }}>
          过滤规则{nodeParams?.rules?.length ? `（已从节点参数同步 ${nodeParams.rules.length} 条）` : ''}
        </div>
        <CompactRulesEditor
          value={rules}
          onChange={(newRules) => uc.setInput((prev: any) => ({ ...(prev || {}), rules: newRules }))}
          fields={uc.schemaFields}
          fieldTypes={uc.schemaFieldTypes as Record<string, any>}
          fieldMeta={uc.schemaFieldMeta as Record<string, any>}
          globalLogicEnabled={!!current.rulesGlobalLogicEnabled}
          globalLogicOperator={current.rulesGlobalLogicOperator || 'and'}
          defaultLogic={current.logic || 'and'}
        />
        {/* 整体取反 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
          <Switch
            size="small"
            checked={!!current.negate}
            onChange={(v) => uc.setInput((prev: any) => ({ ...(prev || {}), negate: v }))}
            checkedChildren="NOT"
            unCheckedChildren="NOT"
          />
          <span style={{ fontSize: 12, color: '#595959' }}>整体取反</span>
        </div>
        {/* AviatorScript 表达式（高级） */}
        <Collapse ghost size="small" style={{ marginTop: 4 }}>
          <Collapse.Panel header={<span style={{ fontSize: 11, color: '#8c8c8c' }}>AviatorScript 表达式（高级）</span>} key="condition">
            <Input.TextArea
              size="small"
              rows={2}
              placeholder="AviatorScript 表达式（例：status == 'active'），rules 为空时生效"
              value={current.condition || ''}
              onChange={(e) => uc.setInput((prev: any) => ({ ...(prev || {}), condition: e.target.value }))}
              style={{ fontSize: 12 }}
            />
          </Collapse.Panel>
        </Collapse>
      </div>
    );
  };

  return (
    <Spin spinning={uc.loading || uc.schemasLoading}>
      <div className="function-test-panel">
        <div className="function-test-columns">
          <div className="function-test-col function-test-input-col">
            <div className="function-test-col-header">
              <span className="function-test-col-title">条件/过滤 · 测试输入</span>
              <Button type="primary" icon={<PlayCircleOutlined />} size="small" onClick={uc.handleTest} disabled={testDisabled}>
                执行测试
              </Button>
            </div>
            <div className="function-test-col-body">
              <Space direction="vertical" size={6} style={{ width: '100%', marginBottom: 12 }}>
                {fixedInputSchema ? (
                  <Select
                    disabled
                    value="(fixed)"
                    options={[{ value: '(fixed)', label: '当前节点入参 Schema（已锁定）' }]}
                    size="small" style={{ width: '100%' }}
                  />
                ) : (
                  <Select
                    value={uc.selectedSchemaName}
                    onChange={(v) => uc.handleSchemaChange(v)}
                    loading={uc.schemasLoading}
                    options={Array.isArray(schemasForSelect) ? schemasForSelect.map((s: any) => ({
                      value: s.schemaName || s.name || '',
                      label: `${s.schemaName || s.name || '未命名'}${s.description ? ` - ${s.description}` : ''}${s.schemaType ? ` [${s.schemaType}]` : ''}`,
                    })) : []}
                    placeholder={
                      uc.schemasLoading
                        ? `正在加载 Schema...（${Array.isArray(schemasForSelect) ? schemasForSelect.length : 0} 条）`
                        : `选择业务对象 Schema（作为字段源）· 共 ${Array.isArray(schemasForSelect) ? schemasForSelect.length : 0} 条可选项`
                    }
                    allowClear showSearch optionFilterProp="label"
                    size="small" style={{ width: '100%' }}
                  />
                )}
                {uc.schemaFields.length > 0 && (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    已加载字段：{uc.schemaFields.slice(0, 8).join(', ')}
                    {uc.schemaFields.length > 8 ? ` 等 ${uc.schemaFields.length} 个字段` : ''}
                  </Typography.Text>
                )}
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>测试数据（JSON）</Typography.Text>
                  <JsonEditor
                    value={uc.testData}
                    onChange={uc.setTestData as any}
                    autoHeight minHeight={80} maxHeight={200}
                  />
                </div>
              </Space>

              {/* filter 节点：使用 CompactRulesEditor */}
              {isFilterNode ? renderFilterRulesEditor() : (
                /* 其他条件函数（conditionBranch 等）：使用 RJSF */
                uc.mergedParamSchema && Object.keys(uc.mergedParamSchema).length > 0 ? (
                  <Form
                    schema={uc.mergedParamSchema as any}
                    uiSchema={uiSchema}
                    validator={validator}
                    widgets={widgets}
                    templates={templates}
                    formData={uc.input}
                    formContext={formContext}
                    onChange={(e) => uc.setInput(e.formData || {})}
                  />
                ) : (
                  <Alert type="info" showIcon message="未检测到条件规则 Schema，请先在元数据中配置 paramSchema（含 rules / conditions 字段）。" size="small" />
                )
              )}
            </div>
          </div>

          <div className="function-test-col function-test-result-col">
            <div className="function-test-col-header">
              <span className="function-test-col-title">测试结果</span>
              {uc.result && (
                <Tag color={uc.result.success ? 'success' : 'error'} size="small">
                  {uc.result.success ? '执行成功' : '执行失败'} · {uc.result.durationMs}ms
                </Tag>
              )}
            </div>
            <div className="function-test-col-body">
              {uc.errorMessage && (
                <div className="function-test-error">
                  <Tag color="error" size="small">请求失败</Tag>
                  <div className="function-test-error-message">{uc.errorMessage}</div>
                </div>
              )}
              {uc.result ? (
                <div className="function-test-result-content">
                  {uc.result.success === false && (
                    <div className="function-test-result-section">
                      <Alert
                        type="error" showIcon
                        message={
                          <Space size={8}>
                            <span>函数执行失败</span>
                            {uc.result.errorType && <Tag color="error" size="small">{uc.result.errorType}</Tag>}
                          </Space>
                        }
                        description={uc.result.error}
                      />
                      {uc.result.errorStack && (
                        <Collapse ghost size="small" style={{ marginTop: 8 }}>
                          <Collapse.Panel header="异常堆栈" key="stack">
                            <pre style={{ fontSize: 11, maxHeight: 240, overflow: 'auto', background: '#fff2f0', padding: 8, borderRadius: 4 }}>
                              {uc.result.errorStack}
                            </pre>
                          </Collapse.Panel>
                        </Collapse>
                      )}
                    </div>
                  )}
                  <div className="function-test-result-section">
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>输出</Typography.Text>
                    <ResultTableOutput output={uc.result.output} />
                  </div>
                  {functionDefinition?.config?.outputSchema && uc.result.outputValid !== undefined && (
                    <div className="function-test-result-section">
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>出参 Schema 校验</Typography.Text>
                      <div style={{ marginTop: 4 }}>
                        <Tag color={uc.result.outputValid ? 'success' : 'error'} size="small">
                          {uc.result.outputValid ? '校验通过' : '校验失败'}
                        </Tag>
                      </div>
                    </div>
                  )}
                  {uc.result.logs && uc.result.logs.length > 0 && (
                    <div className="function-test-result-section">
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>日志</Typography.Text>
                      <pre className="function-test-logs">{uc.result.logs.join('\n')}</pre>
                    </div>
                  )}
                </div>
              ) : !uc.errorMessage ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="执行测试后在此查看结果" className="function-test-empty" />
              ) : null}
            </div>
          </div>
        </div>
      </div>
    </Spin>
  );
};

export default ConditionTestPanel;
