// features/db-function-test/ui/TableBrowser.tsx
// DB Feature 专属 UI：「查看表结构」抽屉面板 + 右侧常驻内嵌面板
// 功能：
//   1) 数据源下拉（允许选择数据源，若限定了绑定数据源则只展示绑定的，否则顶部加黄条提示）
//   2) 表名下拉 + 搜索（Select filterOption 搜索）
//   3) 列详情 Table（字段名 / 类型 / 注释）

import React, { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Input,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Tooltip,
} from 'antd';
import { TableOutlined, DatabaseOutlined, InfoCircleOutlined, SearchOutlined } from '@ant-design/icons';
import type { DbTableColumn } from '../model/types';
import type { DbDatasourceBrief } from '../api';

export interface TableBrowserProps {
  selectedDatasource?: string;
  onSelectedDatasourceChange?: (v: string | undefined) => void;
  datasources?: DbDatasourceBrief[];
  datasourcesLoading?: boolean;
  allowedDatasourceIds?: Set<string>;

  tableBrowserTable?: string;
  setTableBrowserTable: (value?: string) => void;
  tableBrowserTables: string[];
  tableBrowserLoading?: boolean;
  tableBrowserCols: DbTableColumn[];

  triggerButtonSize?: 'small' | 'middle' | 'large';
  /**
   * 'trigger' — 显示「查看表结构」按钮，点击打开 Drawer（老模式，默认）
   * 'inline'  — 直接渲染面板内容（不显示按钮 & 不使用 Drawer），用于 SQL 编辑器右侧常驻
   */
  mode?: 'trigger' | 'inline';
  /** inline 模式下外层卡片标题。默认「表结构浏览」 */
  inlineTitle?: React.ReactNode;
}

const EMPTY_SET = new Set<string>();

export const TableBrowserContent: React.FC<
  Required<Pick<TableBrowserProps,
    | 'selectedDatasource'
    | 'datasources'
    | 'datasourcesLoading'
    | 'allowedDatasourceIds'
    | 'tableBrowserTables'
    | 'tableBrowserTable'
    | 'setTableBrowserTable'
    | 'tableBrowserLoading'
    | 'tableBrowserCols'>> & { onSelectedDatasourceChange?: TableBrowserProps['onSelectedDatasourceChange'] }
