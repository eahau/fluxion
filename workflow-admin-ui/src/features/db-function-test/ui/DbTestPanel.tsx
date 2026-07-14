// features/db-function-test/ui/DbTestPanel.tsx
// === DB 函数测试 Feature 完整独立 UI 面板 ===
// 布局（上下结构，上层左右分栏）：
//   顶部：数据源下拉（DB 类型资源）
//   上层左侧 70%：SQL 编辑器 + 左下：执行测试 + 历史记录
//   上层右侧 30%：常驻表结构面板（数据源下拉 + 表下拉 + 搜索 + 列详情）
//   下层：测试结果区（表格模式，列值过长截断+悬浮完整，列头悬浮类型/注释；紧凑间距）

import React, { useMemo, useState } from 'react';
import {
  Alert, Card, Col, Divider, Empty, Pagination, Row, Select, Space, Spin, Tag,
  Tooltip, Typography, Collapse, Button, message,
} from 'antd';
import {
  DatabaseOutlined, DownloadOutlined,
} from '@ant-design/icons';
import DbSqlEditor from '@/pages/workflow/components/widgets/DbSqlEditor';
import { useDbTest } from '../model/useDbTest';
import { toDatasourceOptions } from '../lib/utils';
import TableBrowser from './TableBrowser';
import DbSqlActionBar from './DbSqlActionBar';

export interface DbTestPanelProps {
  functionId: string;
  functionDefinition?: { config?: { paramSchema?: any; domain?: string; outputSchema?: any } } | null;
  onExecuteTest?: (payload: { inputs: any }) => Promise<any>;
  appId?: number | string | null;
  _canExecute?: boolean;
}

const PAGE_SIZE = 10;

/** 显示字符上限（超过则截断加 Tooltip） */
const MAX_CELL_CHARS = 80;

/** 将任意 JS 值安全转换为可导出的 JSON 字符串（处理 undefined / BigInt / 循环引用 / Error） */
function safeJsonStringify(value: any): string {
  const seen = new WeakSet();
  return JSON.stringify(
    value,
    (_k, v) => {
      if (typeof v === 'bigint') return String(v);
      if (typeof v === 'undefined') return null;
      if (v instanceof Error) return { name: v.name, message: v.message, stack: v.stack };
      if (typeof v === 'object' && v !== null) {
        if (seen.has(v)) return '[Circular]';
        seen.add(v);
      }
      return v;
    },
    2,
  );
}

