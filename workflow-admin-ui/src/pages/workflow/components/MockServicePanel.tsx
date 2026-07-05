import { useState } from 'react';
import { Button, Card, Form, Input, InputNumber, List, Select, Switch, message, Space, Tag } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import JsonEditor from '@/components/JsonEditor';
import type { MockRule, MockCondition, FieldMatcher } from '@/types/api';
import { useClickDebounce } from '@/utils/useClickDebounce';

interface MockServicePanelProps {
  configs: MockRule[];
  onChange: (configs: MockRule[]) => void;
}

type ResponseType = 'static' | 'template';

const MockServicePanel: React.FC<MockServicePanelProps> = ({ configs, onChange }) => {
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<MockRule | null>(null);
  const [responseType, setResponseType] = useState<ResponseType>('static');
  const [responseBody, setResponseBody] = useState<any>({});
  const [fieldMatchers, setFieldMatchers] = useState<{ path: string; exact: string }[]>([]);

  const resetForm = useClickDebounce(() => {
    form.resetFields();
    setResponseType('static');
    setResponseBody({});
    setFieldMatchers([]);
    setEditing(null);
  });

  const handleSave = useClickDebounce(async () => {
    const values = await form.validateFields();

    const condition: MockCondition | undefined =
      values.conditionExpression || fieldMatchers.length > 0
        ? {
            expression: values.conditionExpression || undefined,
            fieldMatchers:
              fieldMatchers.length > 0
                ? Object.fromEntries(
                    fieldMatchers
                      .filter((m) => m.path.trim() && m.exact.trim())
                      .map((m) => [
                        m.path.trim(),
                        { exact: m.exact.split(',').map((s) => s.trim()).filter(Boolean) } as FieldMatcher,
                      ])
                  )
                : undefined,
          }
        : undefined;

    const newConfig: MockRule = {
      id: editing?.id || `${Date.now()}`,
      functionRef: values.functionRef,
      enabled: values.enabled ?? true,
      delayMs: values.delayMs ?? 0,
      condition,
      response: responseType === 'static' ? responseBody : undefined,
      responseTemplate: responseType === 'template' ? values.responseTemplate : undefined,
    };

    if (editing) {
      onChange(configs.map((c) => (c.id === editing.id ? newConfig : c)));
    } else {
      onChange([...configs, newConfig]);
    }
    resetForm();
    message.success('保存成功');
  });

  const handleEdit = (config: MockRule) => {
    setEditing(config);
    form.setFieldsValue({
      functionRef: config.functionRef,
      enabled: config.enabled,
      delayMs: config.delayMs,
      conditionExpression: config.condition?.expression,
      responseTemplate: config.responseTemplate,
    });

    if (config.responseTemplate) {
      setResponseType('template');
      setResponseBody({});
    } else {
      setResponseType('static');
      setResponseBody(config.response ?? {});
    }

    const matchers = config.condition?.fieldMatchers || {};
    setFieldMatchers(
      Object.entries(matchers).map(([path, matcher]) => ({
        path,
        exact: (matcher?.exact || []).join(', '),
      }))
    );
  };

  const handleDelete = useClickDebounce((id?: string) => {
    if (!id) return;
    onChange(configs.filter((c) => c.id !== id));
  });

  const handleToggle = (config: MockRule) => {
    onChange(configs.map((c) => (c.id === config.id ? { ...c, enabled: !c.enabled } : c)));
  };

  return (
    <div style={{ display: 'flex', gap: 16 }}>
      <Card title="Mock 规则列表" size="small" style={{ flex: 1, minWidth: 280 }}>
        <List
          size="small"
          dataSource={configs}
          renderItem={(config) => (
            <List.Item
              actions={[
                <Switch
                  size="small"
                  checked={config.enabled}
                  onChange={() => handleToggle(config)}
                />,
                <Button size="small" onClick={() => handleEdit(config)}>
                  编辑
                </Button>,
                <Button
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => handleDelete(config.id)}
                >
                  删除
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    {config.functionRef}
                    {config.condition?.expression && <Tag size="small">条件</Tag>}
                    {config.condition?.fieldMatchers && Object.keys(config.condition.fieldMatchers).length > 0 && (
                      <Tag size="small">字段匹配</Tag>
                    )}
                  </Space>
                }
                description={config.responseTemplate ? '模板响应' : '静态响应'}
              />
            </List.Item>
          )}
        />
      </Card>
      <Card
        title={editing ? '编辑 Mock 规则' : '新增 Mock 规则'}
        size="small"
        style={{ width: 420 }}
        extra={
          <Button
            size="small"
            icon={<PlusOutlined />}
            onClick={resetForm}
          >
            新建
          </Button>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="functionRef" label="函数引用" rules={[{ required: true }]} style={{ marginBottom: 12 }}>
            <Input placeholder="如 custom:userService.queryById" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked" initialValue={true} style={{ marginBottom: 12 }}>
            <Switch />
          </Form.Item>
          <Form.Item name="conditionExpression" label="条件表达式 (AviatorScript，兼容 JEXL)" style={{ marginBottom: 12 }}>
            <Input.TextArea
              rows={2}
              placeholder="如 workflowInput.env == 'local' 或 userId.startsWith('test_')"
            />
          </Form.Item>
          <Form.Item label="字段匹配器" style={{ marginBottom: 12 }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              {fieldMatchers.map((matcher, idx) => (
                <Space key={idx} style={{ width: '100%' }}>
                  <Input
                    placeholder="字段路径"
                    value={matcher.path}
                    onChange={(e) => {
                      const next = [...fieldMatchers];
                      next[idx].path = e.target.value;
                      setFieldMatchers(next);
                    }}
                    style={{ width: 140 }}
                  />
                  <Input
                    placeholder="精确值，逗号分隔"
                    value={matcher.exact}
                    onChange={(e) => {
                      const next = [...fieldMatchers];
                      next[idx].exact = e.target.value;
                      setFieldMatchers(next);
                    }}
                    style={{ width: 160 }}
                  />
                  <Button
                    size="small"
                    danger
                    onClick={() => setFieldMatchers(fieldMatchers.filter((_, i) => i !== idx))}
                  >
                    删除
                  </Button>
                </Space>
              ))}
              <Button
                size="small"
                type="dashed"
                onClick={() => setFieldMatchers([...fieldMatchers, { path: '', exact: '' }])}
              >
                添加字段匹配
              </Button>
            </Space>
          </Form.Item>
          <Form.Item label="响应类型" style={{ marginBottom: 12 }}>
            <Select<ResponseType>
              value={responseType}
              onChange={(v) => setResponseType(v)}
              options={[
                { value: 'static', label: '静态 JSON' },
                { value: 'template', label: '模板字符串' },
              ]}
            />
          </Form.Item>
          {responseType === 'static' ? (
            <Form.Item label="响应体 (JSON)" style={{ marginBottom: 12 }}>
              <JsonEditor value={responseBody} onChange={setResponseBody} height={180} />
            </Form.Item>
          ) : (
            <Form.Item
              name="responseTemplate"
              label="响应模板"
              rules={[{ required: responseType === 'template' }]}
              style={{ marginBottom: 12 }}
            >
              <Input.TextArea
                rows={6}
                placeholder='{"orderId": "${directInput.orderId}", "createdAt": "#{fn.now()}"}'
              />
            </Form.Item>
          )}
          <Form.Item name="delayMs" label="延迟 (ms)" initialValue={0} style={{ marginBottom: 12 }}>
            <InputNumber min={0} step={100} style={{ width: '100%' }} />
          </Form.Item>
          <Button type="primary" onClick={handleSave}>
            保存
          </Button>
        </Form>
      </Card>
    </div>
  );
};

export default MockServicePanel;