> = ({
  selectedDatasource, onSelectedDatasourceChange, datasources, datasourcesLoading,
  allowedDatasourceIds, tableBrowserTables, tableBrowserTable, setTableBrowserTable,
  tableBrowserLoading, tableBrowserCols,
}) => {
  const [tableKeyword, setTableKeyword] = useState<string>('');

  const filteredDatasources = useMemo<DbDatasourceBrief[]>(() => {
    const allowed = allowedDatasourceIds ?? EMPTY_SET;
    if (allowed.size === 0) return datasources;
    return datasources.filter(
      (ds) =>
        (ds.id && allowed.has(String(ds.id))) ||
        (ds.name && allowed.has(ds.name)) ||
        allowed.has(`${ds.id}`) ||
        allowed.has(`${ds.name}`),
    );
  }, [datasources, allowedDatasourceIds]);

  const bindingRestricted = (allowedDatasourceIds?.size ?? 0) > 0;

  const displayedTables = useMemo(() => {
    const kw = tableKeyword.trim().toLowerCase();
    if (!kw) return tableBrowserTables;
    return tableBrowserTables.filter((t) => t.toLowerCase().includes(kw));
  }, [tableBrowserTables, tableKeyword]);

  const datasourceOptions = useMemo(
    () =>
      filteredDatasources.map((ds) => {
        const label = ds.name || ds.id || '未命名数据源';
        const sub = [ds.type, ds.domain].filter(Boolean).join(' · ') || undefined;
        return {
          value: ds.id ?? label,
          label,
          sub,
          title: sub ? `${label} (${sub})` : label,
        } as const;
      }),
    [filteredDatasources],
  );

  const tableOptions = useMemo(
    () =>
      displayedTables.map((t) => ({
        value: t,
        label: t,
      })),
    [displayedTables],
  );

  return (
    <Space direction="vertical" size={10} style={{ width: '100%' }}>
      {!bindingRestricted && (
        <Alert
          type="warning"
          showIcon
          icon={<InfoCircleOutlined />}
          size="small"
          message={
            <Space size={6} wrap>
              <span>当前数据源访问范围未限定。</span>
              <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                正式使用建议通过「应用详情 → 资源绑定」后再测试
              </Typography.Text>
            </Space>
          }
          style={{ margin: 0 }}
        />
      )}
      {bindingRestricted && filteredDatasources.length === 0 && (
        <Alert
          type="error"
          showIcon
          size="small"
          message="当前应用尚未绑定任何 DB 类型数据源，请先在 应用详情 → 资源绑定 添加。"
          style={{ margin: 0 }}
        />
      )}

      <div>
        <Typography.Text strong style={{ fontSize: 12 }}>
          数据源
        </Typography.Text>
        {onSelectedDatasourceChange ? (
          <Select
            style={{ width: '100%', marginTop: 4 }}
            showSearch
            allowClear
            size="small"
            loading={datasourcesLoading}
            disabled={filteredDatasources.length === 0}
            optionFilterProp="label"
            placeholder={bindingRestricted ? '当前应用绑定的数据源' : '选择数据源（可搜索）'}
            value={selectedDatasource}
            onChange={(v) => onSelectedDatasourceChange(v ?? undefined)}
            options={datasourceOptions as any}
            notFoundContent={
              datasourcesLoading ? (
                <div style={{ textAlign: 'center', padding: 10 }}>
                  <Spin size="small" /> <span style={{ marginLeft: 6 }}>加载中…</span>
                </div>
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无可用数据源" style={{ padding: 6 }} />
              )
            }
          />
        ) : (
          <Tag style={{ marginTop: 4 }} color="geekblue">
            {selectedDatasource || '未指定'}
          </Tag>
        )}
      </div>

      <div>
        <Space size={6} align="end" style={{ width: '100%', justifyContent: 'space-between', flexWrap: 'wrap', rowGap: 4 }}>
          <div style={{ flex: 1, minWidth: 140 }}>
            <Typography.Text strong style={{ fontSize: 12 }}>
              数据表
            </Typography.Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              showSearch
              allowClear
              size="small"
              loading={tableBrowserLoading}
              disabled={!selectedDatasource}
              placeholder={!selectedDatasource ? '先选数据源' : '选择要查看的数据表'}
              value={tableBrowserTable}
              onChange={(v) => setTableBrowserTable(v ?? undefined)}
              options={tableOptions}
              optionFilterProp="label"
              notFoundContent={
                tableBrowserLoading ? (
                  <div style={{ textAlign: 'center', padding: 10 }}>
                    <Spin size="small" /> <span style={{ marginLeft: 6 }}>加载数据表中…</span>
                  </div>
                ) : !selectedDatasource ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="先选择数据源" style={{ padding: 6 }} />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无数据表" style={{ padding: 6 }} />
                )
              }
            />
          </div>
          <Input
            size="small"
            allowClear
            style={{ width: 180, flexShrink: 0 }}
            prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
            placeholder="搜索表名…"
            value={tableKeyword}
            onChange={(e) => setTableKeyword(e.target.value)}
            disabled={!selectedDatasource}
          />
        </Space>
      </div>

      <div>
        {tableBrowserTable && (
          tableBrowserCols.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="（该表未返回列信息）" style={{ padding: 6 }} />
          ) : (
            <Space direction="vertical" size={4} style={{ width: '100%' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Space size={6}>
                  <Tag color="geekblue">{tableBrowserCols.length} 个字段</Tag>
                  <Typography.Text code style={{ fontSize: 11 }}>
                    {tableBrowserTable}
                  </Typography.Text>
                </Space>
              </div>
              <Table
                size="small"
                bordered
                columns={[
                  {
                    title: '字段名',
                    dataIndex: 'name',
                    key: 'name',
                    width: '36%',
                    sorter: (a: any, b: any) => (a.name || '').localeCompare(b.name || ''),
                    render: (v: string) => (
                      <Tooltip title={`点击复制：${v}`}>
                        <Typography.Text
                          code
                          copyable={{ text: v }}
                          style={{ fontSize: 11 }}
                        >
                          {v}
                        </Typography.Text>
                      </Tooltip>
                    ),
                  },
                  {
                    title: '类型',
                    dataIndex: 'dataType',
                    key: 'dataType',
                    width: '28%',
                    render: (v: any) =>
                      v ? (
                        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                          {v}
                        </Typography.Text>
                      ) : (
                        '-'
                      ),
                  },
                  {
                    title: '注释',
                    dataIndex: 'comment',
                    key: 'comment',
                    ellipsis: true,
                    render: (v: any) =>
                      v ? (
                        <Tooltip title={v}>
                          <span style={{ fontSize: 11 }}>{v}</span>
                        </Tooltip>
                      ) : (
                        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                          -
                        </Typography.Text>
                      ),
                  },
                ]}
                dataSource={tableBrowserCols.map((c, idx) => ({ ...c, __idx: String(idx) }))}
                pagination={false}
                rowKey="__idx"
                scroll={{ y: Math.min(360, 48 + tableBrowserCols.length * 40) }}
              />
            </Space>
          )
        )}
        {!tableBrowserTable && (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={selectedDatasource ? '选择数据表以查看字段' : '先选择数据源'}
            style={{ padding: '18px 0' }}
          />
        )}
      </div>
    </Space>
  );
};

