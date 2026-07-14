import { history, useAccess, useModel, useRequest } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Divider,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Row,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  AppstoreOutlined,
  UserOutlined,
  TeamOutlined,
  LinkOutlined,
  CheckCircleOutlined,
  StopOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import {
  listApps,
  deleteApp,
  createApp,
  updateApp,
  type App,
  type AppCreateRequest,
  type AppUpdateRequest,
} from '@/services/apps';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { Text, Paragraph } = Typography;

const STATUS_META: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  ACTIVE: { label: '正常', color: 'success', icon: <CheckCircleOutlined /> },
  DISABLED: { label: '禁用', color: 'default', icon: <StopOutlined /> },
};

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '正常' },
  { value: 'DISABLED', label: '禁用' },
];

const DEFAULT_DEPT_OPTIONS: string[] = [
  '技术部',
  '基础架构组',
  '数据平台组',
  '后端组',
  '前端组',
  '测试组',
  '产品部',
  '设计部',
  '运营部',
  '市场部',
  '财务部',
  '人力资源部',
  '法务合规部',
];

const CICD_DEPLOY_TARGETS: string[] = ['DEV', 'TEST', 'UAT', 'STAGING', 'PROD', 'CANARY'];
const CICD_HOOK_TYPES: string[] = [
  'preBuild',
  'postBuild',
  'preDeploy',
  'postDeploy',
  'deploySuccess',
  'deployFailed',
];

