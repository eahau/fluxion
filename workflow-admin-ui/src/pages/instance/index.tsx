import { useRequest } from '@umijs/max';
import { Card, Col, Row, Statistic, Table, Tag, Radio, Space, Typography } from 'antd';
import { ClusterOutlined, CloudServerOutlined } from '@ant-design/icons';
import { useState, useMemo } from 'react';
import { getInstances, getInstanceGroups } from '@/services/instance';
import type { InstanceInfo, AppGroup } from '@/types/api';

const InstanceManager: React.FC = () => {
  const [selectedGroup, setSelectedGroup] = useState<string>('all');

  const { data: instances, loading } = useRequest(
    () => getInstances(selectedGroup === 'all' ? undefined : selectedGroup),
    {
      refreshDeps: [selectedGroup],
      formatResult: (res) => res,
    },
  );

  const { data: groups } = useRequest(getInstanceGroups, { formatResult: (res) => res });

  const groupMap = useMemo(() => {
    const map: Record<string, number> = {};
    groups?.forEach((g: AppGroup) => {
      if (g.name) map[g.name] = g.instanceCount || 0;
    });
    return map;
  }, [groups]);

  const totalInstances = useMemo(
    () => Object.values(groupMap).reduce((sum, count) => sum + count, 0),
    [groupMap],
  );

  const groupOptions = useMemo(
    () => [
      { label: '全部', value: 'all' },
      ...Object.keys(groupMap).map((name) => ({ label: name, value: name })),
    ],
    [groupMap],
  );

  const columns = [
    {
      title: '实例标识',
      dataIndex: 'instanceId',
      key: 'instanceId',
      ellipsis: { showTitle: true },
      render: (id?: string) => <Typography.Text copyable={{ text: id }}>{id}</Typography.Text>,
    },
    {
      title: '应用群',
      dataIndex: 'appGroup',
      key: 'appGroup',
      width: 180,
      render: (group?: string) =>
        group ? <Tag color="#6366f1">{group}</Tag> : <Tag>默认</Tag>,
    },
    {
      title: '地址',
      key: 'address',
      width: 200,
      render: (_: any, record: InstanceInfo) => (
        <Typography.Text code>
          {record.host}:{record.port}
        </Typography.Text>
      ),
    },
    {
      title: '元数据',
      dataIndex: 'metadata',
      key: 'metadata',
      ellipsis: { showTitle: true },
      render: (metadata?: Record<string, string>) => (
        <Space size={4}>
          {Object.entries(metadata || {}).map(([k, v]) => (
            <Tag size="small" key={k}>
              {k}: {v}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '最近心跳',
      dataIndex: 'lastHeartbeatMs',
      key: 'lastHeartbeatMs',
      width: 180,
      render: (ms?: number) =>
        ms ? new Date(ms).toLocaleString() : '-',
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: () => <Tag color="success">在线</Tag>,
    },
  ];

  return (
    <div>
      <Row gutter={16}>
        <Col span={8}>
          <Card>
            <Statistic
              title="存活实例总数"
              value={totalInstances}
              prefix={<CloudServerOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="应用群数量"
              value={Object.keys(groupMap).length}
              prefix={<ClusterOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="当前分组实例"
              value={instances?.length || 0}
              prefix={<CloudServerOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space>
            <ClusterOutlined style={{ fontSize: 18, color: '#6366f1' }} />
            <span>实例列表</span>
          </Space>
        }
        style={{ marginTop: 16 }}
        extra={
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            value={selectedGroup}
            onChange={(e) => setSelectedGroup(e.target.value)}
            options={groupOptions}
          />
        }
      >
        <Table
          rowKey="instanceId"
          size="small"
          columns={columns}
          dataSource={instances || []}
          loading={loading}
          pagination={false}
        />
      </Card>

    </div>
  );
};

export default InstanceManager;