export const TableBrowser: React.FC<TableBrowserProps> = ({
  selectedDatasource,
  onSelectedDatasourceChange,
  datasources = [],
  datasourcesLoading = false,
  allowedDatasourceIds,
  tableBrowserTable,
  setTableBrowserTable,
  tableBrowserTables,
  tableBrowserLoading = false,
  tableBrowserCols,
  triggerButtonSize = 'small',
  mode = 'trigger',
  inlineTitle,
}) => {
  const [open, setOpen] = useState(false);

  if (mode === 'inline') {
    return (
      <Card
        size="small"
        title={
          inlineTitle ?? (
            <Space size={6}>
              <DatabaseOutlined style={{ color: '#1677ff' }} />
              <span style={{ fontSize: 13, fontWeight: 500 }}>表结构浏览</span>
            </Space>
          )
        }
        styles={{ body: { padding: 10 } }}
        style={{ height: '100%' }}
      >
        <TableBrowserContent
          selectedDatasource={selectedDatasource}
          onSelectedDatasourceChange={onSelectedDatasourceChange}
          datasources={datasources}
          datasourcesLoading={datasourcesLoading}
          allowedDatasourceIds={allowedDatasourceIds}
          tableBrowserTables={tableBrowserTables}
          tableBrowserTable={tableBrowserTable}
          setTableBrowserTable={setTableBrowserTable}
          tableBrowserLoading={tableBrowserLoading}
          tableBrowserCols={tableBrowserCols}
        />
      </Card>
    );
  }

  const triggerNode = (
    <Tooltip title="查看当前数据源下的表结构与字段详情">
      <Button
        type="default"
        size={triggerButtonSize}
        icon={<TableOutlined />}
        onClick={() => setOpen(true)}
      >
        查看表结构
      </Button>
    </Tooltip>
  );

  return (
    <>
      {triggerNode}
      <Drawer
        title={
          <Space size={8}>
            <DatabaseOutlined style={{ color: '#1677ff' }} />
            <span>表结构浏览</span>
            <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>
              （选择数据源 → 选择表 → 查看字段 / 类型 / 注释）
            </Typography.Text>
          </Space>
        }
        open={open}
        onClose={() => setOpen(false)}
        width={Math.max(520, Math.min(760, typeof window === 'undefined' ? 640 : window.innerWidth * 0.55))}
        destroyOnHidden
        maskClosable
      >
        <TableBrowserContent
          selectedDatasource={selectedDatasource}
          onSelectedDatasourceChange={onSelectedDatasourceChange}
          datasources={datasources}
          datasourcesLoading={datasourcesLoading}
          allowedDatasourceIds={allowedDatasourceIds}
          tableBrowserTables={tableBrowserTables}
          tableBrowserTable={tableBrowserTable}
          setTableBrowserTable={setTableBrowserTable}
          tableBrowserLoading={tableBrowserLoading}
          tableBrowserCols={tableBrowserCols}
        />
      </Drawer>
    </>
  );
};

export default TableBrowser;
