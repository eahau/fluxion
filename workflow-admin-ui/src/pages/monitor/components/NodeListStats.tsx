import { Progress, Space, Tag, Typography } from 'antd';
import { ClockCircleOutlined } from '@ant-design/icons';
import type { NodeLatency } from '@/types/monitor';

const { Text } = Typography;

interface NodeListStatsProps {
  data?: NodeLatency[];
}

const NodeListStats: React.FC<NodeListStatsProps> = ({ data }) => {
  const items = data || [];
  const maxLatency = Math.max(...items.map((d) => d.avgDurationMs || 0), 1);

  if (items.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '40px 0', color: '#8c8c8c' }}>
        <ClockCircleOutlined style={{ fontSize: 28, color: '#d9d9d9' }} />
        <div style={{ marginTop: 8, fontSize: 13 }}>暂无耗时数据</div>
      </div>
    );
  }

  return (
    <div>
      {items.slice(0, 10).map((item, idx) => {
        const ms = item.avgDurationMs || 0;
        const pct = (ms / maxLatency) * 100;
        const color = ms < 50 ? '#52c41a' : ms < 150 ? '#f59e0b' : '#ff4d4f';
        return (
          <div key={`${item.nodeName}-${item.functionRef}`} style={{ marginBottom: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
              <Space size={6}>
                <Text style={{ fontSize: 12, fontWeight: 500 }}>{item.nodeName}</Text>
                <Tag style={{ fontSize: 10, borderRadius: 4, margin: 0 }}>{item.functionRef}</Tag>
              </Space>
              <Text strong style={{ fontSize: 12, color }}>{ms}ms</Text>
            </div>
            <Progress
              percent={pct}
              showInfo={false}
              strokeColor={color}
              size="small"
            />
          </div>
        );
      })}
    </div>
  );
};

export default NodeListStats;
