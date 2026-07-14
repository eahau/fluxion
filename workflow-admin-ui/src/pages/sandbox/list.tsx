import { history, useRequest } from '@umijs/max';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  ArrowLeftOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  DatabaseOutlined,
  WarningOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import type { ColumnsType } from 'antd/es/table';
import {
  acquireSandbox,
  releaseSandbox,
  releaseSandboxSession,
  type SandboxTicket,
  type SandboxAcquireRequest,
} from '@/services/sandboxes';
import { listApps, type App } from '@/services/apps';
import { listResources, type Resource } from '@/services/resources';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { Title, Text, Paragraph } = Typography;

interface SandboxRow {
  id: number;
  sessionId: string;
  appId: number;
  appKey?: string;
  resourceId: number;
  resourceName?: string;
  resourceType?: string;
  tablePrefix: string;
  clonedTables: number;
  status: string;
  expiresAt: number;
  createdAt: number;
}

const STATUS_META: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  CREATING: { label: '创建中', color: 'processing', icon: <ClockCircleOutlined /> },
  READY: { label: '就绪', color: 'success', icon: <CheckCircleOutlined /> },
  EXPIRED: { label: '已过期', color: 'warning', icon: <WarningOutlined /> },
  RELEASING: { label: '释放中', color: 'processing', icon: <ClockCircleOutlined /> },
  FAILED: { label: '失败', color: 'error', icon: <CloseCircleOutlined /> },
};

const TYPE_COLOR: Record<string, string> = {
  MYSQL: '#f59e0b',
  POSTGRESQL: '#336791',
  ORACLE: '#c1272d',
  SQLSERVER: '#dc3f24',
  H2: '#0ea5e9',
  CLICKHOUSE: '#ef4444',
  REDIS: '#dc382d',
  OTHER: '#8c8c8c',
};

const DEMO_ROWS: SandboxRow[] = [];

