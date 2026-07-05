import { history, useParams, useRequest } from '@umijs/max';
import { Button, Card, Descriptions, Modal, Tag, Timeline, message } from 'antd';
import { RollbackOutlined, EyeOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { getSchemaVersions, getSchema } from '@/services/schema';
import { useClickDebounce } from '@/utils/useClickDebounce';
import type { SchemaDefinition } from '@/types/schema';
import Editor from '@monaco-editor/react';

const FORMAT_LABEL: Record<string, string> = {
  'json-schema': 'JSON Schema',
  protobuf: 'Protobuf',
  avro: 'Avro',
};

const SchemaVersions: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { data: current } = useRequest(() => getSchema(id!), { ready: !!id });
  const { data: versions, refresh } = useRequest(() => getSchemaVersions(id!), { ready: !!id });
  const [viewVersion, setViewVersion] = useState<SchemaDefinition | null>(null);

  const handleRollback = useClickDebounce((version: SchemaDefinition) => {
    Modal.confirm({
      title: '确认回滚',
      content: `确定回滚到版本 ${(version as any).version || '-'} 吗？`,
      onOk: async () => {
        message.info('回滚功能需后端支持，当前仅作展示');
        refresh();
      },
    });
  });

  const viewSchemaJson = useMemo(() => {
    if (!viewVersion) return '';
    try {
      const schema = (viewVersion as any).schemaJson
        ? JSON.parse((viewVersion as any).schemaJson)
        : {};
      return JSON.stringify(schema, null, 2);
    } catch {
      return (viewVersion as any).schemaJson || '{}';
    }
  }, [viewVersion]);

  return (
    <Card
      title={`Schema 版本历史：${current?.schemaName || id}`}
      extra={
        <Button onClick={() => history.push('/schema')}>返回列表</Button>
      }
    >
      <Descriptions bordered column={2} style={{ marginBottom: 24 }}>
        <Descriptions.Item label="名称">{current?.schemaName}</Descriptions.Item>
        <Descriptions.Item label="类型">
          <Tag>{current?.schemaType}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="格式">
          <Tag color="blue">{FORMAT_LABEL[(current as any)?.schemaFormat] || (current as any)?.schemaFormat || 'JSON Schema'}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="描述">{(current as any)?.description || '-'}</Descriptions.Item>
      </Descriptions>

      <Timeline>
        {(versions || []).map((v: SchemaDefinition, idx: number) => (
          <Timeline.Item key={idx}>
            <Card size="small" title={`版本 ${(v as any).version || idx + 1}`}>
              <div style={{ marginBottom: 12 }}>
                <Tag>{v.schemaType || (v as any).type}</Tag>
                <Tag color="blue" style={{ marginLeft: 8 }}>
                  {FORMAT_LABEL[(v as any).schemaFormat] || (v as any).schemaFormat || 'JSON Schema'}
                </Tag>
                <span style={{ marginLeft: 12, color: '#888' }}>
                  {(v as any).updatedAt || (v as any).createdAt || '-'}
                </span>
              </div>
              <Button
                icon={<EyeOutlined />}
                size="small"
                onClick={() => setViewVersion(v)}
                style={{ marginRight: 8 }}
              >
                查看
              </Button>
              <Button
                icon={<RollbackOutlined />}
                size="small"
                onClick={() => handleRollback(v)}
              >
                回滚
              </Button>
            </Card>
          </Timeline.Item>
        ))}
      </Timeline>

      <Modal
        title="Schema 版本详情"
        open={!!viewVersion}
        onCancel={() => setViewVersion(null)}
        width={700}
        footer={null}
      >
        <Editor
          height={400}
          language="json"
          value={viewSchemaJson}
          options={{ readOnly: true, minimap: { enabled: false } }}
        />
      </Modal>
    </Card>
  );
};

export default SchemaVersions;