/** 浏览器触发下载 Blob */
function triggerDownload(filename: string, content: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

const tsFileStamp = () => {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
};

/** 从 DB 结果对象中抽取出 rows 数组（兼容多种返回结构） */
function extractRows(output: any): any[] {
  if (output == null) return [];
  if (Array.isArray(output)) return output;
  if (Array.isArray(output.rows)) return output.rows;
  if (output.data && Array.isArray(output.data.rows)) return output.data.rows;
  if (output.result && Array.isArray(output.result.rows)) return output.result.rows;
  if (output.list && Array.isArray(output.list)) return output.list;
  return [];
}

function extractMeta(output: any): { columns?: string[]; updateCount?: number; durationMs?: number; rowCount?: number } {
  if (output == null || Array.isArray(output)) return {};
  const meta: any = {};
  if (output.columns != null) meta.columns = output.columns;
  if (typeof output.updateCount === 'number') meta.updateCount = output.updateCount;
  if (typeof output.rowCount === 'number') meta.rowCount = output.rowCount;
  if (typeof output.durationMs === 'number') meta.durationMs = output.durationMs;
  if (typeof output.affectedRows === 'number') meta.updateCount ??= output.affectedRows;
  return meta;
}

function cellValueDisplay(v: any): React.ReactNode {
  if (v === null || v === undefined) {
    return <Typography.Text type="secondary" style={{ fontSize: 12 }}>{String(v)}</Typography.Text>;
  }
  if (typeof v === 'string') {
    if (v.length <= MAX_CELL_CHARS) return <span style={{ fontSize: 12 }}>{v}</span>;
    return (
      <Tooltip title={<div style={{ maxWidth: 520, wordBreak: 'break-all', whiteSpace: 'pre-wrap' }}>{v}</div>}>
        <span style={{ fontSize: 12, cursor: 'help' }}>{v.slice(0, MAX_CELL_CHARS)}…</span>
      </Tooltip>
    );
  }
  if (typeof v === 'number' || typeof v === 'boolean') {
    return <span style={{ fontSize: 12 }}>{String(v)}</span>;
  }
  if (typeof v === 'bigint') {
    const s = String(v);
    return (
      <Tooltip title={s}>
        <Typography.Text code style={{ fontSize: 12 }}>{s}</Typography.Text>
      </Tooltip>
    );
  }
  const s = safeJsonStringify(v);
  if (s.length <= MAX_CELL_CHARS) {
    return <Typography.Text code style={{ fontSize: 12 }}>{s}</Typography.Text>;
  }
  return (
    <Tooltip title={<pre style={{ maxWidth: 640, maxHeight: 420, overflow: 'auto', margin: 0, fontSize: 11, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{s}</pre>}>
      <Typography.Text code style={{ fontSize: 12, cursor: 'help' }}>{s.slice(0, MAX_CELL_CHARS)}…</Typography.Text>
    </Tooltip>
  );
}

const DbTestPanel: React.FC<DbTestPanelProps> = ({ functionId, functionDefinition, onExecuteTest, appId, _canExecute }) => {
  const paramSchemaFromProps = useMemo(() => {
    const raw = functionDefinition?.config?.paramSchema;
    if (!raw) return null;
    return typeof raw === 'string' ? JSON.parse(raw) : raw;
  }, [functionDefinition]);

  const initialInput = useMemo<Record<string, any>>(() => {
    const mergedDefaults: Record<string, any> = {};
    const schema = paramSchemaFromProps;
    if (schema && typeof schema === 'object' && schema.properties) {
      for (const [key, prop] of Object.entries(schema.properties as Record<string, any>)) {
        const def = (prop as any)?.default;
        if (def !== undefined) {
          try { mergedDefaults[key] = typeof def === 'string' ? JSON.parse(def) : def; }
          catch { mergedDefaults[key] = def; }
        }
      }
    }
    if (functionDefinition?.config?.defaultInput) {
      const di = functionDefinition.config.defaultInput;
      const fromDefInput = typeof di === 'string' ? JSON.parse(di) : di;
      if (fromDefInput && typeof fromDefInput === 'object') {
        Object.assign(mergedDefaults, fromDefInput);
      }
    }
    if (!('sql' in mergedDefaults)) mergedDefaults.sql = '';
    return mergedDefaults;
  }, [paramSchemaFromProps, functionDefinition]);

  const uc = useDbTest({
    functionId,
    initialInput,
    customExecuteTest: onExecuteTest,
    paramSchemaFromProps,
    domainFromProps: functionDefinition?.config?.domain as string | undefined,
  });

  const datasourceOptions = useMemo(
    () => toDatasourceOptions(uc.datasources as any),
    [uc.datasources],
  );

  const allowedDatasourceIds = useMemo<Set<string> | undefined>(() => {
    if (appId == null) return undefined;
    return undefined;
  }, [appId]);

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

  const testDisabled = _canExecute === false;

  /** 列注释/类型映射（从表结构浏览的 tableBrowserCols 查） */
  const columnMetaByName = useMemo<Map<string, { dataType?: string; comment?: string }>>(() => {
    const m = new Map<string, { dataType?: string; comment?: string }>();
    (uc.tableBrowserCols || []).forEach((c: any) => {
      if (c && c.name) m.set(String(c.name), { dataType: c.dataType, comment: c.comment });
    });
    return m;
  }, [uc.tableBrowserCols]);

  // ====== 结果区状态 ======
  const [pageNo, setPageNo] = useState(1);

  const rows: any[] = useMemo(() => extractRows(uc.result?.output), [uc.result?.output]);
  const resultMeta = useMemo(() => extractMeta(uc.result?.output), [uc.result?.output]);

  const paginatedRows = useMemo(() => {
    const start = (pageNo - 1) * PAGE_SIZE;
    return rows.slice(start, start + PAGE_SIZE);
  }, [rows, pageNo]);

  const allColumnNames = useMemo(() => {
    if (resultMeta.columns && resultMeta.columns.length) return resultMeta.columns;
    if (rows.length > 0) return Object.keys(rows[0] || {});
    return [] as string[];
  }, [rows, resultMeta.columns]);

  const onExportJson = () => {
    const jsonSource = uc.result?.output;
    if (jsonSource === undefined || jsonSource === null) {
      message.warning('无可导出的数据');
      return;
    }
    try {
      triggerDownload(`db-test_${tsFileStamp()}.json`, safeJsonStringify(jsonSource), 'application/json;charset=utf-8');
      message.success(`已导出 JSON（含 ${rows.length} 行）`);
    } catch (e: any) {
      message.error('JSON 导出失败：' + (e?.message || '未知错误'));
    }
  };

  const handleDatasourceChange = (v: any) => {
    uc.setSelectedDatasource(v);
    uc.setInput((prev: any) => ({ ...prev, dataSource: v }));
  };

  const renderResult = () => {
    if (!uc.result && !uc.errorMessage) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="执行测试后在此查看结果"
          style={{ padding: '20px 0' }}
        />
      );
    }

    return (
      <Space direction="vertical" size={8} style={{ width: '100%' }}>
        {/* 头部：结果状态条 + 导出 JSON */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', rowGap: 6 }}>
          <Space size={6} wrap>
            {uc.result?.errorType && <Tag color="error" size="small">{uc.result.errorType}</Tag>}
            {uc.result?.success ? (
              <Tag color="success" size="small">
                执行成功{uc.result.durationMs ? ` · ${uc.result.durationMs}ms` : (resultMeta.durationMs ? ` · ${resultMeta.durationMs}ms` : '')}
              </Tag>
            ) : (
              uc.result && (
                <Tag color="error" size="small">
                  执行失败{uc.result.durationMs ? ` · ${uc.result.durationMs}ms` : ''}
                </Tag>
              )
            )}
            {rows.length > 0 && (
              <Tag color="geekblue" size="small">
                {rows.length} 行记录
              </Tag>
            )}
            {typeof resultMeta.updateCount === 'number' && resultMeta.updateCount > 0 && (
              <Tag color="cyan" size="small">影响 {resultMeta.updateCount} 行</Tag>
            )}
            {typeof resultMeta.rowCount === 'number' && resultMeta.rowCount !== rows.length && (
              <Tag color="purple" size="small">总 {resultMeta.rowCount} 行</Tag>
            )}
          </Space>
          <Space size={6} align="center">
            <Tooltip title="导出当前结果为 JSON（包含 rows / updateCount / columns 等完整结构）">
              <Button size="small" icon={<DownloadOutlined />} onClick={onExportJson} disabled={!uc.result?.output}>
                导出 JSON
              </Button>
            </Tooltip>
          </Space>
        </div>

        {/* 错误 */}
        {uc.errorMessage && (
          <Alert type="error" showIcon message="请求失败" description={uc.errorMessage} />
        )}
        {uc.result?.success === false && (
          <Alert
            type="error"
            showIcon
            message={
              <Space size={8}>
                <span>函数执行失败</span>
                {uc.result.errorType && <Tag color="error" size="small">{uc.result.errorType}</Tag>}
              </Space>
            }
            description={uc.result.error as any}
          />
        )}
        {uc.result?.success === false && uc.result.errorStack && (
          <Collapse ghost size="small">
            <Collapse.Panel header="异常堆栈" key="stack">
              <pre style={{ fontSize: 11, maxHeight: 240, overflow: 'auto', background: '#fff2f0', padding: 8, borderRadius: 4, margin: 0 }}>
                {uc.result.errorStack}
              </pre>
            </Collapse.Panel>
          </Collapse>
        )}

        {/* 空输出（例如 DDL 成功但无 rows） */}
        {uc.result?.success !== false && rows.length === 0 && (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              typeof resultMeta.updateCount === 'number'
                ? `执行成功，影响 ${resultMeta.updateCount} 行（无返回数据）`
                : '执行成功（无返回数据行）'
            }
            style={{ padding: '14px 0' }}
          />
        )}

        {/* 数据结果 - 表格模式（唯一模式） */}
        {rows.length > 0 && (
          <Card size="small" styles={{ body: { padding: 0 } }}>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                <thead>
                  <tr style={{ background: '#fafafa' }}>
                    <th
                      style={{
                        border: '1px solid #f0f0f0',
                        padding: '6px 10px',
                        width: 56,
                        textAlign: 'center',
                        fontWeight: 600,
                        whiteSpace: 'nowrap',
                        position: 'sticky',
                        top: 0,
                        background: '#fafafa',
                        color: '#8c8c8c',
                        fontSize: 11,
                      }}
                    >
                      #
                    </th>
                    {allColumnNames.map((c) => {
                      const meta = columnMetaByName.get(c);
                      const typeLabel = meta?.dataType;
                      const comment = meta?.comment;
                      const tooltipLines = [
                        `字段名：${c}`,
                        typeLabel ? `类型：${typeLabel}` : null,
                        comment ? `注释：${comment}` : null,
                      ].filter(Boolean) as string[];
                      return (
                        <th
                          key={c}
                          style={{
                            border: '1px solid #f0f0f0',
                            padding: '6px 10px',
                            textAlign: 'left',
                            fontWeight: 600,
                            whiteSpace: 'nowrap',
                            position: 'sticky',
                            top: 0,
                            background: '#fafafa',
                          }}
                        >
                          <Tooltip title={tooltipLines.join('\n') || c} placement="topLeft">
                            <span style={{ cursor: 'help' }}>
                              <Typography.Text code style={{ fontSize: 12 }}>{c}</Typography.Text>
                            </span>
                          </Tooltip>
                          {typeLabel && (
                            <Tag style={{ marginLeft: 6, fontSize: 10, padding: '0 4px', height: 16, lineHeight: '14px' }} color="geekblue">
                              {typeLabel}
                            </Tag>
                          )}
                          {comment && !typeLabel && (
                            <Tooltip title={`注释：${comment}`}>
                              <span style={{ marginLeft: 6, fontSize: 10, color: '#8c8c8c' }} title={comment}>
                                ⓘ {comment.length > 12 ? comment.slice(0, 12) + '…' : comment}
                              </span>
                            </Tooltip>
                          )}
                        </th>
                      );
                    })}
                  </tr>
                </thead>
                <tbody>
                  {paginatedRows.map((row, ri) => {
                    const globalIndex = (pageNo - 1) * PAGE_SIZE + ri + 1;
                    return (
                      <tr key={ri} style={{ verticalAlign: 'top' }}>
                        <td
                          style={{
                            border: '1px solid #f0f0f0',
                            padding: '6px 10px',
                            textAlign: 'center',
                            color: '#8c8c8c',
                            fontSize: 11,
                            background: '#fafafa',
                          }}
                        >
                          {globalIndex}
                        </td>
                        {allColumnNames.map((c) => (
                          <td
                            key={c}
                            style={{
                              border: '1px solid #f0f0f0',
                              padding: '6px 10px',
                              maxWidth: 440,
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              verticalAlign: 'top',
                            }}
                            title={typeof row?.[c] === 'string' && row[c].length > MAX_CELL_CHARS ? row[c] : undefined}
                          >
                            {cellValueDisplay(row == null ? undefined : row[c])}
                          </td>
                        ))}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div style={{ padding: '6px 10px', display: 'flex', justifyContent: 'flex-end' }}>
              <Pagination
                current={pageNo}
                pageSize={PAGE_SIZE}
                total={rows.length}
                showSizeChanger={false}
                onChange={(p) => setPageNo(p)}
                showTotal={(t) => `共 ${t} 行`}
                size="small"
              />
            </div>
          </Card>
        )}

        {/* 原始 JSON（仅在有非空数据时可展开） */}
        {rows.length > 0 && (
          <Collapse ghost size="small">
            <Collapse.Panel header="原始 JSON（完整输出结构）" key="raw">
              <pre
                style={{
                  fontSize: 11,
                  maxHeight: 320,
                  overflow: 'auto',
                  background: '#fafafa',
                  padding: 8,
                  borderRadius: 4,
                  margin: 0,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                }}
              >
                {safeJsonStringify(uc.result?.output)}
              </pre>
            </Collapse.Panel>
          </Collapse>
        )}

        {uc.result?.logs && uc.result.logs.length > 0 && (
          <Collapse ghost size="small">
            <Collapse.Panel header={`日志（${uc.result.logs.length}）`} key="logs">
              <pre style={{ fontSize: 11, maxHeight: 200, overflow: 'auto', background: '#f6ffed', padding: 8, borderRadius: 4, margin: 0 }}>
                {uc.result.logs.join('\n')}
              </pre>
            </Collapse.Panel>
          </Collapse>
        )}
      </Space>
    );
  };

  return (
    <Spin spinning={uc.loading}>
      <div className="function-test-panel" style={{ padding: '2px 2px 4px' }}>
        {/* 顶部：数据源下拉（独立的 DB 资源选择） */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            rowGap: 6,
            padding: '6px 10px',
            border: '1px solid #f0f0f0',
            borderRadius: 6,
            marginBottom: 6,
            background: '#fafcff',
          }}
        >
          <Space size={6} align="center" wrap>
            <DatabaseOutlined style={{ color: '#1677ff' }} />
            <Typography.Text strong style={{ fontSize: 12 }}>
              DB 函数测试
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 10 }}>
              选择数据源 → 编辑 SQL → 执行测试
            </Typography.Text>
          </Space>
          <Select
            value={uc.selectedDatasource}
            onChange={handleDatasourceChange}
            options={datasourceOptions}
            style={{ width: 260 }}
            size="small"
            showSearch
            allowClear
            placeholder="选择数据源（DB 类型资源）"
            optionFilterProp="label"
            loading={uc.datasourcesLoading}
          />
        </div>

        {/* 上层：左右分栏（左 SQL + 右 表结构） */}
        <Row gutter={8} style={{ marginBottom: 6 }} align="stretch">
          <Col xs={24} lg={16} xl={17} style={{ display: 'flex', flexDirection: 'column' }}>
            <Card
              size="small"
              title={
                <Space size={4}>
                  <span style={{ fontSize: 12, fontWeight: 500 }}>SQL 编辑器</span>
                </Space>
              }
              styles={{ body: { padding: 6 } }}
              style={{ flex: 1 }}
            >
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                <DbSqlEditor
                  value={uc.input.sql || ''}
                  onChange={(sql) => uc.setInput((prev: any) => ({ ...prev, sql }))}
                  disabled={false}
                  readonly={false}
                  formContext={formContext}
                  options={{ functionId, compact: true, autoHeight: true, minHeight: 120, maxHeight: 300 }}
                />
                <DbSqlActionBar
                  onRunTest={uc.handleTest}
                  sqlHistory={uc.sqlHistory as any[]}
                  onSelectSql={(sql) => uc.setInput((prev: any) => ({ ...prev, sql }))}
                  runButtonDisabled={testDisabled}
                />
              </Space>
            </Card>
          </Col>

          <Col xs={24} lg={8} xl={7} style={{ display: 'flex', flexDirection: 'column' }}>
            <TableBrowser
              mode="inline"
              selectedDatasource={uc.selectedDatasource}
              onSelectedDatasourceChange={handleDatasourceChange}
              datasources={uc.datasources}
              datasourcesLoading={uc.datasourcesLoading}
              allowedDatasourceIds={allowedDatasourceIds}
              tableBrowserTable={uc.tableBrowserTable}
              setTableBrowserTable={uc.setTableBrowserTable}
              tableBrowserTables={uc.tableBrowserTables}
              tableBrowserLoading={uc.tableBrowserLoading}
              tableBrowserCols={uc.tableBrowserCols as any[]}
            />
          </Col>
        </Row>

        {/* 下层：测试结果 */}
        <Divider orientation="left" style={{ margin: '0 0 4px', fontSize: 12, fontWeight: 600, color: '#262626' }}>
          测试结果
        </Divider>
        <Card size="small" styles={{ body: { padding: 8 } }}>
          {renderResult()}
        </Card>
      </div>
    </Spin>
  );
};

export default DbTestPanel;
