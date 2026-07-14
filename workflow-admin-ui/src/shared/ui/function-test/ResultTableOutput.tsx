// shared/ui/function-test/ResultTableOutput.tsx
// 跨 features 通用：函数测试结果渲染（表格/JSON + CSV/JSON 导出）

import React, { useMemo } from 'react';
import { Alert, Button, Collapse, Empty, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { DownloadOutlined, FileTextOutlined, CodeOutlined } from '@ant-design/icons';
import JsonEditor from '@/components/JsonEditor';
import { MAX_DISPLAY_ROWS } from '../../types/function-test-common';

export interface ResultTableOutputProps {
  output: any;
  isDbDomain?: boolean;
  maxDisplayRows?: number;
}

/** 将任意 JS 值安全转换为可导出的字符串（处理 undefined / BigInt / 循环引用） */
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

/** 表格数据 → CSV 字符串（含 BOM，Excel 打开不乱码；字段含逗号/换行时用双引号包裹） */
function toCSV(rows: Record<string, any>[], columns: string[]): string {
  const escapeCell = (v: any): string => {
    if (v === null || v === undefined) return '';
    let s = typeof v === 'object' ? safeJsonStringify(v) : String(v);
    if (/[",\r\n]/.test(s)) s = `"${s.replace(/"/g, '""')}"`;
    return s;
  };
  const header = columns.map(escapeCell).join(',');
  const body = rows
    .map((r) => columns.map((c) => escapeCell(r[c])).join(','))
    .join('\r\n');
  return '\uFEFF' + header + '\r\n' + body + '\r\n';
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

const ExportBar: React.FC<{
  rows?: Record<string, any>[];
  columns?: string[];
  jsonSource?: any;
  defaultName?: string;
}> = ({ rows, columns, jsonSource, defaultName = 'db-result' }) => {
  const fileName = useMemo(() => `${defaultName}_${tsFileStamp()}`, [defaultName]);

  const handleExportCsv = () => {
    if (!rows || !columns || rows.length === 0) {
      message.warning('无可导出的表格数据');
      return;
    }
    try {
      triggerDownload(`${fileName}.csv`, toCSV(rows, columns), 'text/csv;charset=utf-8');
      message.success(`已导出 CSV（${rows.length} 行 / ${columns.length} 列）`);
    } catch (e: any) {
      message.error('CSV 导出失败：' + (e?.message || '未知错误'));
    }
  };

  const handleExportJson = () => {
    const src = jsonSource === undefined ? rows : jsonSource;
    if (src === undefined || src === null) {
      message.warning('无可导出的数据');
      return;
    }
    try {
      triggerDownload(`${fileName}.json`, safeJsonStringify(src), 'application/json;charset=utf-8');
      message.success('已导出 JSON');
    } catch (e: any) {
      message.error('JSON 导出失败：' + (e?.message || '未知错误'));
    }
  };

  return (
    <Space size={4} wrap>
      <Button
        type="text"
        size="small"
        icon={<DownloadOutlined />}
        onClick={handleExportCsv}
        disabled={!rows || !columns || rows.length === 0}
      >
        <FileTextOutlined style={{ marginRight: 2 }} />
        导出 CSV
      </Button>
      <Button
        type="text"
        size="small"
        icon={<DownloadOutlined />}
        onClick={handleExportJson}
        disabled={jsonSource === undefined && (!rows || rows.length === 0)}
      >
        <CodeOutlined style={{ marginRight: 2 }} />
        导出 JSON
      </Button>
    </Space>
  );
};

export const ResultTableOutput: React.FC<ResultTableOutputProps> = ({
  output, isDbDomain = false, maxDisplayRows = MAX_DISPLAY_ROWS,
}) => {
  if (!isDbDomain) {
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <ExportBar jsonSource={output} defaultName="fn-result" />
        </div>
        <JsonEditor value={output} readOnly autoHeight minHeight={80} maxHeight={360} />
      </Space>
    );
  }
  if (output === null || output === undefined) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="（空输出）" style={{ padding: '16px 0' }} />;
  }
  if (typeof output !== 'object') {
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <ExportBar jsonSource={output} defaultName="fn-scalar" />
        </div>
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
      return (
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <ExportBar jsonSource={output} defaultName="db-empty" />
          </div>
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="返回 0 行" style={{ padding: '16px 0' }} />
        </Space>
      );
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
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <Tag color="blue">共 {output.length} 行</Tag>
            {overLimit && <Tag color="gold">仅显示前 {maxDisplayRows} 行</Tag>}
            {allCols.length > 15 && <Tag color="purple">{allCols.length} 列</Tag>}
          </div>
          <ExportBar
            rows={output as Record<string, any>[]}
            columns={allCols}
            jsonSource={output}
            defaultName="db-rows"
          />
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
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <ExportBar jsonSource={output} defaultName="db-empty-object" />
        </div>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="（空对象）" style={{ padding: '16px 0' }} />
      </Space>
    );
  }
  return (
    <Space direction="vertical" size={6} style={{ width: '100%' }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'space-between' }}>
        <Tag color="geekblue">{entries.length} 个字段</Tag>
        <ExportBar jsonSource={output} defaultName="db-object" />
      </div>
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