const AppList: React.FC = () => {
  const access = useAccess();
  const { initialState } = useModel('@@initialState');
  const currentUserName = initialState?.currentUser?.name ?? '系统';
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [pageIndex, setPageIndex] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<App | null>(null);
  const [form] = Form.useForm<AppCreateRequest & AppUpdateRequest & { creator?: string }>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setPageIndex(1);
  }, [keyword, statusFilter]);

  const { data, loading, refresh } = useRequest(() => listApps(), {
    formatResult: (res) => res,
    refreshDeps: [],
  });

  const allApps = useMemo(() => (data ?? []) as App[], [data]);

  const ownerGroupOptions = useMemo(() => {
    const existing = Array.from(new Set(allApps.map((a) => a.owner).filter((v): v is string => !!v)));
    const merged = Array.from(new Set([...DEFAULT_DEPT_OPTIONS, ...existing]));
    return merged.map((v) => ({ value: v, label: v }));
  }, [allApps]);

  const filteredApps = useMemo(() => {
    return allApps.filter((app) => {
      if (statusFilter !== 'all' && app.status !== statusFilter) return false;
      if (keyword) {
        const k = keyword.trim().toLowerCase();
        return (
          app.appKey.toLowerCase().includes(k) ||
          app.appName.toLowerCase().includes(k) ||
          (app.description ?? '').toLowerCase().includes(k) ||
          (app.owner ?? '').toLowerCase().includes(k)
        );
      }
      return true;
    });
  }, [allApps, keyword, statusFilter]);

  const total = filteredApps.length;
  const pageList = filteredApps.slice((pageIndex - 1) * pageSize, pageIndex * pageSize);

  const openCreate = () => {
    setEditingRecord(null);
    form.resetFields();
    form.setFieldsValue({ status: 'ACTIVE', creator: currentUserName });
    setModalOpen(true);
  };

  const openEdit = (record: App) => {
    setEditingRecord(record);
    form.setFieldsValue({
      appKey: record.appKey,
      appName: record.appName,
      description: record.description ?? undefined,
      owner: record.owner ?? undefined,
      status: record.status,
      creator: (record as any).creator ?? currentUserName,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editingRecord) {
        await updateApp(editingRecord.id, {
          appName: values.appName,
          description: values.description,
          owner: values.owner,
          status: values.status,
        } as any);
        message.success('应用已更新');
      } else {
        await createApp({
          appKey: values.appKey!,
          appName: values.appName!,
          description: values.description,
          owner: values.owner,
          status: values.status as any,
          creator: values.creator ?? currentUserName,
        } as any);
        message.success('应用已创建');
      }
      setModalOpen(false);
      refresh();
    } catch (e: any) {
      const raw = e?.response?.data ?? e;
      const detail: string =
        (raw && typeof raw === 'object' && (raw.detail || raw.message || raw.error)) ||
        e?.message ||
        String(e) ||
        '操作失败';
      message.error(detail.length > 160 ? detail.slice(0, 160) + '…' : detail);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = useClickDebounce((record: App) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定删除应用「${record.appName}（${record.appKey}）」吗？\n该操作会同时解除所有资源绑定。`,
      okType: 'danger',
      onOk: async () => {
        await deleteApp(record.id);
        message.success('删除成功');
        refresh();
      },
    });
  });

  return (
    <div style={{ padding: 24 }}>
      <Card
        style={{ borderRadius: 12, border: 'none', marginBottom: 16 }}
        className="home-stat-card"
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                background: 'linear-gradient(135deg, #6366f1, #22d3ee)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <AppstoreOutlined style={{ fontSize: 18, color: '#fff' }} />
            </div>
            <div>
              <Typography.Title level={4} style={{ margin: 0 }}>
                应用管理
              </Typography.Title>
              <Text type="secondary" style={{ fontSize: 12 }}>
                管理所有业务应用及资源绑定（函数集合 appGroup 对齐 appKey）
              </Text>
            </div>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建应用
          </Button>
        </div>
        <div style={{ marginTop: 16, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Input.Search
            placeholder="搜索 appKey / appName / owner / 描述"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            allowClear
            style={{ width: 360 }}
          />
          <Select
            value={statusFilter}
            onChange={setStatusFilter}
            options={[
              { value: 'all', label: '全部状态' },
              ...STATUS_OPTIONS,
            ]}
            style={{ width: 160 }}
          />
        </div>
      </Card>

      {loading && pageList.length === 0 ? (
        <Card style={{ borderRadius: 12 }}>
          <Empty description="加载中…" />
        </Card>
      ) : pageList.length === 0 ? (
        <Card style={{ borderRadius: 12 }}>
          <Empty description="暂无应用，点击右上角「新建应用」开始创建" />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {pageList.map((app) => {
            const st = STATUS_META[app.status] || STATUS_META.ACTIVE;
            return (
              <Col span={24} md={12} xl={8} key={app.id}>
                <Card
                  style={{ borderRadius: 12, height: '100%' }}
                  hoverable
                  actions={[
                    <Tooltip title="查看详情 / 资源绑定">
                      <EyeOutlined
                        key="view"
                        onClick={() => history.push(`/app/${app.id}`)}
                      />
                    </Tooltip>,
                    <Tooltip title="编辑">
                      <EditOutlined key="edit" onClick={() => openEdit(app)} />
                    </Tooltip>,
                    <Tooltip title="删除">
                      <DeleteOutlined
                        key="del"
                        style={{ color: '#ff4d4f' }}
                        onClick={() => handleDelete(app)}
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
                          background: '#eef2ff',
                          color: '#6366f1',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 16,
                        }}
                      >
                        <AppstoreOutlined />
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <Text strong style={{ fontSize: 15 }}>
                            {app.appName}
                          </Text>
                          <Tag color={st.color as any} icon={st.icon} style={{ margin: 0 }}>
                            {st.label}
                          </Tag>
                        </div>
                        <Tag color="geekblue" style={{ marginTop: 4, marginLeft: 0 }}>
                          <LinkOutlined /> {app.appKey}
                        </Tag>
                      </div>
                    </Space>
                  </div>
                  <Paragraph
                    type="secondary"
                    ellipsis={{ rows: 2 }}
                    style={{ marginTop: 12, marginBottom: 8, minHeight: 40, fontSize: 13 }}
                  >
                    {app.description || '暂无描述'}
                  </Paragraph>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      paddingTop: 8,
                      borderTop: '1px dashed #f0f0f0',
                      fontSize: 12,
                      color: '#8c8c8c',
                    }}
                  >
                    <Space size={4}>
                      <UserOutlined />
                      <span>{app.owner || '未指派'}</span>
                    </Space>
                    <span>
                      创建：{app.createdAt ? new Date(app.createdAt).toLocaleDateString() : '-'}
                    </span>
                  </div>
                </Card>
              </Col>
            );
          })}
        </Row>
      )}

      {total > pageSize && (
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
      )}

      <Modal
        title={editingRecord ? `编辑应用：${editingRecord.appName}` : '新建应用'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        confirmLoading={submitting}
        destroyOnHidden
        width={620}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            label="App Key（稳定业务标识）"
            name="appKey"
            rules={[
              { required: !editingRecord, message: '请输入 appKey' },
              { pattern: /^[a-z0-9][a-z0-9_-]*$/, message: '仅允许小写字母、数字、-、_' },
            ]}
          >
            <Input
              placeholder="如 order-app / pay-service"
              disabled={!!editingRecord}
              style={{ fontFamily: '"SF Mono", Monaco, monospace' }}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item
                label="应用名称"
                name="appName"
                rules={[{ required: true, message: '请输入应用名称' }]}
              >
                <Input placeholder="如 订单中心" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="状态" name="status">
                <Select options={STATUS_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={14}>
              <Form.Item
                label="所属组/部门"
                name="owner"
                tooltip="选择或输入此应用所属的组织架构（团队/部门/小组），用于按组织维度检索应用和资源权限归属"
              >
                <Select
                  mode="combobox"
                  showSearch
                  optionFilterProp="label"
                  placeholder="如 技术部 / 后端组 / 数据平台组（可手动输入自定义组）"
                  options={ownerGroupOptions}
                  allowClear
                >
                </Select>
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item
                label="创建人"
                name="creator"
                tooltip="创建人自动根据当前登录账号填充，创建后不可修改"
              >
                <Input
                  prefix={<UserOutlined />}
                  disabled
                  placeholder="将自动填充登录用户"
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} maxLength={512} placeholder="简要描述此应用的用途" showCount />
          </Form.Item>

          {/* ============ CICD 集成预留锚点 START ============ */}
          {/*
            TODO(架构接入): CICD 配置入口锚点
              - 后续接入时：将下方 <Button> 改为实际可点击入口，弹出 CICD 配置抽屉（含 Webhook、CI 项目地址、凭据管理等）
              - 建议 SDK：@/services/cicd.{ts} 新建 cicdConfig CRUD + 与 apps 绑定
          */}
          {/*
            TODO(架构接入): 构建流程钩子注册表
              - 下方 hookTypes 为预置钩子枚举；实际接入时：
                1) 在 workflow-engine 中触发这些钩子时回调此应用配置的 webhook / pipeline
                2) 保存结构建议：Map<hookType, HookSpec[]>，HookSpec = { name, endpoint, secretRef, enabled }
          */}
          {/*
            TODO(架构接入): 部署目标环境同步
              - 下方 deployTargets 为占位枚举；实际接入时：
                从运维中心（Nacos / 发布平台 / K8s namespace 服务发现）拉取实时可用环境列表
                并与发布系统（如 ArgoCD / Jenkins / FluxCD）的 targetName 保持 1:1 对齐
          */}
          <Divider orientation="left" plain style={{ marginTop: 4 }}>
            <Tag icon={<BulbOutlined />} color="processing">CICD 集成（预留 / 敬请期待）</Tag>
          </Divider>
          <Row gutter={12}>
            <Col span={10}>
              <Form.Item label="CICD 配置入口">
                <Tooltip title="CICD 集成模块开发中，后续版本开放 Jenkins / GitLab CI / GitHub Actions / ArgoCD 绑定">
                  <Button icon={<LinkOutlined />} block disabled>前往配置（敬请期待）</Button>
                </Tooltip>
              </Form.Item>
            </Col>
            <Col span={14}>
              <Form.Item label="构建流程钩子（预置）" name={['cicdReserved', 'hookTypes']}>
                <Select
                  mode="multiple"
                  disabled
                  placeholder="preBuild / postBuild / deploySuccess 等钩子将在后续版本可配置"
                  options={CICD_HOOK_TYPES.map((v) => ({ value: v, label: v }))}
                  allowClear
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="部署目标环境（预置）" name={['cicdReserved', 'deployTargets']}>
            <Select
              mode="multiple"
              disabled
              placeholder="DEV / TEST / STAGING / PROD 等环境将与运维中心环境管理打通"
              options={CICD_DEPLOY_TARGETS.map((v) => ({
                value: v,
                label: <Tag color={v.includes('PROD') ? 'red' : v.includes('CANARY') ? 'orange' : 'blue'}>{v}</Tag>,
              }))}
              allowClear
            />
          </Form.Item>
          {/* ============ CICD 集成预留锚点 END ============ */}
        </Form>
      </Modal>
    </div>
  );
};

export default AppList;
