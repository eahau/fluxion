import { useRequest, history } from '@umijs/max';
import { Card, Col, List, Row, Statistic, Tag, Space, Typography, Progress, Avatar, Button, Badge } from 'antd';
import {
  DashboardOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  ClockCircleOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  FireOutlined,
  BugOutlined,
  HeartOutlined,
} from '@ant-design/icons';
import { getMetricsOverview, getMetricsTrend, getNodeLatencies, getErrorLogs } from '@/services/monitor';
import MetricsChart from './components/MetricsChart';
import NodeListStats from './components/NodeListStats';

const { Text, Title } = Typography;

const MonitorDashboard: React.FC = () => {
  const { data: overview } = useRequest(getMetricsOverview, { formatResult: (res) => res });
  const { data: trend } = useRequest(() => getMetricsTrend({ metric: 'requests', hours: 24 }), {
    formatResult: (res) => res,
  });
  const { data: latencies } = useRequest(getNodeLatencies, { formatResult: (res) => res });
  const { data: errors } = useRequest(
    () => getErrorLogs({ pageSize: 5 }),
    { formatResult: (res) => res },
  );

  const successRate = overview?.successRate || 99.9;
  const healthStatus = successRate >= 99.5 ? 'healthy' : successRate >= 95 ? 'degraded' : 'unhealthy';
  const healthMeta: Record<string, { color: string; label: string }> = {
    healthy: { color: '#52c41a', label: '健康' },
    degraded: { color: '#faad14', label: '降级' },
    unhealthy: { color: '#ff4d4f', label: '异常' },
  };

  return (
    <div>
      {/* 顶部健康度横幅 */}
      <div style={{
        background: `linear-gradient(135deg, ${healthStatus === 'healthy' ? '#f0fdf4' : healthStatus === 'degraded' ? '#fffbeb' : '#fef2f2'} 0%, #fff 100%)`,
        borderRadius: 12,
        padding: '20px 28px',
        marginBottom: 20,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        border: `1px solid ${healthStatus === 'healthy' ? '#bbf7d0' : healthStatus === 'degraded' ? '#fde68a' : '#fecaca'}`,
      }}>
        <Space size={16}>
          <div style={{
            width: 48, height: 48, borderRadius: 12,
            background: healthMeta[healthStatus].color,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <HeartOutlined style={{ fontSize: 22, color: '#fff' }} />
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>系统状态</Text>
            <div>
              <Text strong style={{ fontSize: 20 }}>
                {healthMeta[healthStatus].label}
              </Text>
              <Badge status={healthStatus === 'healthy' ? 'success' : healthStatus === 'degraded' ? 'warning' : 'error'} style={{ marginLeft: 8 }} />
            </div>
          </div>
        </Space>
        <Space size={32}>
          <div style={{ textAlign: 'center' }}>
            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>成功率</Text>
            <Text strong style={{ fontSize: 24, color: healthMeta[healthStatus].color }}>
              {successRate}%
            </Text>
          </div>
          <div style={{ textAlign: 'center' }}>
            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>P99 延迟</Text>
            <Text strong style={{ fontSize: 24 }}>{overview?.p99LatencyMs || 0}<Text type="secondary" style={{ fontSize: 14 }}>ms</Text></Text>
          </div>
          <div style={{ textAlign: 'center' }}>
            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>今日请求</Text>
            <Text strong style={{ fontSize: 24 }}>{(overview?.totalRequests || 0).toLocaleString()}</Text>
          </div>
        </Space>
      </div>

      {/* KPI 卡片行 */}
      <Row gutter={16} style={{ marginBottom: 20 }}>
        <Col span={6}>
          <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 36, height: 36, borderRadius: 8,
                background: 'linear-gradient(135deg, #6366f1, #818cf8)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <ThunderboltOutlined style={{ fontSize: 16, color: '#fff' }} />
              </div>
              <Text type="secondary" style={{ fontSize: 13 }}>请求量(今日)</Text>
            </div>
            <Statistic
              value={overview?.totalRequests || 0}
              valueStyle={{ fontSize: 28, fontWeight: 700 }}
            />
            <Progress percent={Math.min(100, ((overview?.totalRequests || 0) / 10000) * 100)} showInfo={false} strokeColor="#6366f1" size="small" style={{ marginTop: 8 }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 36, height: 36, borderRadius: 8,
                background: 'linear-gradient(135deg, #52c41a, #73d13d)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <CheckCircleOutlined style={{ fontSize: 16, color: '#fff' }} />
              </div>
              <Text type="secondary" style={{ fontSize: 13 }}>成功率</Text>
            </div>
            <Statistic
              value={successRate}
              suffix="%"
              precision={1}
              valueStyle={{ fontSize: 28, fontWeight: 700, color: successRate >= 99 ? '#52c41a' : successRate >= 95 ? '#faad14' : '#ff4d4f' }}
            />
            <Progress percent={successRate} showInfo={false} strokeColor={successRate >= 99 ? '#52c41a' : '#faad14'} size="small" style={{ marginTop: 8 }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 36, height: 36, borderRadius: 8,
                background: 'linear-gradient(135deg, #f59e0b, #fbbf24)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <ClockCircleOutlined style={{ fontSize: 16, color: '#fff' }} />
              </div>
              <Text type="secondary" style={{ fontSize: 13 }}>P99 延迟</Text>
            </div>
            <Statistic
              value={overview?.p99LatencyMs || 0}
              suffix="ms"
              valueStyle={{ fontSize: 28, fontWeight: 700 }}
            />
            <Progress
              percent={Math.min(100, ((overview?.p99LatencyMs || 0) / 500) * 100)}
              showInfo={false}
              strokeColor={(overview?.p99LatencyMs || 0) < 200 ? '#52c41a' : '#f59e0b'}
              size="small"
              style={{ marginTop: 8 }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 36, height: 36, borderRadius: 8,
                background: 'linear-gradient(135deg, #8b5cf6, #a78bfa)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <FireOutlined style={{ fontSize: 16, color: '#fff' }} />
              </div>
              <Text type="secondary" style={{ fontSize: 13 }}>活跃工作流</Text>
            </div>
            <Statistic
              value={overview?.activeWorkflows || 0}
              valueStyle={{ fontSize: 28, fontWeight: 700 }}
            />
            <Progress
              percent={Math.min(100, ((overview?.activeWorkflows || 0) / 50) * 100)}
              showInfo={false}
              strokeColor="#8b5cf6"
              size="small"
              style={{ marginTop: 8 }}
            />
          </Card>
        </Col>
      </Row>

      {/* 图表行 */}
      <Row gutter={16} style={{ marginBottom: 20 }}>
        <Col span={14}>
          <Card title={<Space><DashboardOutlined style={{ color: '#6366f1' }} /><span>请求量趋势 (24h)</span></Space>}
            style={{ borderRadius: 12, border: 'none' }} className="home-stat-card"
          >
            <MetricsChart trend={trend} />
          </Card>
        </Col>
        <Col span={10}>
          <Card title={<Space><ClockCircleOutlined style={{ color: '#f59e0b' }} /><span>节点耗时 Top10</span></Space>}
            style={{ borderRadius: 12, border: 'none' }} className="home-stat-card"
          >
            <NodeListStats data={latencies} />
          </Card>
        </Col>
      </Row>

      {/* 最近错误 */}
      <Card
        title={<Space><BugOutlined style={{ color: '#ff4d4f' }} /><span>最近错误</span></Space>}
        extra={
          <Tag color="red" style={{ borderRadius: 4 }}>
            {errors?.list?.length || 0} 条
          </Tag>
        }
        style={{ borderRadius: 12, border: 'none' }}
        className="home-stat-card"
      >
        {errors?.list?.length ? (
          <List
            dataSource={errors.list}
            renderItem={(item: any) => (
              <List.Item
                style={{ padding: '12px 0' }}
                extra={
                  <Button type="link" size="small" onClick={() => history.push('/monitor')}>
                    详情
                  </Button>
                }
              >
                <List.Item.Meta
                  avatar={
                    <Avatar size="small" style={{ background: '#ff4d4f', marginTop: 4 }}>
                      <BugOutlined />
                    </Avatar>
                  }
                  title={
                    <Space size={8}>
                      <Text strong style={{ fontSize: 13 }}>{item.workflowName}</Text>
                      <Tag color="orange" style={{ borderRadius: 4 }}>{item.nodeName}</Tag>
                      <Tag color="red" style={{ borderRadius: 4 }}>{item.errorType}</Tag>
                    </Space>
                  }
                  description={
                    <Space direction="vertical" size={2}>
                      <Text type="secondary" style={{ fontSize: 12 }}>{item.message}</Text>
                      <Text type="secondary" style={{ fontSize: 11 }}>{item.time}</Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        ) : (
          <div style={{ textAlign: 'center', padding: '32px 0', color: '#8c8c8c' }}>
            <CheckCircleOutlined style={{ fontSize: 32, color: '#d9d9d9' }} />
            <div style={{ marginTop: 8 }}>暂无错误记录</div>
          </div>
        )}
      </Card>
    </div>
  );
};

export default MonitorDashboard;
