import { history, useAccess, useRequest } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Empty,
  Modal,
  Pagination,
  Row,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Input,
  message,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  DatabaseOutlined,
  CloudServerOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import {
  listResources,
  deleteResource,
  type Resource,
} from '@/services/resources';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { Text, Title } = Typography;

const RESOURCE_TYPES = [
  'MYSQL', 'POSTGRESQL', 'ORACLE', 'SQLSERVER', 'H2', 'CLICKHOUSE',
  'REDIS', 'HTTP_ENDPOINT', 'RABBITMQ', 'KAFKA', 'OTHER',
] as const;

const TYPE_META: Record<string, { color: string; icon: React.ReactNode; label: string }> = {
  MYSQL: { color: '#f59e0b', icon: <DatabaseOutlined />, label: 'MySQL' },
  POSTGRESQL: { color: '#336791', icon: <DatabaseOutlined />, label: 'PostgreSQL' },
  ORACLE: { color: '#c1272d', icon: <DatabaseOutlined />, label: 'Oracle' },
  SQLSERVER: { color: '#dc3f24', icon: <DatabaseOutlined />, label: 'SQL Server' },
  H2: { color: '#0ea5e9', icon: <DatabaseOutlined />, label: 'H2' },
  CLICKHOUSE: { color: '#ef4444', icon: <DatabaseOutlined />, label: 'ClickHouse' },
  REDIS: { color: '#dc382d', icon: <CloudServerOutlined />, label: 'Redis' },
  HTTP_ENDPOINT: { color: '#6366f1', icon: <CloudServerOutlined />, label: 'HTTP Endpoint' },
  RABBITMQ: { color: '#ff6600', icon: <CloudServerOutlined />, label: 'RabbitMQ' },
  KAFKA: { color: '#231f20', icon: <CloudServerOutlined />, label: 'Kafka' },
  OTHER: { color: '#8c8c8c', icon: <DatabaseOutlined />, label: '其它' },
};

function renderConfigPreview(config: Record<string, unknown> | null | undefined): React.ReactNode {
  if (!config || Object.keys(config).length === 0) {
    return <Tag>未配置</Tag>;
  }
  const tokens: string[] = [];
  const keys = ['host', 'port', 'database', 'baseUrl', 'dbIndex', 'username'];
  keys.forEach((k) => {
    if (config[k] !== undefined && config[k] !== null && config[k] !== '<REDACTED>') {
      tokens.push(`${k}=${config[k]}`);
    }
  });
  const redacted = Object.values(config).some((v) => v === '<REDACTED>');
  return (
    <Space size={4} wrap>
      {tokens.map((t) => (
        <Tag key={t} color="geekblue" style={{ margin: 0, fontSize: 11, fontFamily: '"SF Mono", Monaco, monospace' }}>
          {t}
        </Tag>
      ))}
      {redacted ? <Tag color="orange" style={{ margin: 0, fontSize: 11 }}>含敏感字段（已脱敏）</Tag> : null}
      {tokens.length === 0 && !redacted ? <Tag>已配置</Tag> : null}
    </Space>
  );
}

