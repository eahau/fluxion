// features/db-function-test/ui/TableBrowser.tsx
// DB Feature 专属 UI：表结构 + 字段预览面板

import React from 'react';
import { Collapse, Empty, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import type { DbTableColumn } from '../model/types';

export interface TableBrowserProps {
  selectedDatasource?: string;
  tableBrowserTable?: string;
  setTableBrowserTable: (value?: string) => void;
  tableBrowserTables: string[];
  tableBrowserLoading?: boolean;
  tableBrowserCols: DbTableColumn[];
}

export const TableBrowser: React.FC<TableBrowserProps> = ({
  selectedDatasource, tableBrowserTable, setTableBrowserTable,
  tableBrowserTables, tableBrowserLoading = false, tableBrowserCols,
}) => (
  <Collapse ghost size="small" style={{ marginTop: 2 }}>
    <Collapse.Panel
      header={
        <Space size={8}>
          <span>📋 表结构 & 字段预览</span>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            （{selectedDatasource || 'default'} 数据源，点击表名查看字段、类型、注释）
          </Typography.Text>
        </Space>
      }
      key="table_browser"
    >
      <Space direction="vertical" size={6} style={{ width: '100%' }}>
        <Select
          showSearch allowClear placeholder="选择要查看列结构的数据表"
          loading={tableBrowserLoading} value={tableBrowserTable}
          onChange={setTableBrowserTable} style={{ width: '100%' }}
          options={tableBrowserTables.map((t) => ({ value: t, label: t }))}
          filterOption={(input, option) =>
            String(option?.label || '').toLowerCase().includes(input.toLowerCase())
          }
          notFoundContent={tableBrowserLoading ? <Spin size="small" /> : '无数据表'}
          size="small"
        />
        {tableBrowserTable && (
          tableBrowserCols.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="（无字段）" style={{ padding: 8 }} />
          ) : (
            <>
              <Space size={8}>
                <Tag color="geekblue">{tableBrowserCols.length} 个字段</Tag>
              </Space>
              <Table
                size="small" bordered
                columns={[
                  {
                    title: '字段名', dataIndex: 'name', key: 'name', width: '30%',
                    render: (v) => <Typography.Text code style={{ fontSize: 12 }}>{v}</Typography.Text>,
                  },
                  {
                    title: '类型', dataIndex: 'dataType', key: 'dataType', width: '25%',
                    render: (v) => v ? <Typography.Text type="secondary">{v}</Typography.Text> : '-',
                  },
                  {
                    title: '注释', dataIndex: 'comment', key: 'comment',
                    render: (v) => v ? <span>{v}</span> : <Typography.Text type="secondary">-</Typography.Text>,
                  },
                ]}
                dataSource={tableBrowserCols.map((c, idx) => ({ ...c, __idx: String(idx) }))}
                pagination={false} rowKey="__idx"
                scroll={{ y: Math.min(260, 60 + tableBrowserCols.length * 36) }}
              />
            </>
          )
        )}
      </Space>
    </Collapse.Panel>
  </Collapse>
);

export default TableBrowser;
