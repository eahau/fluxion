// features/generic-function-test/ui/GenericTestPanel.tsx
// === 通用函数测试 Feature 完整独立 UI 面板 ===
// 适用于：Script 脚本、External HTTP、其他非 DB/Redis/Condition 函数
// 渲染分支：① paramSchema RJSF 表单；② 兜底 JSON 编辑器；③ lockedSchema 模式（paramValidate 等）

import React, { useMemo } from 'react';
import { Alert, Button, Collapse, Empty, Space, Spin, Tag, Typography } from 'antd';
import { PlayCircleOutlined, LockOutlined } from '@ant-design/icons';
import Form from '@rjsf/antd';
import validator from '@rjsf/validator-ajv8';
import JsonEditor from '@/components/JsonEditor';
import JsonCodeEditor from '@/pages/workflow/components/widgets/JsonCodeEditor';
import { resolveDynamicWidgets } from '@/utils/schemaUiResolver';
import { useGenericTest } from '../model/useGenericTest';
import { ResultTableOutput } from '@/shared';

export interface GenericTestPanelProps {
  functionId: string;
  functionDefinition?: { config?: { paramSchema?: any; domain?: string; outputSchema?: any; defaultInput?: any } } | null;
  onExecuteTest?: (payload: { inputs: any }) => Promise<any>;
  /** paramValidate 的锁定校验 Schema（只读展示），测试输入基于此 schema 生成 */
  lockedSchema?: Record<string, any>;
}

const BASE_UI_SCHEMA = {
  params: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  data: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  where: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  config: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  script: { 'ui:widget': 'textarea', 'ui:options': { rows: 4 } },
};

const GenericTestPanel: React.FC<GenericTestPanelProps> = ({ functionId, functionDefinition, onExecuteTest, lockedSchema }) => {
  const initialInput = useMemo(() => {
    const def = functionDefinition?.config?.defaultInput;
    if (!def) return undefined;
    return typeof def === 'string' ? JSON.parse(def) : def;
  }, [functionDefinition]);

  const uc = useGenericTest({
    functionId, initialInput,
    customExecuteTest: onExecuteTest,
    functionDefinition: functionDefinition as any,
  });

  const hasSchema = !!(uc.paramSchema && Object.keys(uc.paramSchema).length > 0);
  // lockedSchema 模式：强制 JSON 编辑器（不渲染 RJSF 多字段表单）
  const forceJsonMode = !!lockedSchema;

  const dynamicWidgets = useMemo(
    () => (hasSchema ? resolveDynamicWidgets(uc.paramSchema as any) : {}),
    [hasSchema, uc.paramSchema],
  );
  const uiSchema = useMemo(() => ({
    ...BASE_UI_SCHEMA,
    ...dynamicWidgets,
    'ui:submitButtonOptions': { norender: true },
  }), [dynamicWidgets]);

  const widgets: Record<string, any> = {
    jsonCode: JsonCodeEditor as any,
  };

  const renderInput = () => {
    // lockedSchema 模式：只读 Schema 参考 + JSON 编辑器（用于 paramValidate 等）
    if (forceJsonMode) {
      return (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          {lockedSchema && (
            <Collapse ghost size="small" defaultActiveKey={[]} style={{ marginBottom: 4 }}>
              <Collapse.Panel
                header={
                  <Space size={4}>
                    <LockOutlined style={{ color: '#1677ff', fontSize: 12 }} />
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>入参校验 Schema（已锁定）</Typography.Text>
                  </Space>
                }
                key="schema"
              >
                <pre style={{ fontSize: 11, maxHeight: 200, overflow: 'auto', background: '#f6f8fa', padding: 8, borderRadius: 4 }}>
                  {JSON.stringify(lockedSchema, null, 2)}
                </pre>
              </Collapse.Panel>
            </Collapse>
          )}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>测试数据（JSON）</Typography.Text>
          <JsonEditor value={uc.input} onChange={uc.setInput as any} autoHeight minHeight={120} maxHeight={300} />
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" icon={<PlayCircleOutlined />} size="middle" onClick={uc.handleTest}>
              执行测试
            </Button>
          </div>
        </Space>
      );
    }

    if (hasSchema) {
      return (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Form
            schema={uc.sanitizedParamSchema as any}
            uiSchema={uiSchema}
            validator={validator}
            widgets={widgets}
            formData={uc.input}
            onChange={(e) => uc.setInput(e.formData || {})}
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" icon={<PlayCircleOutlined />} size="middle" onClick={uc.handleTest}>
              执行测试
            </Button>
          </div>
        </Space>
      );
    }
    return (
      <div style={{ border: '2px solid #fa8c16', borderRadius: 8, padding: '8px 10px', marginTop: 4, marginBottom: 4, background: '#fff7e6' }}>
        <Typography.Text type="secondary" style={{ fontSize: 11, color: '#fa8c16', fontWeight: 600 }}>
          🟠 通用函数测试 · 兜底 JSON 模式（未检测到入参 Schema）
        </Typography.Text>
        <Space direction="vertical" size={6} style={{ width: '100%', marginTop: 6 }}>
          {!functionDefinition?.config?.paramSchema && (
            <Alert type="info" showIcon message="当前函数未配置入参 Schema，请直接填写 JSON 参数。" size="small" />
          )}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>输入参数（JSON）</Typography.Text>
          <JsonEditor value={uc.input} onChange={uc.setInput as any} autoHeight minHeight={80} maxHeight={240} />
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" icon={<PlayCircleOutlined />} size="middle" onClick={uc.handleTest}>
              执行测试
            </Button>
          </div>
        </Space>
      </div>
    );
  };

  return (
    <Spin spinning={uc.loading}>
      <div className="function-test-panel">
        <div className="function-test-columns">
          <div className="function-test-col function-test-input-col">
            <div className="function-test-col-header">
              <span className="function-test-col-title">
                {forceJsonMode ? '入参校验 · 测试输入' : '通用函数 · 测试输入'}
              </span>
            </div>
            <div className="function-test-col-body">{renderInput()}</div>
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

export default GenericTestPanel;
