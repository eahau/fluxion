import { history, useAccess, useRequest } from '@umijs/max';
import {
  Button,
  Card,
  Checkbox,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Tag,
  Tooltip,
  Upload,
  message,
  Tabs,
  Table,
  Segmented,
  Badge,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ImportOutlined,
  ExportOutlined,
  EyeOutlined,
  CloudUploadOutlined,
  FileAddOutlined,
  ApartmentOutlined,
  GlobalOutlined,
  LinkOutlined,
  AppstoreOutlined,
  UnorderedListOutlined,
  PartitionOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { useState, useMemo } from 'react';
import {
  getWorkflows,
  deleteWorkflow,
  publishWorkflow,
  importOpenAPI,
  confirmImportOpenAPI,
  importWorkflowDefinition,
} from '@/services/workflow';
import { useClickDebounce } from '@/utils/useClickDebounce';
import OpenApiExportModal from './components/OpenApiExportModal';
import { getInstances, getInstanceGroups } from '@/services/instance';
import type { WorkflowDefinition } from '@/types/workflow';
import type { PublishTarget, PublishType, InstanceInfo } from '@/types/api';

const { Text } = Typography;

const statusConfig: Record<string, { label: string; color: string; bg: string }> = {
  ACTIVE: { label: '已发布', color: '#52c41a', bg: '#f6ffed' },
  DEPRECATED: { label: '已下线', color: '#8c8c8c', bg: '#f5f5f5' },
  DRAFT: { label: '草稿', color: '#faad14', bg: '#fffbe6' },
};

import { METHOD_COLOR_MAP } from '@/constants/protocol';

const scopeConfig: Record<string, { label: string; color: string }> = {
  PLATFORM: { label: '平台内置', color: '#722ed1' },
  PRIVATE: { label: '应用私有', color: '#1890ff' },
  MARKETPLACE: { label: '市场', color: '#fa8c16' },
};

const categoryConfig: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  META: { label: '元工作流', color: '#722ed1', icon: <ApartmentOutlined /> },
  BUSINESS: { label: '业务', color: '#1890ff', icon: <GlobalOutlined /> },
};

function formatBindingSummary(record: WorkflowDefinition): string {
  const parts: string[] = [];
  if (record.method) parts.push(record.method);
  if (record.path) parts.push(record.path);
  return parts.length > 0 ? parts.join(' ') : '-';
}

