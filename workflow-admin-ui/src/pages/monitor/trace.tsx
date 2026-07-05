import { useParams } from '@umijs/max';
import { useRequest } from '@umijs/max';
import { Button, Card, Input, Space } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { getTrace } from '@/services/monitor';
import TraceTimeline from './components/TraceTimeline';

const TraceDetailPage: React.FC = () => {
  const { executionId: paramId } = useParams<{ executionId?: string }>();
  const [executionId, setExecutionId] = useState(paramId || '');
  const { data: trace } = useRequest(() => getTrace(executionId), { ready: !!executionId });

  return (
    <Card
      title="链路追踪详情"
      extra={
        <Space>
          <Input
            placeholder="输入 Execution ID"
            value={executionId}
            onChange={(e) => setExecutionId(e.target.value)}
            style={{ width: 240 }}
          />
          <Button icon={<SearchOutlined />} onClick={() => {}}>
            查询
          </Button>
        </Space>
      }
    >
      {trace && <TraceTimeline trace={trace} />}
    </Card>
  );
};

export default TraceDetailPage;
