import { history, useParams, useRequest } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  ArrowLeftOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  AppstoreOutlined,
  DatabaseOutlined,
  LinkOutlined,
  CheckCircleOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import {
  getApp,
  listAppBindings,
  createAppBinding,
  updateAppBinding,
  deleteAppBinding,
  listAppResourceOptions,
  type App,
  type AppBinding,
  type AppBindingCreateRequest,
  type AppBindingUpdateRequest,
} from '@/services/apps';
import { listResources, type Resource } from '@/services/resources';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { Title, Text } = Typography;

const STATUS_META: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  ACTIVE: { label: '正常', color: 'success', icon: <CheckCircleOutlined /> },
  DISABLED: { label: '禁用', color: 'default', icon: <StopOutlined /> },
};

const SCOPE_META: Record<string, { label: string; color: string }> = {
  READONLY: { label: '只读', color: 'blue' },
  READWRITE: { label: '读写', color: 'green' },
  ADMIN: { label: '管理', color: 'purple' },
};

const SCOPE_OPTIONS = [
  { value: 'READONLY', label: '只读（READONLY）' },
  { value: 'READWRITE', label: '读写（READWRITE）' },
  { value: 'ADMIN', label: '管理（ADMIN）' },
];

const TYPE_COLOR: Record<string, string> = {
  MYSQL: '#f59e0b',
  POSTGRESQL: '#336791',
  ORACLE: '#c1272d',
  SQLSERVER: '#dc3f24',
  H2: '#0ea5e9',
  CLICKHOUSE: '#ef4444',
  REDIS: '#dc382d',
  HTTP_ENDPOINT: '#6366f1',
  RABBITMQ: '#ff6600',
  KAFKA: '#231f20',
  OTHER: '#8c8c8c',
};

const AppDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const appId = Number(id);

  const [bindingModalOpen, setBindingModalOpen] = useState(false);
  const [editingBinding, setEditingBinding] = useState<AppBinding | null>(null);
  const [form] = Form.useForm<AppBindingCreateRequest & AppBindingUpdateRequest>();
  const [submitting, setSubmitting] = useState(false);

  const { data: app, loading: appLoading } = useRequest(() => getApp(appId), {
    ready: !!appId,
  });
  const { data: bindings, loading: bindingsLoading, refresh: refreshBindings } = useRequest(
    () => listAppBindings(appId),
    { ready: !!appId },
  );
  const { data: allResources } = useRequest(() => listResources(), {
    ready: !!appId,
  });

  const availableResources = useMemo(() => {
    const boundIds = new Set((bindings ?? []).map((b) => b.resourceId));
    return (allResources ?? []).filter((r) => !boundIds.has(r.id));
  }, [allResources, bindings]);

  const openCreateBinding = () => {
    setEditingBinding(null);
    form.resetFields();
    form.setFieldsValue({ scope: 'READWRITE' });
    setBindingModalOpen(true);
  };

  const openEditBinding = (b: AppBinding) => {
    setEditingBinding(b);
    form.setFieldsValue({
      resourceId: b.resourceId,
      aliasInApp: b.aliasInApp ?? undefined,
      scope: b.scope as any,
    });
    setBindingModalOpen(true);
  };

  const handleBindingSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editingBinding) {
        await updateAppBinding(appId, editingBinding.id, {
          aliasInApp: values.aliasInApp ?? null,
          scope: values.scope as any,
        });
        message.success('绑定已更新');
      } else {
        await createAppBinding(appId, {
          resourceId: values.resourceId!,
          aliasInApp: values.aliasInApp,
          scope: values.scope as any,
        });
        message.success('绑定已创建');
      }
      setBindingModalOpen(false);
      refreshBindings();
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteBinding = useClickDebounce((b: AppBinding) => {
    Modal.confirm({
      title: '确认解除绑定',
      content: `确定解除资源「${b.resourceName}（别名=${b.effectiveAlias}）」的绑定吗？`,
      okType: 'danger',
      onOk: async () => {
        await deleteAppBinding(appId, b.id);
        message.success('已解除绑定');
        refreshBindings();
      },
    });
  });

  const appSt = app ? STATUS_META[app.status] || STATUS_META.ACTIVE : null;

  const bindingColumns = [
    {
      title: '别名（函数参数用）',
      dataIndex: 'effectiveAlias',
      key: 'effectiveAlias',
      render: (_: any, r: AppBinding) => (
        <Space>
          <Tag color="cyan" icon={<LinkOutlined />} style={{ fontFamily: '"SF Mono", Monaco, monospace' }}>
            {r.effectiveAlias}
          </Tag>
          {r.aliasInApp && r.aliasInApp !== r.resourceName ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              自定义
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: '资源',
      key: 'resource',
      render: (_: any, r: AppBinding) => (
        <Space>
          <Tag color={TYPE_COLOR[r.resourceType] || '#8c8c8c'} icon={<DatabaseOutlined />}>
            {r.resourceType}
          </Tag>
          <Text strong>{r.resourceName}</Text>
          {r.driver ? <Text type="secondary" code>{r.driver}</Text> : null}
        </Space>
      ),
    },
    {
      title: '访问级别',
      dataIndex: 'scope',
      key: 'scope',
      render: (scope: string) => {
        const s = SCOPE_META[scope] || { label: scope, color: 'default' };
        return <Tag color={s.color as any}>{s.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'ops',
      width: 160,
      render: (_: any, r: AppBinding) => (
        <Space>
          <Tooltip title="编辑别名 / 权限">
            <Button size="small" icon={<EditOutlined />} onClick={() => openEditBinding(r)}>
              编辑
            </Button>
          </Tooltip>
          <Tooltip title="解除绑定">
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDeleteBinding(r)}
            >
              解绑
            </Button>
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1280, margin: '0 auto' }}>
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space align="center" size={16}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => history.push('/app')}>
              返回列表
            </Button>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div
                style={{
                  width: 48,
                  height: 48,
                  borderRadius: 12,
                  background: 'linear-gradient(135deg, #6366f1, #22d3ee)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <AppstoreOutlined style={{ fontSize: 22, color: '#fff' }} />
              </div>
              <div>
                <Space align="center" size={12}>
                  <Title level={4} style={{ margin: 0 }}>
                    {app ? app.appName : '加载中…'}
                  </Title>
                  {appSt ? (
                    <Tag color={appSt.color as any} icon={appSt.icon}>
                      {appSt.label}
                    </Tag>
                  ) : null}
                </Space>
                {app ? (
                  <Tag color="geekblue" style={{ marginTop: 4 }}>
                    <LinkOutlined /> {app.appKey}
                  </Tag>
                ) : null}
              </div>
            </div>
          </Space>
        </div>
      </Card>

      <Row gutter={[16, 16]}>
        <Col span={24} lg={10}>
          <Card title={<span><AppstoreOutlined /> 基本信息</span>} style={{ borderRadius: 12 }} loading={appLoading}>
            {app ? (
              <Descriptions bordered column={1} size="small" labelStyle={{ width: 100, background: '#f8fafc' }}>
                <Descriptions.Item label="ID">{app.id}</Descriptions.Item>
                <Descriptions.Item label="App Key">
                  <Text code copyable>{app.appKey}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="名称">{app.appName}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={appSt?.color as any} icon={appSt?.icon}>{appSt?.label}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="负责人">{app.owner || '-'}</Descriptions.Item>
                <Descriptions.Item label="描述">
                  <div style={{ maxWidth: 360, whiteSpace: 'pre-wrap' }}>{app.description || '-'}</div>
                </Descriptions.Item>
                <Descriptions.Item label="创建时间">
                  {app.createdAt ? new Date(app.createdAt).toLocaleString() : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="更新时间">
                  {app.updatedAt ? new Date(app.updatedAt).toLocaleString() : '-'}
                </Descriptions.Item>
              </Descriptions>
            ) : null}
          </Card>
        </Col>
        <Col span={24} lg={14}>
          <Card
            title={
              <Space>
                <DatabaseOutlined />
                <span>已绑定资源（{(bindings ?? []).length}）</span>
              </Space>
            }
            style={{ borderRadius: 12 }}
            loading={bindingsLoading}
            extra={
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreateBinding}>
                绑定资源
              </Button>
            }
          >
            <Table
              rowKey="id"
              size="small"
              dataSource={bindings as AppBinding[]}
              columns={bindingColumns as any}
              pagination={false}
              locale={{ emptyText: '暂无绑定，点击右上角「绑定资源」添加' }}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title={editingBinding ? `编辑资源绑定：${editingBinding.effectiveAlias}` : '为应用绑定全局资源'}
        open={bindingModalOpen}
        onCancel={() => setBindingModalOpen(false)}
        onOk={handleBindingSubmit}
        confirmLoading={submitting}
        destroyOnHidden
        width={600}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            label="全局资源"
            name="resourceId"
            rules={[{ required: !editingBinding, message: '请选择要绑定的资源' }]}
            extra="已绑定的资源不会出现在下拉中（一个资源只能绑定一个别名）"
          >
            <Select
              disabled={!!editingBinding}
              showSearch
              optionFilterProp="label"
              placeholder="选择资源"
              options={(availableResources as Resource[]).map((r) => ({
                value: r.id,
                label: `[${r.resourceType}] ${r.resourceName}`,
              }))}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={14}>
              <Form.Item
                label="别名 aliasInApp"
                name="aliasInApp"
                extra="设计器函数参数下拉使用此别名；为空时默认使用资源名"
              >
                <Input
                  placeholder="留空默认 = resourceName"
                  style={{ fontFamily: '"SF Mono", Monaco, monospace' }}
                />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item
                label="访问级别 scope"
                name="scope"
                rules={[{ required: true, message: '请选择访问级别' }]}
              >
                <Select options={SCOPE_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

export default AppDetail;
