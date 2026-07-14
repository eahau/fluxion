import { history, useParams, useRequest } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Col,
  Divider,
  Empty,
  Form,
  Input,
  message,
  Modal,
  Row,
  Select,
  Space,
  Tab,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  DatabaseOutlined,
  CloudServerOutlined,
  InfoCircleOutlined,
  CloudUploadOutlined,
  CheckCircleOutlined,
  PlayCircleOutlined,
  AppstoreOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import JsonEditor from '@/components/JsonEditor';
import {
  listResources,
  createResource,
  updateResource,
  getResource,
  testResource,
  deleteResource,
  type Resource,
  type ResourceCreateRequest,
  type ResourceUpdateRequest,
} from '@/services/resources';
import {
  listApps,
  listAppBindings,
  createAppBinding,
  deleteAppBinding,
  type App as AppInfo,
  type AppBinding,
} from '@/services/apps';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { Title, Text } = Typography;

const RESOURCE_TYPES = [
  { value: 'MYSQL', label: 'MySQL' },
  { value: 'POSTGRESQL', label: 'PostgreSQL' },
  { value: 'ORACLE', label: 'Oracle' },
  { value: 'SQLSERVER', label: 'SQL Server' },
  { value: 'H2', label: 'H2' },
  { value: 'CLICKHOUSE', label: 'ClickHouse' },
  { value: 'REDIS', label: 'Redis' },
  { value: 'HTTP_ENDPOINT', label: 'HTTP Endpoint' },
  { value: 'RABBITMQ', label: 'RabbitMQ' },
  { value: 'KAFKA', label: 'Kafka' },
  { value: 'OTHER', label: '其它 (OTHER)' },
];

const TYPE_ICON: Record<string, React.ReactNode> = {
  MYSQL: <DatabaseOutlined />,
  POSTGRESQL: <DatabaseOutlined />,
  ORACLE: <DatabaseOutlined />,
  SQLSERVER: <DatabaseOutlined />,
  H2: <DatabaseOutlined />,
  CLICKHOUSE: <DatabaseOutlined />,
  REDIS: <CloudServerOutlined />,
  HTTP_ENDPOINT: <CloudServerOutlined />,
  RABBITMQ: <CloudServerOutlined />,
  KAFKA: <CloudServerOutlined />,
  OTHER: <DatabaseOutlined />,
};

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

interface ConfigTemplate {
  hint: string;
  template: Record<string, unknown>;
}

const CONFIG_TEMPLATES: Record<string, ConfigTemplate> = {
  MYSQL: {
    hint: 'host/port/database/username/password 是必配项；driverClassname 可为 com.mysql.cj.jdbc.Driver',
    template: {
      host: 'localhost',
      port: 3306,
      database: 'mydb',
      username: 'root',
      password: '<change-me>',
      useSSL: false,
      driverClassname: 'com.mysql.cj.jdbc.Driver',
    },
  },
  POSTGRESQL: {
    hint: 'PostgreSQL 典型参数',
    template: {
      host: 'localhost',
      port: 5432,
      database: 'postgres',
      username: 'postgres',
      password: '<change-me>',
      schema: 'public',
      useSSL: false,
    },
  },
  ORACLE: {
    hint: 'Oracle Thin 配置',
    template: {
      host: 'localhost',
      port: 1521,
      serviceName: 'ORCL',
      username: 'system',
      password: '<change-me>',
    },
  },
  SQLSERVER: {
    hint: 'SQL Server 典型配置',
    template: {
      host: 'localhost',
      port: 1433,
      database: 'master',
      username: 'sa',
      password: '<change-me>',
      encrypt: false,
    },
  },
  H2: {
    hint: 'H2 内存 / 嵌入式 / TCP 模式',
    template: {
      jdbcUrl: 'jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1',
      username: 'sa',
      password: '',
    },
  },
  CLICKHOUSE: {
    hint: 'ClickHouse 配置',
    template: {
      host: 'localhost',
      port: 8123,
      database: 'default',
      username: 'default',
      password: '',
    },
  },
  REDIS: {
    hint: '单实例 / Sentinel / Cluster 任选其一；必填 host/port/dbIndex 或 sentinel 配置',
    template: {
      host: 'localhost',
      port: 6379,
      dbIndex: 0,
      password: null,
      timeoutMs: 3000,
    },
  },
  HTTP_ENDPOINT: {
    hint: 'HTTP 外部服务入口，builtin:http 可引用此资源',
    template: {
      baseUrl: 'https://api.example.com',
      defaultHeaders: {
        Accept: 'application/json',
      },
      timeoutMs: 5000,
      auth: {
        type: 'none',
      },
    },
  },
  RABBITMQ: {
    hint: 'RabbitMQ Broker',
    template: {
      host: 'localhost',
      port: 5672,
      virtualHost: '/',
      username: 'guest',
      password: 'guest',
    },
  },
  KAFKA: {
    hint: 'Kafka Producer/Consumer 通用配置',
    template: {
      bootstrapServers: 'localhost:9092',
      clientId: 'fluxion-worker',
      acks: 'all',
    },
  },
  OTHER: {
    hint: '自定义资源；configJson 结构完全自定义，由函数自行解释',
    template: {
      note: '自定义资源，自由填写',
    },
  },
};

