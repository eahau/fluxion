import { history, useRequest } from '@umijs/max';
import {
  Card, Col, Row, Statistic, Tag, Space, Typography, Button, Progress, List, Avatar, Divider, Tooltip,
} from 'antd';
import {
  PartitionOutlined,
  FunctionOutlined,
  FileTextOutlined,
  ClusterOutlined,
  DashboardOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  ThunderboltOutlined,
  ArrowUpOutlined,
  ArrowRightOutlined,
  CloudServerOutlined,
  PlayCircleOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { getMetricsOverview } from '@/services/monitor';
import { getWorkflows } from '@/services/workflow';
import { getFunctions } from '@/services/function';
import { getSchemas } from '@/services/schema';
import { listRunningExecutions } from '@/services/execution';
import { getInstanceGroups } from '@/services/instance';
import { METHOD_COLOR_MAP } from '@/constants/protocol';

const { Title, Text, Paragraph } = Typography;

const statusColorMap: Record<string, string> = {
  SUCCESS: '#52c41a',
  COMPLETED: '#52c41a',
  FAILED: '#ff4d4f',
  RUNNING: '#6366f1',
  DRAFT: '#faad14',
  ACTIVE: '#52c41a',
};

const HomePage: React.FC = () => {
  const { data: overview } = useRequest(getMetricsOverview, { formatResult: (res) => res });
  const { data: wfData } = useRequest(() => getWorkflows({ pageSize: 6 }), { formatResult: (res) => res });
  const { data: fnData } = useRequest(() => getFunctions({ page: 0, pageSize: 1000 }), { formatResult: (res) => res });
  const { data: schemaData } = useRequest(() => getSchemas({ page: 0, pageSize: 20 }), { formatResult: (res) => res });
  const { data: runningList } = useRequest(listRunningExecutions, { pollingInterval: 5000 });
  const { data: groups } = useRequest(getInstanceGroups, { formatResult: (res) => res });

  const workflows = wfData?.list || [];
  const functions = fnData?.list || [];
  const schemas = schemaData?.list || [];
  const totalInstances = groups?.reduce((sum: number, g: any) => sum + (g.instanceCount || 0), 0) || 0;

  const quickActions = [
    { label: '新建工作流', icon: <PlusOutlined />, path: '/workflow/designer', color: '#6366f1' },
    { label: '注册函数', icon: <FunctionOutlined />, path: '/function/editor', color: '#8b5cf6' },
    { label: '新建 Schema', icon: <FileTextOutlined />, path: '/schema/editor', color: '#06b6d4' },
    { label: '查看监控', icon: <DashboardOutlined />, path: '/monitor', color: '#f59e0b' },
  ];

  return (
    <div>
      {/* 欢迎横幅 */}
      <div className="home-hero" style={{
        background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #a78bfa 100%)',
        borderRadius: 16,
        padding: '32px 40px',
        marginBottom: 24,
        color: '#fff',
        position: 'relative',
        overflow: 'hidden',
      }}>
        {/* 装饰圆 */}
        <div style={{
          position: 'absolute', right: -40, top: -40,
          width: 200, height: 200, borderRadius: '50%',
          background: 'rgba(255,255,255,0.08)',
        }} />
        <div style={{
          position: 'absolute', right: 80, bottom: -20,
          width: 120, height: 120, borderRadius: '50%',
          background: 'rgba(255,255,255,0.05)',
        }} />

        <Title level={3} style={{ color: '#fff', margin: 0 }}>
          <PartitionOutlined style={{ marginRight: 12 }} />
          Fluxion 控制台
        </Title>
        <Paragraph style={{ color: 'rgba(255,255,255,0.85)', margin: '8px 0 20px', fontSize: 14 }}>
          管理工作流编排、函数注册、Schema 契约，监控运行时状态
        </Paragraph>

        <Space size={12}>
          {quickActions.map((action) => (
            <Button
              key={action.path}
              icon={action.icon}
              onClick={() => history.push(action.path)}
              style={{
                borderRadius: 8,
                background: 'rgba(255,255,255,0.15)',
                border: '1px solid rgba(255,255,255,0.25)',
                color: '#fff',
                backdropFilter: 'blur(4px)',
              }}
            >
              {action.label}
            </Button>
          ))}
        </Space>
      </div>

      {/* KPI 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card className="home-stat-card" style={{ borderRadius: 12, border: 'none' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{
                width: 48, height: 48, borderRadius: 12,
                background: 'linear-gradient(135deg, #6366f1, #818cf8)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <PartitionOutlined style={{ fontSize: 22, color: '#fff' }} />
              </div>
              <div>
                <Statistic
                  title={<Text type="secondary" style={{ fontSize: 13 }}>工作流总数</Text>}
                  value={workflows.length || overview?.activeWorkflows || 0}
                  valueStyle={{ fontSize: 28, fontWeight: 700, color: '#1e293b' }}
                />
              </div>
            </div>
          </Card>
        </Col>
        <Col span={6}>
          <Card className="home-stat-card" style={{ borderRadius: 12, border: 'none' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{
                width: 48, height: 48, borderRadius: 12,
                background: 'linear-gradient(135deg, #8b5cf6, #a78bfa)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <FunctionOutlined style={{ fontSize: 22, color: '#fff' }} />
              </div>
              <div>
                <Statistic
                  title={<Text type="secondary" style={{ fontSize: 13 }}>注册函数</Text>}
                  value={functions.length}
                  valueStyle={{ fontSize: 28, fontWeight: 700, color: '#1e293b' }}
                />
              </div>
            </div>
          </Card>
        </Col>
        <Col span={6}>
          <Card className="home-stat-card" style={{ borderRadius: 12, border: 'none' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{
                width: 48, height: 48, borderRadius: 12,
                background: 'linear-gradient(135deg, #06b6d4, #67e8f9)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <CheckCircleOutlined style={{ fontSize: 22, color: '#fff' }} />
              </div>
              <div>
                <Statistic
                  title={<Text type="secondary" style={{ fontSize: 13 }}>成功率</Text>}
                  value={overview?.successRate || 99.9}
                  suffix="%"
                  precision={1}
                  valueStyle={{ fontSize: 28, fontWeight: 700, color: '#1e293b' }}
                />
              </div>
            </div>
          </Card>
        </Col>
        <Col span={6}>
          <Card className="home-stat-card" style={{ borderRadius: 12, border: 'none' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{
                width: 48, height: 48, borderRadius: 12,
                background: 'linear-gradient(135deg, #f59e0b, #fbbf24)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <CloudServerOutlined style={{ fontSize: 22, color: '#fff' }} />
              </div>
              <div>
                <Statistic
                  title={<Text type="secondary" style={{ fontSize: 13 }}>运行实例</Text>}
                  value={totalInstances}
                  valueStyle={{ fontSize: 28, fontWeight: 700, color: '#1e293b' }}
                />
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      {/* 第二行：运行时指标 */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <Text strong style={{ fontSize: 15 }}>运行中执行</Text>
              <Tag color="processing" icon={<PlayCircleOutlined />}>LIVE</Tag>
            </div>
            {runningList && runningList.length > 0 ? (
              <List
                size="small"
                dataSource={runningList.slice(0, 4)}
                renderItem={(item: any) => (
                  <List.Item
                    style={{ padding: '8px 0', borderBottom: '1px solid #f5f5f5' }}
                    extra={
                      <Button
                        type="link"
                        size="small"
                        onClick={() => history.push(`/monitor/trace/${item.executionId}`)}
                      >
                        查看
                      </Button>
                    }
                  >
                    <List.Item.Meta
                      avatar={<Avatar size="small" style={{ background: '#6366f1' }}>W</Avatar>}
                      title={<Text style={{ fontSize: 13 }}>{item.workflowName || item.workflowId}</Text>}
                      description={<Text type="secondary" style={{ fontSize: 11 }}>{item.executionId?.slice(0, 12)}...</Text>}
                    />
                  </List.Item>
                )}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: '20px 0', color: '#8c8c8c' }}>
                <CheckCircleOutlined style={{ fontSize: 28, color: '#d9d9d9' }} />
                <div style={{ marginTop: 8, fontSize: 13 }}>暂无运行中的执行</div>
              </div>
            )}
          </Card>
        </Col>

        <Col span={8}>
          <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <Text strong style={{ fontSize: 15 }}>系统健康</Text>
              <Tag color={overview?.p99LatencyMs && overview.p99LatencyMs < 200 ? 'success' : 'warning'}>
                P99: {overview?.p99LatencyMs || 0}ms
              </Tag>
            </div>
            <div style={{ marginBottom: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <Text style={{ fontSize: 12, color: '#8c8c8c' }}>请求量(今日)</Text>
                <Text strong style={{ fontSize: 12 }}>{overview?.totalRequests || 0}</Text>
              </div>
              <Progress
                percent={Math.min(100, ((overview?.totalRequests || 0) / 10000) * 100)}
                showInfo={false}
                strokeColor={{ '0%': '#6366f1', '100%': '#8b5cf6' }}
                size="small"
              />
            </div>
            <div style={{ marginBottom: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <Text style={{ fontSize: 12, color: '#8c8c8c' }}>成功率</Text>
                <Text strong style={{ fontSize: 12 }}>{overview?.successRate || 99.9}%</Text>
              </div>
              <Progress
                percent={overview?.successRate || 99.9}
                showInfo={false}
                strokeColor={{ '0%': '#52c41a', '100%': '#73d13d' }}
                size="small"
              />
            </div>
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <Text style={{ fontSize: 12, color: '#8c8c8c' }}>活跃工作流</Text>
                <Text strong style={{ fontSize: 12 }}>{overview?.activeWorkflows || 0}</Text>
              </div>
              <Progress
                percent={Math.min(100, ((overview?.activeWorkflows || 0) / 50) * 100)}
                showInfo={false}
                strokeColor={{ '0%': '#f59e0b', '100%': '#fbbf24' }}
                size="small"
              />
            </div>
          </Card>
        </Col>

        <Col span={8}>
          <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <Text strong style={{ fontSize: 15 }}>快速导航</Text>
            </div>
            <Row gutter={[8, 8]}>
              {[
                { label: '工作流', icon: <PartitionOutlined />, path: '/workflow', color: '#6366f1', count: workflows.length },
                { label: '函数库', icon: <FunctionOutlined />, path: '/function', color: '#8b5cf6', count: functions.length },
                { label: 'Schema', icon: <FileTextOutlined />, path: '/schema', color: '#06b6d4', count: schemas.length },
                { label: '执行历史', icon: <ClockCircleOutlined />, path: '/workflow/execution', color: '#f59e0b', count: null },
                { label: '链路追踪', icon: <DashboardOutlined />, path: '/monitor', color: '#ef4444', count: null },
                { label: '实例管理', icon: <ClusterOutlined />, path: '/instance', color: '#10b981', count: totalInstances },
              ].map((item) => (
                <Col span={8} key={item.path}>
                  <div
                    onClick={() => history.push(item.path)}
                    style={{
                      padding: '12px 8px',
                      borderRadius: 10,
                      textAlign: 'center',
                      cursor: 'pointer',
                      border: '1px solid #f0f0f0',
                      transition: 'all 0.2s',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.borderColor = item.color;
                      e.currentTarget.style.background = `${item.color}08`;
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.borderColor = '#f0f0f0';
                      e.currentTarget.style.background = 'transparent';
                    }}
                  >
                    <div style={{ color: item.color, fontSize: 20, marginBottom: 4 }}>{item.icon}</div>
                    <div style={{ fontSize: 12, fontWeight: 500 }}>{item.label}</div>
                    {item.count !== null && (
                      <div style={{ fontSize: 11, color: '#8c8c8c' }}>{item.count}</div>
                    )}
                  </div>
                </Col>
              ))}
            </Row>
          </Card>
        </Col>
      </Row>

      {/* 第三行：最近工作流 */}
      <Card
        style={{ borderRadius: 12, border: 'none' }}
        className="home-stat-card"
        title={
          <Space>
            <PartitionOutlined style={{ color: '#6366f1' }} />
            <span>最近工作流</span>
          </Space>
        }
        extra={
          <Button type="link" onClick={() => history.push('/workflow')}>
            查看全部 <ArrowRightOutlined />
          </Button>
        }
      >
        <Row gutter={[16, 12]}>
          {workflows.slice(0, 4).map((wf: any) => {
            const status = wf.status || 'DRAFT';
            const statusMeta: Record<string, { color: string; label: string }> = {
              ACTIVE: { color: '#52c41a', label: '已发布' },
              DRAFT: { color: '#faad14', label: '草稿' },
              DEPRECATED: { color: '#8c8c8c', label: '已下线' },
            };
            const s = statusMeta[status] || statusMeta.DRAFT;
            return (
              <Col span={6} key={wf.id}>
                <Card
                  hoverable
                  size="small"
                  onClick={() => history.push(`/workflow/detail/${wf.id}`)}
                  style={{ borderRadius: 10, border: '1px solid #f0f0f0' }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <PartitionOutlined style={{ color: '#6366f1', fontSize: 16 }} />
                    <Text strong ellipsis style={{ flex: 1, fontSize: 13 }}>{wf.name}</Text>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Tag color={s.color} style={{ margin: 0, fontSize: 11, borderRadius: 4 }}>{s.label}</Tag>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {wf.nodes?.length || 0} 节点
                    </Text>
                  </div>
                  <div style={{ marginTop: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
                    {wf.method && (
                      <Tag color={METHOD_COLOR_MAP[wf.method] || '#6366f1'} style={{ margin: 0, fontSize: 10, borderRadius: 4, fontWeight: 600, lineHeight: '18px' }}>
                        {wf.method}
                      </Tag>
                    )}
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {wf.protocol} {wf.path || ''}
                    </Text>
                  </div>
                </Card>
              </Col>
            );
          })}
        </Row>
      </Card>
    </div>
  );
};

export default HomePage;