const ResourceList: React.FC = () => {
  const access = useAccess();
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [viewMode, setViewMode] = useState<string>('table');
  const [pageIndex, setPageIndex] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  useEffect(() => {
    setPageIndex(1);
  }, [keyword, typeFilter]);

  const { data, loading, refresh } = useRequest(
    () => listResources(typeFilter === 'all' ? undefined : { resourceType: typeFilter }),
    {
      formatResult: (res) => res,
      refreshDeps: [typeFilter],
    },
  );

  const allResources = useMemo(() => (data ?? []) as Resource[], [data]);

  const filtered = useMemo(() => {
    if (!keyword) return allResources;
    const k = keyword.trim().toLowerCase();
    return allResources.filter((r) => {
      if (r.resourceName.toLowerCase().includes(k)) return true;
      if (r.resourceType.toLowerCase().includes(k)) return true;
      if ((r.driver ?? '').toLowerCase().includes(k)) return true;
      const cfg = r.configJson as Record<string, unknown> | null | undefined;
      if (cfg) {
        const v = Object.values(cfg).filter((x) => typeof x === 'string' && x !== '<REDACTED>').join(' ');
        if (v.toLowerCase().includes(k)) return true;
      }
      return false;
    });
  }, [allResources, keyword]);

  const total = filtered.length;
  const pageList = filtered.slice((pageIndex - 1) * pageSize, pageIndex * pageSize);

  const typeCounts = useMemo(() => {
    const counts: Record<string, number> = { all: allResources.length };
    allResources.forEach((r) => {
      counts[r.resourceType] = (counts[r.resourceType] || 0) + 1;
    });
    return counts;
  }, [allResources]);

  const handleDelete = useClickDebounce((r: Resource) => {
    Modal.confirm({
      title: '确认删除资源',
      content: `确定删除资源「${r.resourceName}（${r.resourceType}）」吗？\n如果仍有应用绑定此资源会删除失败（需先解绑）。`,
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteResource(r.id);
          message.success('删除成功');
          refresh();
        } catch (e: any) {
          if (e?.response?.data?.code === 409 || String(e.message).includes('409') || String(e.message).includes('引用')) {
            Modal.error({
              title: '删除失败',
              content: '该资源仍有应用绑定引用，请先解除应用绑定后再删除。',
            });
          }
        }
      },
    });
  });

  const tableColumns = [
    {
      title: '类型',
      dataIndex: 'resourceType',
      key: 'resourceType',
      width: 160,
      render: (t: string) => {
        const m = TYPE_META[t] || TYPE_META.OTHER;
        return (
          <Tag color={m.color} icon={m.icon} style={{ margin: 0 }}>
            {m.label}
          </Tag>
        );
      },
    },
    {
      title: '资源名',
      dataIndex: 'resourceName',
      key: 'resourceName',
      render: (n: string, r: Resource) => (
        <Space>
          <Text strong>{n}</Text>
          {r.driver ? <Text type="secondary" code style={{ fontSize: 12 }}>{r.driver}</Text> : null}
        </Space>
      ),
    },
    {
      title: '配置预览',
      key: 'configPreview',
      render: (_: any, r: Resource) => renderConfigPreview(r.configJson as any),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      render: (t: number) => (
        <Space size={4}>
          <ClockCircleOutlined style={{ color: '#8c8c8c' }} />
          <span style={{ fontSize: 12, color: '#595959' }}>
            {t ? new Date(t).toLocaleString() : '-'}
          </span>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'ops',
      width: 200,
      render: (_: any, r: Resource) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => history.push(`/resource/${r.id}`)}>
            编辑
          </Button>
          <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(r)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                background: 'linear-gradient(135deg, #f59e0b, #ef4444)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <DatabaseOutlined style={{ fontSize: 18, color: '#fff' }} />
            </div>
            <div>
              <Title level={4} style={{ margin: 0 }}>
                资源中心
              </Title>
              <Text type="secondary" style={{ fontSize: 12 }}>
                全局可复用的 DB / Redis / HTTP 等资源注册表，敏感字段服务端加密
              </Text>
            </div>
          </div>
          <Space>
            <Segmented value={viewMode} onChange={(v) => setViewMode(String(v))} options={[
              { label: '表格', value: 'table' },
              { label: '卡片', value: 'card' },
            ]} />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/resource/new')}>
              新建资源
            </Button>
          </Space>
        </div>
        <div style={{ marginTop: 16, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Input.Search
            placeholder="搜索资源名 / 类型 / 配置值"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            allowClear
            style={{ width: 360 }}
          />
          <Select
            value={typeFilter}
            onChange={setTypeFilter}
            style={{ width: 220 }}
            options={[
              { value: 'all', label: `全部类型 (${typeCounts.all ?? 0})` },
              ...RESOURCE_TYPES.map((t) => ({
                value: t,
                label: `${TYPE_META[t]?.label ?? t} (${typeCounts[t] ?? 0})`,
              })),
            ]}
          />
        </div>
      </Card>

      {viewMode === 'table' ? (
        <Card style={{ borderRadius: 12 }} bodyStyle={{ padding: 0 }}>
          <Table
            rowKey="id"
            loading={loading}
            dataSource={pageList}
            columns={tableColumns as any}
            pagination={false}
            size="middle"
            locale={{ emptyText: '暂无资源，点击右上角「新建资源」' }}
          />
        </Card>
      ) : loading && pageList.length === 0 ? (
        <Card style={{ borderRadius: 12 }}>
          <Empty description="加载中…" />
        </Card>
      ) : pageList.length === 0 ? (
        <Card style={{ borderRadius: 12 }}>
          <Empty description="暂无资源，点击右上角「新建资源」开始创建" />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {pageList.map((r) => {
            const m = TYPE_META[r.resourceType] || TYPE_META.OTHER;
            return (
              <Col span={24} md={12} xl={8} key={r.id}>
                <Card
                  style={{ borderRadius: 12, height: '100%' }}
                  hoverable
                  actions={[
                    <Tooltip title="编辑">
                      <EditOutlined key="edit" onClick={() => history.push(`/resource/${r.id}`)} />
                    </Tooltip>,
                    <Tooltip title="删除">
                      <DeleteOutlined
                        key="del"
                        style={{ color: '#ff4d4f' }}
                        onClick={() => handleDelete(r)}
                      />
                    </Tooltip>,
                  ]}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                    <Space align="start" size={12}>
                      <div
                        style={{
                          width: 36,
                          height: 36,
                          borderRadius: 10,
                          background: `${m.color}1a`,
                          color: m.color,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 16,
                        }}
                      >
                        {m.icon}
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <Text strong style={{ fontSize: 15 }}>
                            {r.resourceName}
                          </Text>
                          <Tag color={m.color} style={{ margin: 0 }}>
                            {m.label}
                          </Tag>
                        </div>
                        {r.driver ? (
                          <Text type="secondary" style={{ fontSize: 12, fontFamily: '"SF Mono", Monaco, monospace' }}>
                            {r.driver}
                          </Text>
                        ) : null}
                      </div>
                    </Space>
                  </div>
                  <div style={{ marginTop: 12, minHeight: 32 }}>
                    {renderConfigPreview(r.configJson as any)}
                  </div>
                  <div
                    style={{
                      marginTop: 12,
                      paddingTop: 8,
                      borderTop: '1px dashed #f0f0f0',
                      fontSize: 12,
                      color: '#8c8c8c',
                      display: 'flex',
                      justifyContent: 'space-between',
                    }}
                  >
                    <span>ID: {r.id}</span>
                    <span>更新：{r.updatedAt ? new Date(r.updatedAt).toLocaleDateString() : '-'}</span>
                  </div>
                </Card>
              </Col>
            );
          })}
        </Row>
      )}

      {total > pageSize ? (
        <div style={{ marginTop: 24, textAlign: 'right' }}>
          <Pagination
            current={pageIndex}
            pageSize={pageSize}
            total={total}
            showSizeChanger
            onChange={(p, s) => {
              setPageIndex(p);
              setPageSize(s);
            }}
          />
        </div>
      ) : null}
    </div>
  );
};

export default ResourceList;