const SandboxList: React.FC = () => {
  const { message, modal } = AntdApp.useApp();
  const [rows, setRows] = useState<SandboxRow[]>(DEMO_ROWS);
  const [tickets, setTickets] = useState<SandboxTicket[]>([]);
  const [acquireOpen, setAcquireOpen] = useState(false);
  const [form] = Form.useForm<SandboxAcquireRequest>();
  const [submitting, setSubmitting] = useState(false);

  const { data: apps } = useRequest(() => listApps());
  const { data: dbResources } = useRequest(() => listResources());

  const dbResourceOptions = useMemo(() => {
    return ((dbResources ?? []) as Resource[]).filter((r) =>
      ['MYSQL', 'POSTGRESQL', 'ORACLE', 'SQLSERVER', 'H2', 'CLICKHOUSE'].includes(r.resourceType),
    );
  }, [dbResources]);

  const sessionCounts = useMemo(() => {
    const map = new Map<string, number>();
    rows.forEach((r) => map.set(r.sessionId, (map.get(r.sessionId) || 0) + 1));
    return map;
  }, [rows]);

  const stats = useMemo(() => {
    const ready = rows.filter((r) => r.status === 'READY').length;
    const totalTables = rows.reduce((acc, r) => acc + (r.clonedTables || 0), 0);
    return {
      sessions: tickets.length,
      instances: rows.length,
      ready,
      totalTables,
    };
  }, [rows, tickets]);

  const openAcquire = () => {
    if ((apps ?? []).length === 0) {
      Modal.warning({
        title: '暂无可调试的应用',
        content: '请先在「应用管理」中创建至少一个应用并绑定 DB 资源，才能申请沙盒调试。',
      });
      return;
    }
    form.resetFields();
    form.setFieldsValue({
      sessionId: `sb-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
    });
    setAcquireOpen(true);
  };

  const handleAcquire = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const ticket = await acquireSandbox({
        appId: values.appId,
        sessionId: values.sessionId,
        username: values.username,
      } as SandboxAcquireRequest);
      setTickets((prev) => [ticket, ...prev.filter((t) => t.sessionId !== ticket.sessionId)]);
      const newRows: SandboxRow[] = (ticket.instances ?? []).map((inst: any) => ({
        id: inst.id,
        sessionId: ticket.sessionId,
        appId: ticket.appId,
        appKey: (apps as App[] | undefined)?.find((a) => a.id === ticket.appId)?.appKey,
        resourceId: inst.resourceId,
        resourceName: (dbResources as Resource[] | undefined)?.find((r) => r.id === inst.resourceId)
          ?.resourceName,
        resourceType:
          (dbResources as Resource[] | undefined)?.find((r) => r.id === inst.resourceId)?.resourceType ||
          'MYSQL',
        tablePrefix: inst.tablePrefix,
        clonedTables: inst.clonedTables ?? 0,
        status: inst.status || 'READY',
        expiresAt: ticket.expiresAt,
        createdAt: ticket.createdAt,
      }));
      setRows((prev) => [...newRows, ...prev]);
      setAcquireOpen(false);
      message.success('沙盒会话已申请，克隆表操作正在后台进行');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReleaseInstance = useClickDebounce((row: SandboxRow) => {
    modal.confirm({
      title: '释放单个沙盒实例',
      content: `确定释放沙盒实例 ID=${row.id}（prefix=${row.tablePrefix}）吗？\n将 DROP 所有 sb_* 克隆表，数据不可恢复。`,
      okType: 'danger',
      onOk: async () => {
        await releaseSandbox(row.id);
        setRows((prev) => prev.filter((r) => r.id !== row.id));
        message.success('沙盒实例已释放');
      },
    });
  });

  const handleReleaseSession = useClickDebounce((sessionId: string) => {
    modal.confirm({
      title: '释放整个调试会话',
      content: `确定释放会话 session=${sessionId} 下的所有沙盒吗？\n共 ${sessionCounts.get(sessionId) ?? 0} 个实例。`,
      okType: 'danger',
      onOk: async () => {
        await releaseSandboxSession(sessionId);
        setRows((prev) => prev.filter((r) => r.sessionId !== sessionId));
        setTickets((prev) => prev.filter((t) => t.sessionId !== sessionId));
        message.success('调试会话已释放');
      },
    });
  });

  const columns: ColumnsType<SandboxRow> = [
    {
      title: '会话',
      dataIndex: 'sessionId',
      key: 'sessionId',
      width: 220,
      render: (s: string) => (
        <Space>
          <Tag color="purple" icon={<ExperimentOutlined />} style={{ margin: 0 }}>
            <Text copyable style={{ fontFamily: '"SF Mono", Monaco, monospace', fontSize: 12 }}>
              {s.length > 18 ? s.slice(0, 18) + '…' : s}
            </Text>
          </Tag>
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined />}
            onClick={() => {
              navigator.clipboard.writeText(s);
              message.success('sessionId 已复制');
            }}
          />
        </Space>
      ),
    },
    {
      title: '应用',
      key: 'app',
      width: 160,
      render: (_: any, r: SandboxRow) => (
        <Tag color="geekblue" style={{ margin: 0 }}>
          {r.appKey || `App#${r.appId}`}
        </Tag>
      ),
    },
    {
      title: '源资源',
      key: 'resource',
      width: 200,
      render: (_: any, r: SandboxRow) => (
        <Space size={6}>
          <Tag color={TYPE_COLOR[r.resourceType || 'OTHER'] || '#8c8c8c'} icon={<DatabaseOutlined />}>
            {r.resourceType}
          </Tag>
          <Text strong>{r.resourceName || `#${r.resourceId}`}</Text>
        </Space>
      ),
    },
    {
      title: '表前缀',
      dataIndex: 'tablePrefix',
      key: 'tablePrefix',
      width: 180,
      render: (p: string) => (
        <Text code copyable style={{ fontFamily: '"SF Mono", Monaco, monospace' }}>
          {p}
        </Text>
      ),
    },
    {
      title: '克隆表数',
      dataIndex: 'clonedTables',
      key: 'clonedTables',
      width: 100,
      render: (n: number) => (
        <Space>
          <DatabaseOutlined style={{ color: '#0891b2' }} />
          <span>{n ?? 0}</span>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: string) => {
        const m = STATUS_META[s] || { label: s, color: 'default', icon: null };
        return (
          <Tag color={m.color as any} icon={m.icon}>
            {m.label}
          </Tag>
        );
      },
    },
    {
      title: '到期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 170,
      render: (t: number) => (
        <Space size={4}>
          <ClockCircleOutlined style={{ color: '#8c8c8c' }} />
          <span style={{ fontSize: 12 }}>{t ? new Date(t).toLocaleString() : '-'}</span>
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'ops',
      width: 180,
      render: (_: any, r: SandboxRow) => (
        <Space>
          <Tooltip title="仅释放本实例">
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleReleaseInstance(r)}>
              释放
            </Button>
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space align="center" size={16}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => history.push('/')}>
              返回首页
            </Button>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 10,
                  background: 'linear-gradient(135deg, #8b5cf6, #ec4899)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <ExperimentOutlined style={{ fontSize: 18, color: '#fff' }} />
              </div>
              <div>
                <Title level={4} style={{ margin: 0 }}>
                  沙盒管理
                </Title>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  对指定 DB 资源做 sb_ 前缀克隆表的安全调试，不会污染真实数据
                </Text>
              </div>
            </div>
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openAcquire}>
            申请调试会话
          </Button>
        </div>

        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={12} md={6}>
            <Card bordered={false} style={{ borderRadius: 10, background: '#f5f3ff' }}>
              <Statistic
                title={<span style={{ color: '#6d28d9' }}>活跃会话</span>}
                value={stats.sessions}
                prefix={<ExperimentOutlined />}
                valueStyle={{ color: '#6d28d9' }}
              />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card bordered={false} style={{ borderRadius: 10, background: '#eff6ff' }}>
              <Statistic
                title={<span style={{ color: '#1d4ed8' }}>沙盒实例</span>}
                value={stats.instances}
                suffix="个"
                prefix={<DatabaseOutlined />}
                valueStyle={{ color: '#1d4ed8' }}
              />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card bordered={false} style={{ borderRadius: 10, background: '#f0fdf4' }}>
              <Statistic
                title={<span style={{ color: '#15803d' }}>就绪实例</span>}
                value={stats.ready}
                prefix={<CheckCircleOutlined />}
                valueStyle={{ color: '#15803d' }}
              />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card bordered={false} style={{ borderRadius: 10, background: '#ecfeff' }}>
              <Statistic
                title={<span style={{ color: '#155e75' }}>已克隆表</span>}
                value={stats.totalTables}
                suffix="张"
                prefix={<DatabaseOutlined />}
                valueStyle={{ color: '#155e75' }}
              />
            </Card>
          </Col>
        </Row>
      </Card>

      {tickets.length > 0 ? (
        <Card
          style={{ borderRadius: 12, marginBottom: 16 }}
          title={<span><ExperimentOutlined /> 最近会话</span>}
        >
          <List
            size="small"
            dataSource={tickets}
            renderItem={(t) => {
              const instCount = t.instances?.length ?? 0;
              return (
                <List.Item
                  actions={[
                    <Button
                      key="release"
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => handleReleaseSession(t.sessionId)}
                    >
                      释放会话
                    </Button>,
                  ]}
                >
                  <List.Item.Meta
                    avatar={<ExperimentOutlined style={{ color: '#8b5cf6', fontSize: 20 }} />}
                    title={
                      <Space>
                        <Text code>{t.sessionId}</Text>
                        <Tag>App #{t.appId}</Tag>
                        {t.ownerUsername ? <Tag color="blue">用户 {t.ownerUsername}</Tag> : null}
                      </Space>
                    }
                    description={
                      <Space size={16}>
                        <span>{instCount} 个沙盒实例</span>
                        <span>
                          到期：{t.expiresAt ? new Date(t.expiresAt).toLocaleString() : '-'}
                        </span>
                      </Space>
                    }
                  />
                </List.Item>
              );
            }}
          />
        </Card>
      ) : null}

      <Alert
        style={{ marginBottom: 16 }}
        type="info"
        showIcon
        message="沙盒使用说明"
        description={
          <div style={{ lineHeight: 1.75 }}>
            <ol style={{ margin: 0, paddingLeft: 20 }}>
              <li>先选择应用（应用下已绑定 DB 资源 → 列表会列出 READWRITE scope 的 DB 资源）</li>
              <li>点「申请调试会话」后，系统会对指定 DB 资源做 CREATE TABLE LIKE + INSERT 全量克隆，前缀为 <Text code>sb_&lt;hash&gt;_</Text></li>
              <li>设计器调试（debug）时将 routingContext 合并到请求体，builtin:dbExecute 自动路由到 sb_* 表</li>
              <li>调试结束后手动「释放会话」；到期后系统自动清理（默认 2 小时）</li>
            </ol>
          </div>
        }
      />

      <Card style={{ borderRadius: 12 }} bodyStyle={{ padding: 0 }}>
        <Table
          rowKey={(r) => `${r.sessionId}-${r.id}`}
          dataSource={rows}
          columns={columns as any}
          pagination={false}
          size="middle"
          locale={{ emptyText: rows.length === 0 ? (
            <Empty
              description={
                <div style={{ padding: 20 }}>
                  <Paragraph>暂无沙盒实例</Paragraph>
                  <Button type="primary" icon={<PlusOutlined />} onClick={openAcquire}>
                    申请第一个调试会话
                  </Button>
                </div>
              }
            />
          ) : '暂无数据' }}
        />
      </Card>

      <Modal
        title="申请调试沙盒会话"
        open={acquireOpen}
        onCancel={() => setAcquireOpen(false)}
        onOk={handleAcquire}
        confirmLoading={submitting}
        destroyOnHidden
        width={560}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            label="目标应用"
            name="appId"
            rules={[{ required: true, message: '请选择要调试的应用' }]}
            extra="沙盒会克隆该应用下所有 scope=READWRITE 的 DB 资源；也可以在 resourceIds 中单独指定"
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择应用"
              options={((apps ?? []) as App[]).map((a) => ({
                value: a.id,
                label: `${a.appKey} — ${a.appName}`,
              }))}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item
                label="会话 ID"
                name="sessionId"
                rules={[{ required: true, message: '请输入 sessionId' }]}
                extra="调试设计器 debug 面板的 session 就是这个值"
              >
                <Input style={{ fontFamily: '"SF Mono", Monaco, monospace' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="用户名（可选）" name="username">
                <Input placeholder="用于审计" />
              </Form.Item>
            </Col>
          </Row>
          <Alert
            type="warning"
            showIcon
            message="注意"
            description="大库克隆可能耗时较久，克隆完成前沙盒状态为 CREATING；克隆完成后会变成 READY 方可使用。"
          />
        </Form>
      </Modal>
    </div>
  );
};

export default SandboxList;