const WorkflowList: React.FC = () => {
  const access = useAccess();
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [viewMode, setViewMode] = useState<string>('card');
  const { data, loading, refresh } = useRequest(
    () => getWorkflows({ keyword }),
    { formatResult: (res) => res, refreshDeps: [keyword] },
  );
  const [importItems, setImportItems] = useState<any[] | null>(null);
  const [publishRecord, setPublishRecord] = useState<WorkflowDefinition | null>(null);
  const [publishForm] = Form.useForm();
  const [exportVisible, setExportVisible] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [importDefinitionFile, setImportDefinitionFile] = useState<File | null>(null);
  const [importDefinitionData, setImportDefinitionData] = useState<WorkflowDefinition | null>(null);
  const [publishing, setPublishing] = useState(false);

  const { data: groups } = useRequest(getInstanceGroups);
  const { data: instances } = useRequest(getInstances);

  const allWorkflows = data?.list || [];

  const workflows = useMemo(() => {
    if (statusFilter === 'all') return allWorkflows;
    return allWorkflows.filter((w: WorkflowDefinition) => w.status === statusFilter);
  }, [allWorkflows, statusFilter]);

  const statusCounts = useMemo(() => {
    const counts: Record<string, number> = { all: allWorkflows.length };
    allWorkflows.forEach((w: WorkflowDefinition) => {
      const s = w.status || 'DRAFT';
      counts[s] = (counts[s] || 0) + 1;
    });
    return counts;
  }, [allWorkflows]);

  const handleDelete = useClickDebounce((record: WorkflowDefinition) => {
    if (!record.id) {
      message.warning('该工作流缺少 ID，无法删除');
      return;
    }
    Modal.confirm({
      title: '确认删除',
      content: `确定删除工作流「${record.name}」吗？`,
      onOk: async () => {
        try {
          await deleteWorkflow(record.id!);
          message.success('删除成功');
          refresh();
        } catch (e: any) {
          const msg = e?.response?.data?.message || e?.message || '删除失败，请稍后重试';
          message.error(msg);
          throw e;
        }
      },
    });
  });

  const handlePublish = (record: WorkflowDefinition) => {
    if (!record.id) {
      message.warning('该工作流缺少 ID，无法发布');
      return;
    }
    publishForm.setFieldsValue({
      type: record.publishTarget?.type || 'ALL',
      groups: record.publishTarget?.groups || [],
      instanceIds: record.publishTarget?.instanceIds || [],
    });
    setPublishRecord(record);
  };

  const handlePublishConfirm = useClickDebounce(async () => {
    if (!publishRecord?.id) return;
    try {
      const values = await publishForm.validateFields();
      const target: PublishTarget = {
        type: values.type as PublishType,
        groups: values.type === 'APP_GROUP' ? values.groups : undefined,
        instanceIds: values.type === 'INSTANCES' ? values.instanceIds : undefined,
      };
      setPublishing(true);
      await publishWorkflow(publishRecord.id, target);
      message.success('发布成功');
      setPublishRecord(null);
      refresh();
    } catch (e: any) {
      if (e?.errorFields) return;
      const msg = e?.response?.data?.message || e?.message || '发布失败，请稍后重试';
      message.error(msg);
    } finally {
      setPublishing(false);
    }
  });

  const handleImport = useClickDebounce(async (file: File) => {
    try {
      const res = await importOpenAPI(file);
      setImportItems((res || []).map((item: any) => ({ ...item, selected: true })));
    } catch (e) {
      console.error('OpenAPI 解析失败:', e);
    }
    return false;
  });

  const readFileText = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsText(file);
    });

  const handleImportDefinition = useClickDebounce(async (file: File) => {
    try {
      const content = await readFileText(file);
      const data = JSON.parse(content) as WorkflowDefinition;
      if (!data.name || !data.nodes) {
        message.error('JSON 格式不正确：缺少 name 或 nodes');
        return false;
      }
      setImportDefinitionFile(file);
      setImportDefinitionData(data);
    } catch (e) {
      message.error('工作流 JSON 解析失败');
    }
    return false;
  });

  const confirmImportDefinition = useClickDebounce(async () => {
    if (!importDefinitionFile) return;
    try {
      await importWorkflowDefinition(importDefinitionFile);
      message.success('工作流导入成功');
      setImportDefinitionFile(null);
      setImportDefinitionData(null);
      refresh();
    } catch (e) {
      console.error('工作流导入失败:', e);
    }
  });

  const confirmImport = useClickDebounce(async () => {
    if (!importItems) return;
    try {
      await confirmImportOpenAPI(importItems.filter((i) => i.selected));
      setImportItems(null);
      message.success('导入成功');
      refresh();
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || '导入失败，请稍后重试';
      message.error(msg, 6);
    }
  });

  // 表格列定义
  const tableColumns = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (name: string, record: WorkflowDefinition) => {
        const category = categoryConfig[record.category || 'BUSINESS'];
        return (
          <Space size={8}>
            <span style={{ color: category.color }}>{category.icon}</span>
            <Text strong style={{ fontSize: 13 }}>{name}</Text>
          </Space>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => {
        const s = statusConfig[status || 'DRAFT'];
        return <Tag color={s.bg} style={{ color: s.color, borderColor: s.color, borderRadius: 4 }}>{s.label}</Tag>;
      },
    },
    {
      title: '协议绑定',
      render: (_: any, record: WorkflowDefinition) => (
        <Text code style={{ fontSize: 12 }}>{formatBindingSummary(record)}</Text>
      ),
    },
    {
      title: '节点',
      render: (_: any, record: WorkflowDefinition) => (
        <Tag>{record.nodes?.length || 0}</Tag>
      ),
      width: 70,
    },
    {
      title: '作用域',
      dataIndex: 'scope',
      width: 100,
      render: (scope: string, record: WorkflowDefinition) => {
        const s = scopeConfig[scope || 'PRIVATE'];
        return (
          <Space size={4} direction="vertical">
            <Tag color={s.color} style={{ borderRadius: 4, fontSize: 12 }}>{s.label}</Tag>
            {scope === 'PRIVATE' && record.appGroup && (
              <Text type="secondary" style={{ fontSize: 11 }}>{record.appGroup}</Text>
            )}
          </Space>
        );
      },
    },
    {
      title: '类别',
      dataIndex: 'category',
      width: 100,
      render: (cat: string) => {
        const c = categoryConfig[cat || 'BUSINESS'];
        return <Tag color={c.color} style={{ borderRadius: 4, fontSize: 12 }}>{c.label}</Tag>;
      },
    },
    {
      title: '操作',
      width: 200,
      render: (_: any, record: WorkflowDefinition) => (
        <Space size={4}>
          <Button size="small" icon={<EyeOutlined />} onClick={() => history.push(`/workflow/detail/${record.id}`)}>详情</Button>
          {access.canEditWorkflow && (
            <Button size="small" icon={<EditOutlined />} onClick={() => history.push(`/workflow/designer/${record.id}`)}>编辑</Button>
          )}
          <Button
            size="small"
            icon={<CloudUploadOutlined />}
            disabled={record.status === 'ACTIVE' || record.status === 'DEPRECATED' || record.isProtected}
            onClick={() => handlePublish(record)}
          >发布</Button>
          {access.canEditWorkflow && (
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              disabled={record.isProtected}
              onClick={() => handleDelete(record)}
            />
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* 顶部操作栏 */}
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space>
            <PartitionOutlined style={{ fontSize: 20, color: '#6366f1' }} />
            <Text strong style={{ fontSize: 16 }}>工作流管理</Text>
            <Tag style={{ borderRadius: 4, marginLeft: 8 }}>{allWorkflows.length} 个</Tag>
          </Space>
          <Space>
            <Input.Search
              placeholder="搜索工作流"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onSearch={refresh}
              style={{ width: 240 }}
            />
            <Upload accept=".json,.yaml,.yml" beforeUpload={handleImport} showUploadList={false}>
              <Button icon={<ImportOutlined />}>导入 OpenAPI</Button>
            </Upload>
            <Upload accept=".json" beforeUpload={handleImportDefinition} showUploadList={false}>
              <Button icon={<FileAddOutlined />}>导入 JSON</Button>
            </Upload>
            <Button icon={<ExportOutlined />} onClick={() => setExportVisible(true)}>导出</Button>
            {access.canEditWorkflow && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/workflow/designer')}>
                新建工作流
              </Button>
            )}
            <Segmented
              value={viewMode}
              onChange={(v) => setViewMode(v as string)}
              options={[
                { value: 'card', icon: <AppstoreOutlined /> },
                { value: 'table', icon: <UnorderedListOutlined /> },
              ]}
              size="small"
            />
          </Space>
        </div>
      </Card>

      {/* 状态筛选 Tabs */}
      <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
        <Tabs
          activeKey={statusFilter}
          onChange={setStatusFilter}
          items={[
            {
              key: 'all',
              label: <span>全部 <Badge count={statusCounts.all || 0} style={{ backgroundColor: '#f0f0f0', color: '#595959', fontSize: 11, boxShadow: 'none' }} /></span>,
            },
            {
              key: 'ACTIVE',
              label: <span style={{ color: '#52c41a' }}>已发布 <Badge count={statusCounts.ACTIVE || 0} style={{ backgroundColor: '#f6ffed', color: '#52c41a', fontSize: 11, boxShadow: 'none' }} /></span>,
            },
            {
              key: 'DRAFT',
              label: <span style={{ color: '#faad14' }}>草稿 <Badge count={statusCounts.DRAFT || 0} style={{ backgroundColor: '#fffbe6', color: '#faad14', fontSize: 11, boxShadow: 'none' }} /></span>,
            },
            {
              key: 'DEPRECATED',
              label: <span style={{ color: '#8c8c8c' }}>已下线 <Badge count={statusCounts.DEPRECATED || 0} style={{ backgroundColor: '#f5f5f5', color: '#8c8c8c', fontSize: 11, boxShadow: 'none' }} /></span>,
            },
          ]}
        />

        {/* 卡片视图 */}
        {viewMode === 'card' && (
          <>
            {workflows.length === 0 && !loading ? (
              <Empty description="暂无工作流" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: '40px 0' }} />
            ) : (
              <Row gutter={[16, 16]}>
                {workflows.map((record: WorkflowDefinition) => {
                  const status = statusConfig[record.status || 'DRAFT'];
                  const category = categoryConfig[record.category || 'BUSINESS'];
                  return (
                    <Col key={record.id} xs={24} sm={12} lg={8} xl={6}>
                      <Card
                        hoverable
                        size="small"
                        className="workflow-list-card"
                        onClick={() => history.push(`/workflow/detail/${record.id}`)}
                        style={{ borderRadius: 12, border: '1px solid #f0f0f0' }}
                        title={
                          <Tooltip title={record.name} placement="topLeft">
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                              <span style={{ color: category.color, flexShrink: 0 }}>{category.icon}</span>
                              <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>
                                {record.name}
                              </span>
                            </div>
                          </Tooltip>
                        }
                        extra={
                          <Tag color={status.bg} style={{ color: status.color, borderColor: status.color, margin: 0, borderRadius: 4, fontSize: 11 }}>
                            {status.label}
                          </Tag>
                        }
                        actions={[
                          ...(access.canEditWorkflow ? [
                            <Button key="edit" type="text" size="small" icon={<EditOutlined />}
                              onClick={(e) => { e.stopPropagation(); history.push(`/workflow/designer/${record.id}`); }}>
                              编辑
                            </Button>,
                          ] : []),
                          <Button key="publish" type="text" size="small" icon={<CloudUploadOutlined />}
                            disabled={record.status === 'ACTIVE' || record.status === 'DEPRECATED' || record.isProtected}
                            onClick={(e) => { e.stopPropagation(); handlePublish(record); }}>
                            发布
                          </Button>,
                          ...(access.canEditWorkflow ? [
                            <Button key="delete" type="text" size="small" danger icon={<DeleteOutlined />}
                              disabled={record.isProtected}
                              onClick={(e) => { e.stopPropagation(); handleDelete(record); }}>
                              删除
                            </Button>,
                          ] : []),
                        ]}
                      >
                        <Tooltip title={formatBindingSummary(record)} placement="topLeft">
                          <div style={{ color: '#595959', fontSize: 12, marginBottom: 10, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            <LinkOutlined style={{ marginRight: 6 }} />
                            {formatBindingSummary(record)}
                          </div>
                        </Tooltip>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          <Space wrap size={4}>
                            {record.method && (
                              <Tag size="small" color={METHOD_COLOR_MAP[record.method] || '#6366f1'} style={{ borderRadius: 4, fontWeight: 600 }}>{record.method}</Tag>
                            )}
                            <Tag size="small" color={record.protocol === 'HTTP' || record.protocol === 'HTTPS' ? '#6366f1' : '#8b5cf6'} style={{ borderRadius: 4 }}>{record.protocol}</Tag>
                            <Tag size="small" color={scopeConfig[record.scope || 'PRIVATE'].color} style={{ borderRadius: 4 }}>{scopeConfig[record.scope || 'PRIVATE'].label}</Tag>
                            {record.scope === 'PRIVATE' && record.appGroup && (
                              <Tag size="small" style={{ borderRadius: 4, fontSize: 11 }}>{record.appGroup}</Tag>
                            )}
                          </Space>
                          <Text type="secondary" style={{ fontSize: 11 }}>
                            {record.nodes?.length || 0} 节点
                          </Text>
                        </div>
                      </Card>
                    </Col>
                  );
                })}
              </Row>
            )}
          </>
        )}

        {/* 表格视图 */}
        {viewMode === 'table' && (
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={workflows}
            columns={tableColumns}
            pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }}
          />
        )}
      </Card>

      {/* 导入 OpenAPI 预览 */}
      <Modal title="导入 OpenAPI 预览" open={!!importItems} onCancel={() => setImportItems(null)} onOk={confirmImport} width={700}>
        {importItems?.map((item, idx) => (
          <div key={idx} style={{ marginBottom: 8 }}>
            <Checkbox
              checked={item.selected}
              onChange={(e) => {
                const next = [...importItems];
                next[idx].selected = e.target.checked;
                setImportItems(next);
              }}
              style={{ marginRight: 8 }}
            />
            <Tag>{item.protocol || 'HTTP'}</Tag> {item.method ? `${item.method} ` : ''}{item.path} - {item.name}
          </div>
        ))}
      </Modal>

      {/* 导入工作流 JSON 预览 */}
      <Modal
        title="导入工作流 JSON 预览"
        open={!!importDefinitionData}
        onCancel={() => { setImportDefinitionFile(null); setImportDefinitionData(null); }}
        onOk={confirmImportDefinition}
        width={560}
      >
        {importDefinitionData && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <div><b>ID:</b> {importDefinitionData.id || '-'}</div>
            <div><b>名称：</b>{importDefinitionData.name}</div>
            <div><b>协议：</b>{importDefinitionData.protocol} {formatBindingSummary(importDefinitionData)}</div>
            <div><b>节点：</b></div>
            {importDefinitionData.nodes?.map((node) => (
              <div key={node.id} style={{ marginLeft: 16 }}>{node.name} ({node.type})</div>
            ))}
          </Space>
        )}
      </Modal>

      {/* 发布弹窗 */}
      <Modal
        title={`发布工作流：${publishRecord?.name}`}
        open={!!publishRecord}
        onCancel={() => setPublishRecord(null)}
        onOk={handlePublishConfirm}
        confirmLoading={publishing}
        destroyOnClose
        width={560}
      >
        <Form form={publishForm} layout="vertical">
          <Form.Item name="type" label="发布目标" initialValue="ALL" style={{ marginBottom: 12 }}>
            <Radio.Group>
              <Radio value="ALL">全量广播</Radio>
              <Radio value="APP_GROUP">应用群</Radio>
              <Radio value="INSTANCES">指定实例</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.type !== curr.type}>
            {({ getFieldValue }) =>
              getFieldValue('type') === 'APP_GROUP' ? (
                <Form.Item name="groups" label="选择应用群" rules={[{ required: true, message: '请选择至少一个应用群' }]} style={{ marginBottom: 12 }}>
                  <Select mode="multiple" placeholder="请选择应用群"
                    options={groups?.map((g) => ({ label: `${g.name} (${g.instanceCount} 实例)`, value: g.name }))}
                    loading={!groups}
                  />
                </Form.Item>
              ) : getFieldValue('type') === 'INSTANCES' ? (
                <Form.Item name="instanceIds" label="选择实例" rules={[{ required: true, message: '请选择至少一个实例' }]} style={{ marginBottom: 12 }}>
                  <Select mode="multiple" placeholder="请选择实例"
                    options={instances?.map((i: InstanceInfo) => ({
                      label: `${i.instanceId} (${i.appGroup} / ${i.host}:${i.port})`,
                      value: i.instanceId,
                    }))}
                    loading={!instances}
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>

      <OpenApiExportModal
        workflowIds={selectedRowKeys.map((k) => String(k))}
        visible={exportVisible}
        onClose={() => setExportVisible(false)}
      />
    </div>
  );
};

export default WorkflowList;
