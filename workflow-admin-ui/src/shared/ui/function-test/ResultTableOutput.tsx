// shared/ui/function-test/ResultTableOutput.tsx
// 跨 features 通用：函数测试结果渲染（表格/JSON）

import React from 'react';
import { Alert, Collapse, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd';
import JsonEditor from '@/components/JsonEditor';
import { MAX_DISPLAY_ROWS } from '../../types/function-test-common';

export interface ResultTableOutputProps {
  output: any;
  isDbDomain?: boolean;
  maxDisplayRows?: number;
}

export const ResultTableOutput: React.FC<ResultTableOutputProps> = ({
  output, isDbDomain = false, maxDisplayRows = MAX_DISPLAY_ROWS,
}) => {
  if (!isDbDomain) {
    return <JsonEditor value={output} readOnly autoHeight minHeight={80} maxHeight={360} />;
  }
  if (output === null || output === undefined) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="（空输出）" style={{ padding: '16px 0' }} />;
  }
  if (typeof output !== 'object') {
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon={false}
          message={
            <Typography.Text strong>
              {typeof output === 'number' ? '返回数值（通常为行数/影响行数）：' : '返回值：'}
              {String(output)}
            </Typography.Text>
          }
          style={{ padding: '6px 12px' }}
        />
        <Collapse ghost size="small">
          <Collapse.Panel header="原始 JSON" key="raw">
            <JsonEditor value={output} readOnly autoHeight minHeight={60} maxHeight={240} />
          </Collapse.Panel>
        </Collapse>
      </Space>
    );
  }
  if (Array.isArray(output)) {
    if (output.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="返回 0 行" style={{ padding: '16px 0' }} />;
    }
    const allCols = Object.keys(output[0] || {});
    const columns = allCols.map((col) => ({
      title: <Typography.Text code style={{ fontSize: 12 }}>{col}</Typography.Text>,
      dataIndex: col, key: col,
      render: (val: any) => {
        if (val === null || val === undefined) return <Typography.Text type="secondary">{String(val)}</Typography.Text>;
        if (typeof val === 'string') {
          if (val.length > 120) return <Tooltip title={val}><span>{val.slice(0, 120)}…</span></Tooltip>;
          return <span>{val}</span>;
        }
        if (typeof val === 'object') {
          const s = JSON.stringify(val);
          return <Tooltip title={s}><Typography.Text code style={{ fontSize: 11 }}>{s.length > 120 ? s.slice(0, 120) + '…' : s}</Typography.Text></Tooltip>;
        }
        return <span>{String(val)}</span>;
      },
      ellipsis: true,
    }));
    const displayRows = output.length > maxDisplayRows ? output.slice(0, maxDisplayRows) : output;
    const overLimit = output.length > maxDisplayRows;
    return (
      <Space direction="vertical" size={6} style={{ width: '100%' }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <Tag color="blue">共 {output.length} 行</Tag>
          {overLimit && <Tag color="gold">仅显示前 {maxDisplayRows} 行</Tag>}
          {allCols.length > 15 && <Tag color="purple">{allCols.length} 列</Tag>}
        </div>
        <Table
          size="small" bordered columns={columns as any} dataSource={displayRows as any[]}
          pagination={false} rowKey={(_, idx) => String(idx)}
          scroll={{ x: true, y: Math.min(420, 80 + displayRows.length * 40) }}
        />
        <Collapse ghost size="small">
          <Collapse.Panel header="查看原始 JSON（完整结果）" key="raw">
            <JsonEditor value={output} readOnly autoHeight minHeight={60} maxHeight={360} />
          </Collapse.Panel>
        </Collapse>
      </Space>
    );
  }
  const entries = Object.entries(output as Record<string, any>);
  if (entries.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="（空对象）" style={{ padding: '16px 0' }} />;
  }
  return (
    <Space direction="vertical" size={6} style={{ width: '100%' }}>
      <Tag color="geekblue">{entries.length} 个字段</Tag>
      <Table
        size="small" bordered
        columns={[
          { title: '字段', dataIndex: 'k', key: 'k', width: '30%', render: (v: any) => <Typography.Text code>{v}</Typography.Text> },
          {
            title: '值', dataIndex: 'v', key: 'v',
            render: (v: any) => {
              if (v === null || v === undefined) return <Typography.Text type="secondary">{String(v)}</Typography.Text>;
              if (typeof v === 'string') {
                if (v.length > 200) return <Tooltip title={v}><span>{v.slice(0, 200)}…</span></Tooltip>;
                return <span>{v}</span>;
              }
              if (typeof v === 'object') {
                const s = JSON.stringify(v);
                return <Tooltip title={s}><Typography.Text code style={{ fontSize: 11 }}>{s.length > 200 ? s.slice(0, 200) + '…' : s}</Typography.Text></Tooltip>;
              }
              return <span>{String(v)}</span>;
            },
          },
        ]}
        dataSource={entries.map(([k, v], idx) => ({ k, v, __k: String(idx) }))}
        pagination={false} rowKey="__k"
        scroll={{ y: Math.min(420, 80 + entries.length * 40) }}
      />
      <Collapse ghost size="small">
        <Collapse.Panel header="查看原始 JSON" key="raw">
          <JsonEditor value={output} readOnly autoHeight minHeight={60} maxHeight={360} />
        </Collapse.Panel>
      </Collapse>
    </Space>
  );
};

export default ResultTableOutput;
