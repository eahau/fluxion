// features/redis-function-test/ui/RedisTestPanel.tsx
// === Redis 函数测试 Feature 完整独立 UI 面板 ===
// 职责：结构化命令 / 原始命令行 两种模式切换、执行测试
// 只 import 本 feature + shared + entities，不引其他 features

import React, { useMemo } from 'react';
import { Alert, Button, Empty, Segmented, Space, Spin, Tag, Typography, Collapse } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import Form from '@rjsf/antd';
import validator from '@rjsf/validator-ajv8';
import JsonEditor from '@/components/JsonEditor';
import RedisRawEditor from '@/pages/workflow/components/widgets/RedisRawEditor';
import SearchableSelectWidget from '@/pages/workflow/components/widgets/SearchableSelectWidget';
import { resolveRedisCommandUiSchema, resolveDynamicWidgets } from '@/utils/schemaUiResolver';
import { useRedisTest } from '../model/useRedisTest';
import { ResultTableOutput } from '@/shared';

export interface RedisTestPanelProps {
  functionId: string;
  functionDefinition?: { config?: { paramSchema?: any; domain?: string; outputSchema?: any } } | null;
  onExecuteTest?: (payload: { inputs: any }) => Promise<any>;
}

const RedisTestPanel: React.FC<RedisTestPanelProps> = ({ functionId, functionDefinition, onExecuteTest }) => {
  const paramSchemaFromProps = useMemo(() => {
    const raw = functionDefinition?.config?.paramSchema;
    if (!raw) return null;
    return typeof raw === 'string' ? JSON.parse(raw) : raw;
  }, [functionDefinition]);

  const uc = useRedisTest({
    functionId,
    initialInput: {},
    customExecuteTest: onExecuteTest,
    paramSchemaFromProps,
    domainFromProps: functionDefinition?.config?.domain as string | undefined,
  });

  const hasSchema = !!(uc.paramSchema && Object.keys(uc.paramSchema).length > 0);
  const dynamicWidgets = useMemo(
    () => (hasSchema ? resolveDynamicWidgets(uc.paramSchema || {}) : {}),
    [hasSchema, uc.paramSchema],
  );
  const redisHiddenFields = useMemo(
    () => (hasSchema ? resolveRedisCommandUiSchema(uc.paramSchema as Record<string, any>, uc.input, uc.mode) : {}),
    [hasSchema, uc.paramSchema, uc.input, uc.mode],
  );

  const uiSchema = useMemo(() => {
    const base: Record<string, any> = {
      ...{
        params: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
        data: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
      },
      ...dynamicWidgets,
      ...redisHiddenFields,
    };
    if (uc.mode === 'raw') {
      base.raw = { 'ui:widget': 'redisRawEditor', 'ui:options': { autoHeight: true, minHeight: 60, maxHeight: 180 } };
    } else {
      base.command = { 'ui:widget': 'searchableSelect' };
      if (base.commands?.items) {
        base.commands.items = { ...(base.commands.items || {}), command: { 'ui:widget': 'searchableSelect' } };
      }
    }
    base['ui:submitButtonOptions'] = { norender: true };
    return base;
  }, [dynamicWidgets, redisHiddenFields, uc.mode]);

  const widgets: Record<string, any> = {
    jsonCode: JsonEditor as any,
    redisRawEditor: RedisRawEditor as any,
    searchableSelect: SearchableSelectWidget as any,
  };

  const renderInput = () => {
    if (hasSchema) {
      return (
        <>
          <Form
            schema={uc.sanitizedParamSchema as any}
            uiSchema={uiSchema}
            validator={validator}
            widgets={widgets}
            formData={uc.input}
            onChange={(e) => uc.setInput(e.formData || {})}
          />
          <div style={{ marginTop: 8, display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" icon={<PlayCircleOutlined />} size="middle" onClick={uc.handleTest}>
              执行测试
            </Button>
          </div>
        </>
      );
    }

    return (
      <div style={{ border: '2px solid #c41d7f', borderRadius: 8, padding: '8px 10px', marginTop: 4, marginBottom: 4, background: '#fff0f6' }}>
        <Typography.Text type="secondary" style={{ fontSize: 11, color: '#c41d7f', fontWeight: 600 }}>
          🔴 Redis 函数测试 · 兜底模式（未检测到 Schema）
        </Typography.Text>
        <Space direction="vertical" size={6} style={{ width: '100%', marginTop: 6 }}>
          {uc.mode === 'raw' ? (
            <>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>Redis 命令（原始）</Typography.Text>
              <RedisRawEditor
                value={uc.input?.raw || ''}
                onChange={(raw: string) => uc.setInput((p) => ({ ...p, raw }))}
                options={{ autoHeight: true, minHeight: 120, maxHeight: 360 }}
              />
            </>
          ) : (
            <>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>输入参数（JSON）</Typography.Text>
              <JsonEditor value={uc.input} onChange={uc.setInput} autoHeight minHeight={80} maxHeight={240} />
            </>
          )}
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
        <Segmented
          value={uc.mode}
          onChange={(v) => uc.setMode(v as any)}
          options={[
            { label: '结构化命令', value: 'structured' },
            { label: '原始命令行', value: 'raw' },
          ]}
          size="small"
          style={{ marginBottom: 8 }}
        />
        <div className="function-test-columns">
          <div className="function-test-col function-test-input-col">
            <div className="function-test-col-header">
              <span className="function-test-col-title">Redis 函数 · 测试输入</span>
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

export default RedisTestPanel;
