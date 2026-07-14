import { history, useParams, useRequest } from '@umijs/max';
import {
  Button, Card, Collapse, Descriptions, Space, Table, Tag, Timeline, Tabs, Typography, Row, Col, Statistic,
} from 'antd';
import {
  RollbackOutlined,
  ArrowLeftOutlined,
  EditOutlined,
  CloudUploadOutlined,
  PartitionOutlined,
  ClockCircleOutlined,
  HistoryOutlined,
  FileTextOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import { useState } from 'react';
import { getWorkflow, getWorkflowVersions, rollbackWorkflow } from '@/services/workflow';
import { listExecutions } from '@/services/execution';
import type { WorkflowDefinition } from '@/types/workflow';
import { METHOD_COLOR_MAP } from '@/constants/protocol';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { Text, Title } = Typography;

function renderJson(obj: Record<string, unknown> | undefined | null) {
  if (!obj || Object.keys(obj).length === 0) return <Tag>未配置</Tag>;
  return (
    <pre style={{ margin: 0, fontSize: 12, maxHeight: 320, overflow: 'auto', background: '#f8fafc', padding: 12, borderRadius: 8, border: '1px solid #e2e8f0' }}>
      {JSON.stringify(obj, null, 2)}
    </pre>
  );
}

const PUBLISH_TYPE_LABEL: Record<string, string> = {
  ALL: '全量广播',
  APP_GROUP: '按应用群',
  INSTANCES: '按实例',
};

const statusMeta: Record<string, { color: string; label: string; icon: React.ReactNode }> = {
  ACTIVE: { color: '#52c41a', label: '已发布', icon: <CheckCircleOutlined /> },
  DRAFT: { color: '#faad14', label: '草稿', icon: <EditOutlined /> },
  DEPRECATED: { color: '#8c8c8c', label: '已下线', icon: <CloseCircleOutlined /> },
};

const execStatusColor: Record<string, string> = {
  SUCCESS: 'success',
  COMPLETED: 'success',
  FAILED: 'error',
  RUNNING: 'processing',
};

const WorkflowDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { data: workflow } = useRequest(() => getWorkflow(id!), { ready: !!id });
  const { data: versions, refresh: refreshVersions } = useRequest(() => getWorkflowVersions(id!), { ready: !!id });
  const { data: execData } = useRequest(
    () => listExecutions({ workflowId: id, page: 1, pageSize: 10 }),
    { ready: !!id },
  );

  const [activeTab, setActiveTab] = useState('overview');

  const handleRollback = useClickDebounce(async (version: number) => {
    await rollbackWorkflow(id!, version);
    refreshVersions();
  });

  const wfStatus = statusMeta[workflow?.status || 'DRAFT'] || statusMeta.DRAFT;

  return (
    <div>
      {/* 顶部信息栏 */}
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <div style={{
              width: 48, height: 48, borderRadius: 12,
              background: 'linear-gradient(135deg, #6366f1, #818cf8)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <PartitionOutlined style={{ fontSize: 22, color: '#fff' }} />
            </div>
            <div>
              <Space align="center" size={12}>
                <Title level={4} style={{ margin: 0 }}>{workflow?.name || '-'}</Title>
                <Tag color={wfStatus.color} icon={wfStatus.icon} style={{ borderRadius: 4 }}>{wfStatus.label}</Tag>
                {workflow?.category === 'META' && <Tag color="purple" style={{ borderRadius: 4 }}>元工作流</Tag>}
              </Space>
              <div style={{ marginTop: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {workflow?.protocol} {workflow?.method || ''} {workflow?.path || ''} | v{workflow?.version} | {workflow?.nodes?.length || 0} 节点
                </Text>
              </div>
            </div>
          </div>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => history.push('/workflow')}>返回</Button>
            <Button type="primary" icon={<EditOutlined />} onClick={() => history.push(`/workflow/designer/${workflow?.workflowId || workflow?.id || id}`)}>
              编辑
            </Button>
          </Space>
        </div>
      </Card>

      {/* Tab 区域 */}
      <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'overview',
              label: <span><PartitionOutlined /> 概览</span>,
              children: (
                <Row gutter={24}>
                  <Col span={16}>
                    <Descriptions
                      bordered
                      column={2}
                      size="small"
                      labelStyle={{ fontWeight: 500, background: '#f8fafc', width: 120 }}
                    >
                      <Descriptions.Item label="ID">
                        <Text copyable style={{ fontSize: 13 }}>{workflow?.id}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="类别">
                        <Tag color={workflow?.category === 'META' ? '#8b5cf6' : '#6366f1'}>{workflow?.category}</Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="协议">{workflow?.protocol}</Descriptions.Item>
                      {workflow?.method && (
                        <Descriptions.Item label="方法">
                          <Tag color={METHOD_COLOR_MAP[workflow.method] || '#6366f1'}>
                            {workflow?.method}
                          </Tag>
                        </Descriptions.Item>
                      )}
                      <Descriptions.Item label={workflow?.protocol === 'HTTP' || workflow?.protocol === 'HTTPS' ? '路径' : '绑定键'}>
                        <Text code>{workflow?.path}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="版本">v{workflow?.version}</Descriptions.Item>
                      <Descriptions.Item label="创建时间">
                        {workflow?.createdAt ? new Date(workflow.createdAt).toLocaleString() : '-'}
                      </Descriptions.Item>
                      <Descriptions.Item label="更新时间">
                        {workflow?.updatedAt ? new Date(workflow.updatedAt).toLocaleString() : '-'}
                      </Descriptions.Item>
                      <Descriptions.Item label="发布策略" span={2}>
                        {workflow?.publishTarget ? (
                          <Space>
                            <Tag color="#6366f1">{PUBLISH_TYPE_LABEL[workflow.publishTarget.type || 'ALL'] || workflow.publishTarget.type}</Tag>
                            {workflow.publishTarget.groups?.map((g: string) => <Tag key={g} color="#8b5cf6">{g}</Tag>)}
                            {workflow.publishTarget.instanceIds?.map((i: string) => <Tag key={i} color="#06b6d4">{i}</Tag>)}
                          </Space>
                        ) : (
                          <Tag>未配置</Tag>
                        )}
                      </Descriptions.Item>
                    </Descriptions>
                  </Col>
                  <Col span={8}>
                    <Row gutter={[12, 12]}>
                      <Col span={12}>
                        <Card size="small" style={{ borderRadius: 10, textAlign: 'center', border: '1px solid #f0f0f0' }}>
                          <Statistic title="节点数" value={workflow?.nodes?.length || 0} valueStyle={{ fontSize: 24 }} />
                        </Card>
                      </Col>
                      <Col span={12}>
                        <Card size="small" style={{ borderRadius: 10, textAlign: 'center', border: '1px solid #f0f0f0' }}>
                          <Statistic title="版本" value={workflow?.version || 1} valueStyle={{ fontSize: 24 }} />
                        </Card>
                      </Col>
                      <Col span={24}>
                        <Card size="small" style={{ borderRadius: 10, border: '1px solid #f0f0f0' }}>
                          <Text strong style={{ fontSize: 13 }}>事务配置</Text>
                          <div style={{ marginTop: 8 }}>
                            {workflow?.publishTarget?.type ? (
                              <Tag color="#6366f1">{PUBLISH_TYPE_LABEL[workflow.publishTarget.type || 'ALL'] || workflow.publishTarget.type}</Tag>
                            ) : (
                              <Text type="secondary" style={{ fontSize: 12 }}>未配置</Text>
                            )}
                          </div>
                        </Card>
                      </Col>
                    </Row>
                  </Col>
                </Row>
              ),
            },
            {
              key: 'executions',
              label: <span><PlayCircleOutlined /> 执行记录</span>,
              children: (
                <Table
                  rowKey="executionId"
                  size="small"
                  dataSource={execData?.list || []}
                  columns={[
                    {
                      title: 'Execution ID', dataIndex: 'executionId',
                      render: (v: string) => <Text copyable={{ text: v }} style={{ fontSize: 12 }}>{v?.slice(0, 16)}...</Text>,
                    },
                    {
                      title: '状态', dataIndex: 'status',
                      render: (v: string) => <Tag color={execStatusColor[v] || 'default'}>{v}</Tag>,
                    },
                    { title: '耗时', dataIndex: 'totalDurationMs', render: (v: number) => <Text strong>{v}ms</Text> },
                    { title: '开始时间', dataIndex: 'startTime', render: (v: string) => v ? new Date(v).toLocaleString() : '-' },
                    {
                      title: '操作',
                      render: (_: any, record: any) => (
                        <Button size="small" type="link" onClick={() => history.push(`/monitor/trace/${record.executionId}`)}>
                          查看链路
                        </Button>
                      ),
                    },
                  ]}
                  pagination={{ pageSize: 10, total: execData?.total || 0 }}
                />
              ),
            },
            {
              key: 'versions',
              label: <span><HistoryOutlined /> 版本历史</span>,
              children: (
                <Timeline
                  items={(versions || []).map((v: WorkflowDefinition) => ({
                    color: v.status === 'ACTIVE' ? 'green' : v.status === 'DEPRECATED' ? 'gray' : '#6366f1',
                    children: (
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <Space>
                          <Text strong>版本 {v.version}</Text>
                          <Tag color={statusMeta[v.status || 'DRAFT']?.color}>{statusMeta[v.status || 'DRAFT']?.label || v.status}</Tag>
                          <Text type="secondary" style={{ fontSize: 12 }}>{v.updatedAt}</Text>
                        </Space>
                        {v.status !== 'ACTIVE' && (
                          <Button
                            icon={<RollbackOutlined />}
                            size="small"
                            onClick={() => handleRollback(v.version!)}
                          >
                            回滚到此版本
                          </Button>
                        )}
                      </div>
                    ),
                  }))}
                />
              ),
            },
            {
              key: 'schema',
              label: <span><FileTextOutlined /> Schema</span>,
              children: (
                <Row gutter={16}>
                  <Col span={12}>
                    <Card
                      size="small"
                      title={
                        <Space>
                          <Text strong>入参 Schema</Text>
                          <Tag color="blue">{workflow?.inputSchemaFormat || 'json-schema'}</Tag>
                        </Space>
                      }
                      style={{ borderRadius: 10, border: '1px solid #f0f0f0' }}
                    >
                      {renderJson(workflow?.inputSchema)}
                    </Card>
                  </Col>
                  <Col span={12}>
                    <Card
                      size="small"
                      title={
                        <Space>
                          <Text strong>出参 Schema</Text>
                          <Tag color="blue">{workflow?.outputSchemaFormat || 'json-schema'}</Tag>
                        </Space>
                      }
                      style={{ borderRadius: 10, border: '1px solid #f0f0f0' }}
                    >
                      {renderJson(workflow?.outputSchema)}
                    </Card>
                  </Col>
                </Row>
              ),
            },
            {
              key: 'nodes',
              label: <span><PartitionOutlined /> 节点列表</span>,
              children: (
                <Table
                  rowKey="id"
                  size="small"
                  dataSource={workflow?.nodes || []}
                  columns={[
                    { title: 'ID', dataIndex: 'id', render: (v: string) => <Text code style={{ fontSize: 12 }}>{v}</Text> },
                    { title: '名称', dataIndex: 'name', render: (v: string) => <Text strong>{v}</Text> },
                    { title: '类型', dataIndex: 'type', render: (v: string) => <Tag color="#6366f1">{v}</Tag> },
                    {
                      title: '函数引用', dataIndex: 'functionRef',
                      render: (v: string) => v ? <Tag color="#8b5cf6">{v}</Tag> : <Text type="secondary">-</Text>,
                    },
                    {
                      title: '依赖', dataIndex: 'dependsOn',
                      render: (v: string[]) => v?.length ? v.map((d) => <Tag key={d}>{d}</Tag>) : <Text type="secondary">-</Text>,
                    },
                  ]}
                />
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
};

export default WorkflowDetail;
