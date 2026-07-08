// features/db-function-test/ui/DbTestPanel.tsx
// === DB 函数测试 Feature 完整独立 UI 面板 ===
// 职责：渲染 DB 函数的测试（SQL 编辑器、数据源下拉、表结构预览、历史）
// 只 import 本 feature + shared + entities，不引其他 features

import React, { useMemo } from 'react';
import { Alert, Empty, Select, Space, Spin, Tag, Typography, Collapse } from 'antd';
import Form from '@rjsf/antd';
import validator from '@rjsf/validator-ajv8';
import JsonEditor from '@/components/JsonEditor';
import DbSqlEditor from '@/pages/workflow/components/widgets/DbSqlEditor';
import { useDbTest } from '../model/useDbTest';
import { toDatasourceOptions } from '../lib/utils';
import { ResultTableOutput } from '@/shared';
import TableBrowser from './TableBrowser';
import DbSqlActionBar from './DbSqlActionBar';
import { resolveDynamicWidgets, BASE_FALLBACK_UI_SCHEMA } from './_sharedUiSchema';

export interface DbTestPanelProps {
  functionId: string;
  functionDefinition?: { config?: { paramSchema?: any; domain?: string; outputSchema?: any } } | null;
  onExecuteTest?: (payload: { inputs: any }) => Promise<any>;
}

const DbTestPanel: React.FC<DbTestPanelProps> = ({ functionId, functionDefinition, onExecuteTest }) => {
  const paramSchemaFromProps = useMemo(() => {
    const raw = functionDefinition?.config?.paramSchema;
    if (!raw) return null;
    return typeof raw === 'string' ? JSON.parse(raw) : raw;
  }, [functionDefinition]);

  const uc = useDbTest({
    functionId,
    initialInput: functionDefinition?.config?.paramSchema
      ? undefined
      : { sql: '' },
    customExecuteTest: onExecuteTest,
    paramSchemaFromProps,
    domainFromProps: functionDefinition?.config?.domain as string | undefined,
  });

  const datasourceOptions = useMemo(
    () => toDatasourceOptions(uc.datasources as any),
    [uc.datasources],
  );

  const hasSchema = !!(uc.paramSchema && Object.keys(uc.paramSchema).length > 0);
  const formContext = useMemo(() => ({
    params: uc.input,
    dataSource: uc.input.dataSource || uc.selectedDatasource,
    table: uc.input.table,
    inputSchemaFields: [] as string[],
    upstreamFields: [] as string[],
    upstreamFieldTypes: {} as Record<string, any>,
    upstreamFieldMeta: {} as Record<string, any>,
    bindingPaths: [] as string[],
    declaredDepPaths: [] as string[],
    onUpdateObjectByPath: (() => {}) as any,
  }), [uc.input, uc.selectedDatasource]);

  const renderInput = () => {
    if (hasSchema && (uc.paramSchema as any)?.properties?.sql) {
      const uiSchema = {
        ...BASE_FALLBACK_UI_SCHEMA,
        ...resolveDynamicWidgets(uc.paramSchema || {}),
        sql: { 'ui:widget': 'dbSqlEditor', 'ui:options': { functionId, compact: true, autoHeight: true, minHeight: 80, maxHeight: 240 } },
        'ui:submitButtonOptions': { norender: true },
      };
      const widgets: Record<string, any> = {
        jsonCode: JsonEditor as any,
        dbSqlEditor: DbSqlEditor as any,
      };
      return (
        <>
          <Form
            schema={uc.filteredParamSchema as any}
            uiSchema={uiSchema}
            validator={validator}
            widgets={widgets}
            formData={uc.input}
            formContext={formContext}
            onChange={(e) => uc.setInput(e.formData || {})}
          />
          <DbSqlActionBar
            onRunTest={uc.handleTest}
            sqlHistory={uc.sqlHistory as any[]}
            onSelectSql={(sql) => uc.setInput((prev: any) => ({ ...prev, sql }))}
            tableBrowser={
              <TableBrowser
                selectedDatasource={uc.selectedDatasource}
                tableBrowserTable={uc.tableBrowserTable}
                setTableBrowserTable={uc.setTableBrowserTable}
                tableBrowserTables={uc.tableBrowserTables}
                tableBrowserLoading={uc.tableBrowserLoading}
                tableBrowserCols={uc.tableBrowserCols as any[]}
              />
            }
          />
        </>
      );
    }

    return (
      <div style={{ border: '2px solid #52c41a', borderRadius: 8, padding: '8px 10px', marginTop: 4, marginBottom: 4, background: '#f6ffed' }}>
        <Typography.Text type="secondary" style={{ fontSize: 11, color: '#52c41a', fontWeight: 600 }}>
          🟢 DB 函数测试 · 直接编辑 SQL
        </Typography.Text>
        <div style={{ width: '100%', marginTop: 6 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>SQL 语句</Typography.Text>
          <DbSqlEditor
            value={uc.input.sql || ''}
            onChange={(sql) => uc.setInput((prev: any) => ({ ...prev, sql }))}
            disabled={false} readonly={false}
            formContext={formContext}
            options={{ functionId, compact: true, autoHeight: true, minHeight: 80, maxHeight: 240 }}
          />
          <DbSqlActionBar
            onRunTest={uc.handleTest}
            sqlHistory={uc.sqlHistory as any[]}
            onSelectSql={(sql) => uc.setInput((prev: any) => ({ ...prev, sql }))}
            tableBrowser={
              <TableBrowser
                selectedDatasource={uc.selectedDatasource}
                tableBrowserTable={uc.tableBrowserTable}
                setTableBrowserTable={uc.setTableBrowserTable}
                tableBrowserTables={uc.tableBrowserTables}
                tableBrowserLoading={uc.tableBrowserLoading}
                tableBrowserCols={uc.tableBrowserCols as any[]}
              />
            }
          />
        </div>
      </div>
    );
  };

  const hasResult = uc.result || uc.errorMessage;

  return (
    <Spin spinning={uc.loading}>
      <div className="function-test-panel">
        <div className="function-test-columns">
          <div className="function-test-col function-test-input-col">
            <div className="function-test-col-header">
              <span className="function-test-col-title">DB 函数 · 测试输入</span>
              <Space size={8}>
                {datasourceOptions.length > 0 && (
                  <Select
                    value={uc.selectedDatasource}
                    onChange={(v) => uc.setSelectedDatasource(v)}
                    options={datasourceOptions}
                    style={{ width: 180 }} size="small" placeholder="选择数据源"
                  />
                )}
              </Space>
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
                    <ResultTableOutput output={uc.result.output} isDbDomain />
                  </div>
                  {functionDefinition?.config?.outputSchema && uc.result.outputValid !== undefined && (
                    <div className="function-test-result-section">
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>出参 Schema 校验</Typography.Text>
                      <div style={{ marginTop: 4 }}>
                        <Tag color={uc.result.outputValid ? 'success' : 'error'} size="small">
                          {uc.result.outputValid ? '校验通过' : '校验失败'}
                        </Tag>
                        {uc.result.validationErrors && uc.result.validationErrors.length > 0 && (
                          <ul style={{ margin: '8px 0 0', paddingLeft: 16, fontSize: 12, color: '#cf1322' }}>
                            {uc.result.validationErrors.map((err: string, idx: number) => <li key={idx}>{err}</li>)}
                          </ul>
                        )}
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

export default DbTestPanel;