const ResourceEditor: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isNew = id === 'new' || !id;
  const resourceId = isNew ? null : Number(id);
  const [form] = Form.useForm<ResourceCreateRequest & ResourceUpdateRequest>();
  const [saving, setSaving] = useState(false);
  const [config, setConfig] = useState<Record<string, unknown> | null>(null);
  const [configError, setConfigError] = useState<string | null>(null);
  const [testLoading, setTestLoading] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string; latencyMs?: number } | null>(null);
  const [cloudProvider, setCloudProvider] = useState<string>('aliyun');

  const resourceType = Form.useWatch('resourceType', form) as string | undefined;

  const { data: appsList = [], loading: appsLoading } = useRequest(() => listApps(), { ready: true });
  const {
    data: currentBindings = [],
    loading: bindingsLoading,
    refresh: refreshBindings,
  } = useRequest(() => listAppBindings(0), {
    ready: false,
  });
  const {
    data: currentBindingsForEdit = [],
    loading: bindingsLoadingForEdit,
    refresh: refreshBindingsForEdit,
  } = useRequest(
    () => {
      if (!resourceId) return Promise.resolve([] as AppBinding[]);
      return listAppBindings(0).catch(() => [] as AppBinding[]);
    },
    { ready: false },
  );
  const fetchResourceBindings = async (resourceIdFor: number): Promise<AppBinding[]> => {
    try {
      const allApps = appsList as AppInfo[];
      const collected: AppBinding[] = [];
      for (const app of allApps) {
        try {
          const binds = await listAppBindings(app.id);
          const matched = binds.filter(
            (b) => String(b.resourceId) === String(resourceIdFor),
          );
          if (matched.length) collected.push(...matched);
        } catch {
          /* ignore per-app errors */
        }
      }
      return collected;
    } catch {
      return [];
    }
  };
  const { data: directBindings = [], refresh: refreshDirectBindings } = useRequest(
    () => (resourceId ? fetchResourceBindings(resourceId) : Promise.resolve([] as AppBinding[])),
    { ready: !!resourceId, refreshDeps: [resourceId] },
  );

  const [bindToAppIds, setBindToAppIds] = useState<number[]>([]);
  const [appBindingsBusy, setAppBindingsBusy] = useState(false);

  const appOptions = useMemo(() => {
    const bound = new Set((directBindings ?? []).map((b) => b.appId));
    return (appsList as AppInfo[])
      .filter((a) => {
        if (a == null) return false;
        const rawStatus = (a as any).status;
        if (rawStatus == null) return true;
        const s = String(rawStatus).toUpperCase();
        if (s === 'DISABLED' || s === 'INACTIVE' || s === '0') return false;
        return true;
      })
      .map((a) => ({
        value: a.id,
        label: `${a.appName} — ${a.appKey}${a.owner ? `（负责人：${a.owner}）` : ''}${bound.has(a.id) ? '  ✅已绑定' : ''}`,
        disabled: bound.has(a.id),
      }));
  }, [appsList, directBindings]);

  const { data: resource, loading } = useRequest(() => getResource(resourceId!), {
    ready: !isNew && !!resourceId,
  });

  useEffect(() => {
    if (isNew) {
      form.resetFields();
      form.setFieldsValue({ resourceType: 'MYSQL' });
      setConfig({ ...CONFIG_TEMPLATES.MYSQL.template });
      return;
    }
    if (resource) {
      form.setFieldsValue({
        resourceName: (resource as Resource).resourceName,
        resourceType: (resource as Resource).resourceType,
        driver: (resource as Resource).driver ?? undefined,
      });
      setConfig({ ...((resource as Resource).configJson as Record<string, unknown> ?? {}) });
    }
  }, [isNew, resource]);

  const typeMeta = useMemo(() => {
    const t = resourceType || 'OTHER';
    return {
      label: RESOURCE_TYPES.find((x) => x.value === t)?.label || t,
      color: TYPE_COLOR[t] || TYPE_COLOR.OTHER,
      icon: TYPE_ICON[t] || <DatabaseOutlined />,
      template: CONFIG_TEMPLATES[t] || CONFIG_TEMPLATES.OTHER,
    };
  }, [resourceType]);

  const onTypeChange = (nextType: string) => {
    if (isNew || !config || Object.keys(config).length === 0) {
      setConfig({ ...(CONFIG_TEMPLATES[nextType]?.template ?? {}) });
    } else {
      ModalConfirmSwap(nextType);
    }
  };

  const ModalConfirmSwap = (nextType: string) => {
    const cfg = CONFIG_TEMPLATES[nextType];
    Modal.confirm({
      title: '切换资源类型',
      content: `是否用「${RESOURCE_TYPES.find((x) => x.value === nextType)?.label || nextType}」的默认模板覆盖当前配置？\n取消 = 保留现有 configJson。`,
      okText: '覆盖',
      cancelText: '保留',
      onOk: () => setConfig({ ...(cfg?.template ?? {}) }),
    });
  };

  const handleSave = useClickDebounce(async () => {
    try {
      setConfigError(null);
      const values = await form.validateFields();
      if (!config || typeof config !== 'object') {
        setConfigError('configJson 必须是 JSON 对象');
        return;
      }
      setSaving(true);
      let savedResourceId: number | null = resourceId;
      if (isNew) {
        const req: ResourceCreateRequest = {
          resourceName: values.resourceName!,
          resourceType: values.resourceType as any,
          driver: values.driver,
          configJson: config as any,
        };
        const created = await createResource(req);
        message.success('资源已创建');
        savedResourceId = created.id;
      } else {
        const req: ResourceUpdateRequest = {
          resourceName: values.resourceName,
          resourceType: values.resourceType as any,
          driver: values.driver ?? null,
          configJson: config as any,
        };
        await updateResource(resourceId!, req);
        message.success('保存成功');
        const cfgTag = document.querySelector('.resource-config-tag') as HTMLElement | null;
        if (cfgTag) {
          cfgTag.style.background = '#dcfce7';
          setTimeout(() => {
            if (cfgTag) cfgTag.style.background = '';
          }, 800);
        }
      }
      if (savedResourceId != null && bindToAppIds.length > 0) {
        setAppBindingsBusy(true);
        try {
          let ok = 0;
          for (const appId of bindToAppIds) {
            try {
              await createAppBinding(appId, {
                resourceId: savedResourceId,
                scope: 'READWRITE' as any,
                aliasInApp: undefined,
              });
              ok += 1;
            } catch (e: any) {
              message.warn(
                `绑定应用 ID=${appId} 失败：${e?.response?.data?.message || e?.message || String(e)}，请前往应用详情手动绑定。`,
              );
            }
          }
          if (ok > 0) {
            message.success(`资源已成功绑定到 ${ok} 个应用`);
            setBindToAppIds([]);
            refreshDirectBindings?.();
          }
        } finally {
          setAppBindingsBusy(false);
        }
      }
      if (isNew && savedResourceId != null) {
        history.replace(`/resource/${savedResourceId}`);
      }
    } finally {
      setSaving(false);
    }
  });

  const handleUnbindApp = useClickDebounce((binding: AppBinding) => {
    Modal.confirm({
      title: '确认解除绑定',
      content: `确定解除与应用「appId=${binding.appId}，别名=${binding.effectiveAlias}」的绑定吗？工作流中引用该别名的节点会失败。`,
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteAppBinding(binding.appId, binding.id);
          message.success('已解除绑定');
          refreshDirectBindings?.();
        } catch (e: any) {
          message.error(e?.response?.data?.message || e?.message || '解除绑定失败');
        }
      },
    });
  });

  const handleTest = useClickDebounce(async () => {
    try {
      setTestLoading(true);
      setTestResult(null);
      if (!config || typeof config !== 'object') {
        setTestResult({ ok: false, message: '请先填写 configJson 配置（host/port 等必填项）' });
        return;
      }
      const values = await form.validateFields(['resourceType']).catch(() => null);
      const rt = (values?.resourceType || resourceType) as string;
      if (!rt) {
        setTestResult({ ok: false, message: '请先选择资源类型' });
        return;
      }
      const result = await testResource(
        { resourceType: rt, configJson: config as Record<string, unknown>, resourceId: resourceId ?? undefined },
        { silent: true },
      );
      setTestResult(result);
      if (result.ok) {
        message.success(result.latencyMs ? `${result.message}（耗时 ${result.latencyMs}ms）` : result.message);
      } else if (result.message.includes('尚未实现')) {
        message.info(result.message);
      } else {
        message.error(result.message);
      }
    } finally {
      setTestLoading(false);
    }
  });

  return (
    <div style={{ padding: 24, maxWidth: 1280, margin: '0 auto' }}>
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space align="center" size={16}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => history.push('/resource')}>
              返回资源列表
            </Button>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div
                style={{
                  width: 48,
                  height: 48,
                  borderRadius: 12,
                  background: `linear-gradient(135deg, ${typeMeta.color}, #f97316)`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <span style={{ color: '#fff', fontSize: 20 }}>{typeMeta.icon}</span>
              </div>
              <div>
                <Space align="center" size={12}>
                  <Title level={4} style={{ margin: 0 }}>
                    {isNew ? '新建资源' : (resource ? (resource as Resource).resourceName : '加载中…')}
                  </Title>
                  <Tag color={typeMeta.color} icon={typeMeta.icon} style={{ margin: 0 }}>
                    {typeMeta.label}
                  </Tag>
                  {!isNew ? (
                    <Tag className="resource-config-tag" color="geekblue">
                      ID #{resourceId}
                    </Tag>
                  ) : null}
                </Space>
              </div>
            </div>
          </Space>
          <Space>
            <Button
              icon={<PlayCircleOutlined />}
              loading={testLoading}
              onClick={handleTest}
            >
              测试连接
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saving}
              onClick={handleSave}
            >
              保存
            </Button>
          </Space>
        </div>
      </Card>

      {testResult ? (
        <Alert
          style={{ marginBottom: 16, borderRadius: 12 }}
          type={testResult.ok ? 'success' : (testResult.message.includes('尚未实现') ? 'info' : 'error')}
          showIcon
          icon={testResult.ok ? <CheckCircleOutlined /> : <InfoCircleOutlined />}
          message={testResult.ok ? `连接成功` : (testResult.message.includes('尚未实现') ? '测试连接接口说明' : '连接失败')}
          description={
            <div>
              <div style={{ fontSize: 13, lineHeight: 1.7, whiteSpace: 'pre-wrap' }}>{testResult.message}</div>
              {typeof testResult.latencyMs === 'number' && testResult.ok ? (
                <Tag color="geekblue" style={{ marginTop: 6, marginLeft: 0 }}>
                  耗时：{testResult.latencyMs} ms
                </Tag>
              ) : null}
            </div>
          }
          closable
          onClose={() => setTestResult(null)}
        />
      ) : null}

      <Tabs
        defaultActiveKey="manual"
        style={{ marginBottom: 0 }}
        items={[
          {
            key: 'manual',
            label: (
              <Space size={4}>
                <DatabaseOutlined />
                <span>手动填写配置</span>
              </Space>
            ),
            children: (
              <Row gutter={[16, 16]}>
                <Col span={24} xl={9}>
          <Card title={<span><DatabaseOutlined /> 基本信息</span>} style={{ borderRadius: 12 }} loading={loading && !isNew}>
            <Form form={form} layout="vertical">
              <Row gutter={12}>
                <Col span={14}>
                  <Form.Item
                    label="资源名称"
                    name="resourceName"
                    rules={[
                      { required: true, message: '请输入资源名称' },
                      { min: 1, max: 128 },
                    ]}
                  >
                    <Input placeholder="如 order-db / user-cache / stripe-api" />
                  </Form.Item>
                </Col>
                <Col span={10}>
                  <Form.Item
                    label="资源类型"
                    name="resourceType"
                    rules={[{ required: true, message: '请选择资源类型' }]}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      options={RESOURCE_TYPES}
                      onChange={(v) => onTypeChange(String(v))}
                    />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item
                label="驱动标识（可选）"
                name="driver"
                extra="JDBC driver classname，或自定义资源的协议标识字符串；长度 ≤ 256"
              >
                <Input
                  placeholder={resourceType?.startsWith('HTTP') ? 'n/a' : '如 com.mysql.cj.jdbc.Driver'}
                  style={{ fontFamily: '"SF Mono", Monaco, monospace' }}
                />
              </Form.Item>
              <Divider style={{ margin: '4px 0 12px' }}>应用绑定（数据源归属 App）</Divider>
              <Alert
                style={{ marginBottom: 12 }}
                type="info"
                showIcon
                message="资源必须绑定到 1 个或多个 App 才能被 scope=PRIVATE 的函数集合引用。PLATFORM 通用资源无需绑定。"
                description={
                  <span>
                    绑定后函数通过 <Text code>alias_in_app</Text> 引用该资源，默认别名 = 资源名。
                  </span>
                }
              />
              {directBindings.length > 0 ? (
                <div style={{ marginBottom: 12 }}>
                  <Text type="secondary" style={{ fontSize: 12, marginBottom: 6, display: 'block' }}>
                    已绑定应用（共 {directBindings.length} 个，点 × 可解绑）：
                  </Text>
                  <Space wrap size={[8, 8]}>
                    {directBindings.map((b) => {
                      const a = (appsList as AppInfo[]).find((x) => x.id === b.appId);
                      return (
                        <Tag
                          key={`${b.appId}-${b.id}`}
                          closable
                          onClose={(e) => {
                            e.preventDefault();
                            handleUnbindApp(b);
                          }}
                          color="geekblue"
                          style={{ padding: '2px 8px' }}
                        >
                          <Space size={4}>
                            <AppstoreOutlined />
                            <Text strong>{a?.appName || `App#${b.appId}`}</Text>
                            <Text type="secondary" style={{ fontSize: 11 }}>
                              → {b.effectiveAlias}
                            </Text>
                            {b.scope ? (
                              <Tag color={b.scope === 'READWRITE' ? 'green' : b.scope === 'ADMIN' ? 'purple' : 'blue'} style={{ margin: 0 }}>
                                {b.scope}
                              </Tag>
                            ) : null}
                          </Space>
                        </Tag>
                      );
                    })}
                  </Space>
                </div>
              ) : null}
              <Form.Item
                label={isNew ? '保存时绑定到应用（可多选）' : '追加绑定到应用（可多选）'}
                style={{ marginBottom: 8 }}
                extra="选择后点击「保存」时，会为每个选中的应用自动创建 1 条 READWRITE 级别的 AppResourceBinding；后续可在应用详情中调整别名和权限。"
              >
                <Select
                  mode="multiple"
                  allowClear
                  showSearch
                  loading={appsLoading}
                  value={bindToAppIds}
                  onChange={(v) => setBindToAppIds(Array.isArray(v) ? (v as number[]) : [])}
                  placeholder={
                    appsLoading
                      ? '应用列表加载中…'
                      : appsList.length === 0
                      ? '尚未创建应用，请先前往「应用管理」创建后再选择'
                      : '搜索并选择要绑定的应用（可多选）'
                  }
                  options={appOptions}
                  filterOption={(input, option) =>
                    (option?.label ?? '').toString().toLowerCase().includes(input.toLowerCase())
                  }
                  notFoundContent={
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description={
                        <span>
                          暂无可用应用，请先前往 <a href="#/app" target="_blank" rel="noreferrer">应用管理</a> 创建
                        </span>
                      }
                    />
                  }
                  maxTagCount="responsive"
                />
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col span={24} xl={15}>
          <Card
            title={
              <Space>
                <InfoCircleOutlined />
                <span>configJson（资源配置）</span>
                <Tag className="resource-config-tag" color="blue">
                  {resourceType || '-'}
                </Tag>
                {!isNew ? (
                  <Tag color="orange" style={{ marginLeft: 0 }}>
                    password / secret / token 的值显示为「＜REDACTED＞」表示保留原值，不用重填
                  </Tag>
                ) : null}
              </Space>
            }
            style={{ borderRadius: 12 }}
            extra={
              <Space>
                <Button
                  size="small"
                  onClick={() => setConfig({ ...(typeMeta.template.template ?? {}) })}
                >
                  恢复 {typeMeta.label} 默认模板
                </Button>
              </Space>
            }
          >
            <Alert
              style={{ marginBottom: 12 }}
              type="info"
              showIcon
              message={typeMeta.template.hint}
            />
            {configError ? (
              <Alert
                style={{ marginBottom: 12 }}
                type="error"
                showIcon
                message={configError}
                closable
                onClose={() => setConfigError(null)}
              />
            ) : null}
            <JsonEditor
              value={config ?? {}}
              onChange={(next) => setConfig(next as Record<string, unknown>)}
              height={420}
              readOnly={false}
            />
            <Divider orientation="left" style={{ margin: '20px 0 8px' }}>
              常见字段速查
            </Divider>
            <div style={{ fontSize: 12, color: '#595959', lineHeight: 1.7 }}>
              <Space wrap size={[8, 4]}>
                {Object.keys(typeMeta.template.template).map((k) => (
                  <Tag key={k} color="geekblue" style={{ margin: 0 }}>
                    {k}
                  </Tag>
                ))}
              </Space>
            </div>
          </Card>
        </Col>
              </Row>
            ),
          },
          {
            key: 'cloudsync',
            label: (
              <Space size={4}>
                <CloudUploadOutlined />
                <span>云厂商同步导入（待上线）</span>
                <Tag color="gold" style={{ marginLeft: 6 }}>Planned</Tag>
              </Space>
            ),
            children: (
              <Card style={{ borderRadius: 12 }}>
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                  <Alert
                    type="info"
                    showIcon
                    icon={<CloudUploadOutlined />}
                    message="功能预告：V1.2 将支持从主流云厂商一键导入数据源"
                    description="无需手填连接串/账号密码，RAM/RAM 子账号授权后自动拉取 RDS、Redis、MQ 实例列表，定期同步配置，变更自动通知。"
                  />
                  <Row gutter={16}>
                    <Col span={24} md={12} xl={8}>
                      <Card
                        style={{ borderRadius: 10, cursor: 'not-allowed', opacity: 0.9 }}
                        title={<Space><CloudServerOutlined style={{ color: '#ff6a00' }} /><strong>阿里云 RDS / Redis</strong></Space>}
                      >
                        <div style={{ fontSize: 12, color: '#595959', lineHeight: 1.8 }}>
                          <div>• RDS（MySQL / PostgreSQL / SQLServer）</div>
                          <div>• Tair / ApsaraDB Redis</div>
                          <div>• 消息队列 RocketMQ / Kafka</div>
                        </div>
                        <div style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end' }}>
                          <Tag color="orange" style={{ margin: 0 }}>授权方式：RAM 子账号</Tag>
                        </div>
                      </Card>
                    </Col>
                    <Col span={24} md={12} xl={8}>
                      <Card
                        style={{ borderRadius: 10, cursor: 'not-allowed', opacity: 0.9 }}
                        title={<Space><CloudServerOutlined style={{ color: '#006eff' }} /><strong>腾讯云 Tencent Cloud</strong></Space>}
                      >
                        <div style={{ fontSize: 12, color: '#595959', lineHeight: 1.8 }}>
                          <div>• TencentDB（MySQL / PostgreSQL / MariaDB）</div>
                          <div>• CRS Redis（标准 / 集群 / 读写分离）</div>
                          <div>• CKafka / TDMQ Pulsar</div>
                        </div>
                        <div style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end' }}>
                          <Tag color="blue" style={{ margin: 0 }}>授权方式：CAM 子账号</Tag>
                        </div>
                      </Card>
                    </Col>
                    <Col span={24} md={12} xl={8}>
                      <Card
                        style={{ borderRadius: 10, cursor: 'not-allowed', opacity: 0.9 }}
                        title={<Space><CloudServerOutlined style={{ color: '#00c48a' }} /><strong>火山引擎 Volcengine</strong></Space>}
                      >
                        <div style={{ fontSize: 12, color: '#595959', lineHeight: 1.8 }}>
                          <div>• VeDB（MySQL / PostgreSQL）</div>
                          <div>• Tair / Redis / ByteKV</div>
                          <div>• 消息服务 BMQ / Kafka</div>
                        </div>
                        <div style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end' }}>
                          <Tag color="green" style={{ margin: 0 }}>授权方式：IAM 子账号</Tag>
                        </div>
                      </Card>
                    </Col>
                  </Row>
                  <div style={{ textAlign: 'center', padding: 8 }}>
                    <Space size={12}>
                      <Select value={cloudProvider} onChange={setCloudProvider} style={{ width: 200 }} disabled>
                        <Select.Option value="aliyun">阿里云</Select.Option>
                        <Select.Option value="tencent">腾讯云</Select.Option>
                        <Select.Option value="volc">火山引擎</Select.Option>
                        <Select.Option value="huawei">华为云（规划中）</Select.Option>
                      </Select>
                      <Button type="primary" icon={<CloudUploadOutlined />} disabled style={{ minWidth: 220 }}>
                        授权并一键同步（V1.2 开启）
                      </Button>
                    </Space>
                  </div>
                </Space>
              </Card>
            ),
          },
        ]}
      />
    </div>
  );
};

export default ResourceEditor;
