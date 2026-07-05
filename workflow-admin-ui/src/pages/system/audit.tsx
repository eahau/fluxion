import React, { useState, useCallback } from 'react';
import { Card, Table, Input, Select, DatePicker, Space, Tag, Button } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { getAuditLogs, type AuditLog } from '@/services/audit';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { RangePicker } = DatePicker;

const ACTION_COLORS: Record<string, string> = {
  CREATE: 'green',
  UPDATE: 'blue',
  DELETE: 'red',
  PUBLISH: 'purple',
  LOGIN: 'cyan',
  EXECUTE: 'orange',
};

const AuditPage: React.FC = () => {
  const [filters, setFilters] = useState<{
    operator?: string;
    action?: string;
    resourceType?: string;
    startTime?: string;
    endTime?: string;
  }>({});
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20 });

  const { data, loading, run } = useRequest(
    async () => {
      const res = await getAuditLogs({
        ...filters,
        page: pagination.current - 1,
        pageSize: pagination.pageSize,
      });
      return { list: res?.list ?? [], total: res?.total ?? 0 };
    },
    {
      refreshDeps: [filters, pagination],
    },
  );

  const handleSearch = useClickDebounce(useCallback(() => {
    run();
  }, [run]));

  const handleReset = useCallback(() => {
    setFilters({});
    setPagination({ current: 1, pageSize: 20 });
  }, []);

  const handleTableChange = useCallback((pag: TablePaginationConfig) => {
    setPagination({ current: pag.current ?? 1, pageSize: pag.pageSize ?? 20 });
  }, []);

  const columns: ColumnsType<AuditLog> = [
    { title: '操作人', dataIndex: 'operator', width: 120 },
    {
      title: '操作类型',
      dataIndex: 'action',
      width: 110,
      render: (v: string) => <Tag color={ACTION_COLORS[v] ?? 'default'}>{v}</Tag>,
    },
    { title: '资源类型', dataIndex: 'resourceType', width: 120 },
    { title: '资源 ID', dataIndex: 'resourceId', ellipsis: true },
    { title: '详情', dataIndex: 'detail', ellipsis: true },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    {
      title: '时间',
      dataIndex: 'timestamp',
      width: 170,
      render: (v: string) => v ? new Date(v).toLocaleString() : '-',
      sorter: (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
      defaultSortOrder: 'descend',
    },
  ];

  return (
    <Card
      title="审计日志"
      extra={
        <Space>
          <Input
            placeholder="操作人"
            value={filters.operator}
            onChange={(e) => setFilters((f) => ({ ...f, operator: e.target.value || undefined }))}
            style={{ width: 120 }}
            allowClear
          />
          <Select
            placeholder="操作类型"
            value={filters.action}
            onChange={(v) => setFilters((f) => ({ ...f, action: v }))}
            allowClear
            style={{ width: 120 }}
            options={[
              { label: 'CREATE', value: 'CREATE' },
              { label: 'UPDATE', value: 'UPDATE' },
              { label: 'DELETE', value: 'DELETE' },
              { label: 'PUBLISH', value: 'PUBLISH' },
              { label: 'EXECUTE', value: 'EXECUTE' },
              { label: 'LOGIN', value: 'LOGIN' },
            ]}
          />
          <Select
            placeholder="资源类型"
            value={filters.resourceType}
            onChange={(v) => setFilters((f) => ({ ...f, resourceType: v }))}
            allowClear
            style={{ width: 130 }}
            options={[
              { label: 'Schema', value: 'SCHEMA' },
              { label: 'Workflow', value: 'WORKFLOW' },
              { label: 'Function', value: 'FUNCTION' },
              { label: 'User', value: 'USER' },
              { label: 'Role', value: 'ROLE' },
            ]}
          />
          <RangePicker
            showTime
            onChange={(_, dateStrings) => {
              setFilters((f) => ({
                ...f,
                startTime: dateStrings[0] || undefined,
                endTime: dateStrings[1] || undefined,
              }));
            }}
          />
          <Button icon={<SearchOutlined />} type="primary" onClick={handleSearch}>
            搜索
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleReset}>
            重置
          </Button>
        </Space>
      }
    >
      <Table<AuditLog>
        rowKey="id"
        dataSource={data?.list ?? []}
        columns={columns}
        loading={loading}
        onChange={handleTableChange}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
        scroll={{ x: 900 }}
        size="small"
      />
    </Card>
  );
};

export default AuditPage;
