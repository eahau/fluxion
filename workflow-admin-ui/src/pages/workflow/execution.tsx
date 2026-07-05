import { history, useParams } from '@umijs/max';
import { useRequest } from '@umijs/max';
import {
  Button, Card, Descriptions, Drawer, Form, Input, Modal, Select, Space, Table, Tag, Typography, message, Badge,
} from 'antd';
import { useState } from 'react';
import { ReloadOutlined, SendOutlined, SearchOutlined, PlayCircleOutlined, EyeOutlined } from '@ant-design/icons';
import { listExecutions, rerunExecution, sendSignal, queryExecution, listRunningExecutions } from '@/services/execution';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { Text } = Typography;

const execStatusMeta: Record<string, { color: string; label: string }> = {
  SUCCESS: { color: 'success', label: '成功' },
  COMPLETED: { color: 'success', label: '完成' },
  FAILED: { color: 'error', label: '失败' },
  RUNNING: { color: 'processing', label: '运行中' },
  CANCELLED: { color: 'warning', label: '已取消' },
  TIMEOUT: { color: 'warning', label: '超时' },
};

const ExecutionHistoryPage: React.FC = () => {
  const { workflowId: paramWorkflowId } = useParams<{ workflowId?: string }>();
  const [workflowId, setWorkflowId] = useState(paramWorkflowId || '');
  const [status, setStatus] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 15;

  const { data, loading, refresh } = useRequest(
    () => listExecutions({ workflowId, status, keyword, page, pageSize }),
    { refreshDeps: [workflowId, status, keyword, page] },
  );

  const [signalModal, setSignalModal] = useState<{ visible: boolean; executionId: string }>({ visible: false, executionId: '' });
  const [queryDrawer, setQueryDrawer] = useState<{ visible: boolean; executionId: string; data: any }>({ visible: false, executionId: '', data: null });
  const [signalForm] = Form.useForm();

  const { data: runningList } = useRequest(listRunningExecutions, {
    pollingInterval: 5000,
  });

  const handleSendSignal = useClickDebounce(async () => {
    try {
      const values = await signalForm.validateFields();
      let payload = values.payload;
      if (payload && typeof payload === 'string') {
        try { payload = JSON.parse(payload); } catch { /* keep as string */ }
      }
      await sendSignal(signalModal.executionId, values.signalName, payload);
      message.success('信号发送成功');
      setSignalModal({ visible: false, executionId: '' });
      signalForm.resetFields();
    } catch (e: any) {
      message.error(e?.message || '信号发送失败');
    }
  });

  const handleQuery = useClickDebounce(async (executionId: string) => {
    try {
      const data = await queryExecution(executionId);
      setQueryDrawer({ visible: true, executionId, data });
    } catch (e: any) {
      message.error('查询失败');
    }
  });

  const handleRerun = useClickDebounce(async (executionId: string) => {
    Modal.confirm({
      title: '重放执行',
      content: `确定要重放执行 ${executionId} 吗？`,
      onOk: async () => {
        try {
          const res = await rerunExecution(executionId);
          message.success('重放成功');
          history.push(`/monitor/trace/${res.executionId || executionId}`);
          refresh();
        } catch (e: any) {
          console.error('重放失败:', e);
        }
      },
    });
  });

  const columns = [
    {
      title: 'Execution ID',
      dataIndex: 'executionId',
      render: (v: string) => <Text copyable={{ text: v }} style={{ fontSize: 12 }}>{v?.slice(0, 20)}...</Text>,
    },
    {
      title: '工作流',
      dataIndex: 'workflowName',
      render: (v: string) => <Text strong>{v}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const meta = execStatusMeta[v] || { color: 'default', label: v };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '耗时',
      dataIndex: 'totalDurationMs',
      width: 100,
      render: (v: number) => {
        const color = v < 100 ? '#52c41a' : v < 500 ? '#f59e0b' : '#ff4d4f';
        return <Text strong style={{ color }}>{v}ms</Text>;
      },
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 180,
      render: (v: string) => v ? new Date(v).toLocaleString() : '-',
    },
    {
      title: '操作',
      width: 280,
      render: (_: any, record: any) => (
        <Space size={4}>
          <Button size="small" icon={<SendOutlined />} onClick={() => setSignalModal({ visible: true, executionId: record.executionId })}>
            信号
          </Button>
          <Button size="small" icon={<SearchOutlined />} onClick={() => handleQuery(record.executionId)}>
            查询
          </Button>
          <Button size="small" icon={<ReloadOutlined />} onClick={() => handleRerun(record.executionId)}>
            重放
          </Button>
          <Button size="small" type="primary" ghost icon={<EyeOutlined />} onClick={() => history.push(`/monitor/trace/${record.executionId}`)}>
            链路
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* 顶部栏 */}
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space>
            <PlayCircleOutlined style={{ fontSize: 20, color: '#6366f1' }} />
            <Text strong style={{ fontSize: 16 }}>执行历史</Text>
            <Badge count={runningList?.length || 0} style={{ backgroundColor: '#6366f1' }} overflowCount={999}>
              <Tag color="processing" style={{ borderRadius: 4, marginLeft: 8 }}>运行中</Tag>
            </Badge>
          </Space>
          <Space>
            <Input
              placeholder="工作流 ID"
              value={workflowId}
              onChange={(e) => setWorkflowId(e.target.value)}
              style={{ width: 180 }}
            />
            <Select
              placeholder="状态"
              value={status}
              onChange={setStatus}
              allowClear
              options={[
                { value: 'SUCCESS', label: '成功' },
                { value: 'FAILED', label: '失败' },
                { value: 'RUNNING', label: '运行中' },
                { value: 'CANCELLED', label: '已取消' },
              ]}
              style={{ width: 120 }}
            />
            <Input.Search
              placeholder="关键字"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onSearch={() => setPage(1)}
              style={{ width: 180 }}
            />
          </Space>
        </div>
      </Card>

      {/* 表格 */}
      <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
        <Table
          loading={loading}
          dataSource={data?.list || []}
          rowKey="executionId"
          size="small"
          columns={columns}
          pagination={{
            current: page,
            pageSize,
            total: data?.total || 0,
            showTotal: (total) => `共 ${total} 条`,
            onChange: setPage,
          }}
        />
      </Card>

      {/* 发送信号弹窗 */}
      <Modal
        title={`发送信号 — ${signalModal.executionId?.slice(0, 16)}...`}
        open={signalModal.visible}
        onOk={handleSendSignal}
        onCancel={() => { setSignalModal({ visible: false, executionId: '' }); signalForm.resetFields(); }}
      >
        <Form form={signalForm} layout="vertical">
          <Form.Item name="signalName" label="信号名称" rules={[{ required: true, message: '请输入信号名称' }]} style={{ marginBottom: 12 }}>
            <Input placeholder="如: approve, reject, callback" />
          </Form.Item>
          <Form.Item name="payload" label="信号数据 (JSON)" style={{ marginBottom: 12 }}>
            <Input.TextArea rows={4} placeholder='{"approved": true, "comment": "同意"}' />
          </Form.Item>
        </Form>
      </Modal>

      {/* 执行状态查询抽屉 */}
      <Drawer
        title={`执行状态 — ${queryDrawer.executionId?.slice(0, 16)}...`}
        open={queryDrawer.visible}
        onClose={() => setQueryDrawer({ visible: false, executionId: '', data: null })}
        width={520}
      >
        {queryDrawer.data && (
          <Descriptions column={1} bordered size="small" labelStyle={{ fontWeight: 500, background: '#f8fafc' }}>
            <Descriptions.Item label="状态">
              <Tag color={
                queryDrawer.data.status === 'RUNNING' ? 'processing' :
                queryDrawer.data.status === 'COMPLETED' ? 'success' : 'error'
              }>
                {queryDrawer.data.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="工作流">{queryDrawer.data.workflowName || queryDrawer.data.workflowId}</Descriptions.Item>
            <Descriptions.Item label="版本">{queryDrawer.data.workflowVersion}</Descriptions.Item>
            <Descriptions.Item label="开始时间">
              {new Date(queryDrawer.data.startTime).toLocaleString()}
            </Descriptions.Item>
            {queryDrawer.data.status === 'RUNNING' && (
              <>
                <Descriptions.Item label="等待中的信号">
                  {queryDrawer.data.waitingSignals?.length
                    ? queryDrawer.data.waitingSignals.map((s: string) => <Tag key={s} color="orange">{s}</Tag>)
                    : <Tag>无</Tag>
                  }
                </Descriptions.Item>
                <Descriptions.Item label="已缓冲信号">
                  {queryDrawer.data.bufferedSignals?.length
                    ? queryDrawer.data.bufferedSignals.map((s: string) => <Tag key={s} color="green">{s}</Tag>)
                    : <Tag>无</Tag>
                  }
                </Descriptions.Item>
                <Descriptions.Item label="节点列表">
                  <Space wrap>
                    {queryDrawer.data.nodeIds?.map((id: string) => <Tag key={id}>{id}</Tag>)}
                  </Space>
                </Descriptions.Item>
              </>
            )}
            {queryDrawer.data.status !== 'RUNNING' && queryDrawer.data.completedNodeIds && (
              <Descriptions.Item label="已完成节点">
                <Space wrap>
                  {queryDrawer.data.completedNodeIds.map((id: string) => <Tag key={id} color="green">{id}</Tag>)}
                </Space>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="输入参数">
              <pre style={{ margin: 0, fontSize: 12, maxHeight: 200, overflow: 'auto', background: '#f8fafc', padding: 8, borderRadius: 6 }}>
                {JSON.stringify(queryDrawer.data.inputs, null, 2)}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default ExecutionHistoryPage;
