import { useEffect, useState, useMemo } from 'react';
import { Drawer, Form, Input, Select, Button, Space, Divider, Typography, Spin, Empty, Tag } from 'antd';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { PROTOCOL_OPTIONS, getProtocolConfig } from '@/constants/protocol';
import { getSchemas } from '@/services/schema';
import { useClickDebounce } from '@/utils/useClickDebounce';
import type { SchemaDefinition, SchemaFormat } from '@/types/schema';

interface WorkflowMetaPanelProps {
  visible: boolean;
  onClose: () => void;
}

const CATEGORY_OPTIONS = [
  { value: 'BUSINESS', label: '业务工作流' },
  { value: 'META', label: '元工作流' },
];

const SCOPE_OPTIONS = [
  { value: 'PRIVATE', label: '应用私有', description: '仅所属应用分组可用' },
  { value: 'PLATFORM', label: '平台内置', description: '全局共享，所有 Worker 可用' },
];

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ACTIVE', label: '已发布' },
  { value: 'DEPRECATED', label: '已下线' },
];

function safeStringify(value: any): string {
  return value ? JSON.stringify(value, null, 2) : '';
}

function safeParse(value: string): any {
  try {
    return value ? JSON.parse(value) : undefined;
  } catch {
    return undefined;
  }
}

function normalizeJson(obj: any): string {
  try {
    return JSON.stringify(typeof obj === 'string' ? JSON.parse(obj) : obj);
  } catch {
    return '';
  }
}

/** 根据 JSON 内容反查匹配的 Schema */
function matchSchemaByContent(schemas: SchemaDefinition[], content: any): SchemaDefinition | undefined {
  if (!content) return undefined;
  const normalized = normalizeJson(content);
  return schemas.find((s) => normalizeJson(s.schemaJson) === normalized);
}

const PUBLISH_TYPE_LABEL: Record<string, string> = {
  ALL: '全量广播',
  APP_GROUP: '按应用群',
  INSTANCES: '按实例',
};

const COMPACT_ITEM = { marginBottom: 12 };

const WorkflowMetaPanel: React.FC<WorkflowMetaPanelProps> = ({ visible, onClose }) => {
  const [form] = Form.useForm();
  const { workflowMeta, setWorkflowMeta, ensureParamValidateHeadNode } = useWorkflowStore();
  const [schemas, setSchemas] = useState<SchemaDefinition[]>([]);
  const [schemasLoading, setSchemasLoading] = useState(false);

  /** 按类型标签过滤的 Schema 选项：入参只显示 INPUT，出参只显示 OUTPUT */
  const inputSchemaOptions = useMemo(
    () => schemas.filter((s) => (s.schemaType || 'INPUT').split(',').includes('INPUT'))
      .map((s) => ({ value: s.schemaName, label: `${s.schemaName} (${s.schemaFormat || 'json-schema'})`, description: s.description, format: s.schemaFormat })),
    [schemas],
  );
  const outputSchemaOptions = useMemo(
    () => schemas.filter((s) => (s.schemaType || 'INPUT').split(',').includes('OUTPUT'))
      .map((s) => ({ value: s.schemaName, label: `${s.schemaName} (${s.schemaFormat || 'json-schema'})`, description: s.description, format: s.schemaFormat })),
    [schemas],
  );

  /** 根据选中的 schemaName 获取对应 JSON */
  const getSchemaJson = (schemaName: string | undefined) => {
    if (!schemaName) return undefined;
    const found = schemas.find((s) => s.schemaName === schemaName);
    return found?.schemaJson ? safeParse(found.schemaJson) : undefined;
  };

  /** 根据选中的 schemaName 获取对应格式 */
  const getSchemaFormat = (schemaName: string | undefined): SchemaFormat => {
    if (!schemaName) return 'json-schema';
    const found = schemas.find((s) => s.schemaName === schemaName);
    return (found?.schemaFormat as SchemaFormat) || 'json-schema';
  };

  useEffect(() => {
    if (!visible) return;
    setSchemasLoading(true);
    getSchemas({ pageSize: 500 })
      .then((res) => setSchemas(res?.list ?? []))
      .catch(() => setSchemas([]))
      .finally(() => setSchemasLoading(false));
  }, [visible]);

  useEffect(() => {
    if (visible && schemas.length > 0) {
      const matchedInput = matchSchemaByContent(schemas, workflowMeta.inputSchema);
      const matchedOutput = matchSchemaByContent(schemas, workflowMeta.outputSchema);
      form.setFieldsValue({
        ...workflowMeta,
        inputSchemaRef: matchedInput?.schemaName,
        outputSchemaRef: matchedOutput?.schemaName,
        inputSchema: safeStringify(workflowMeta.inputSchema),
        inputSchemaFormat: matchedInput?.schemaFormat || workflowMeta.inputSchemaFormat || 'json-schema',
        outputSchema: safeStringify(workflowMeta.outputSchema),
        outputSchemaFormat: matchedOutput?.schemaFormat || workflowMeta.outputSchemaFormat || 'json-schema',
        transactionConfig: safeStringify(workflowMeta.transactionConfig),
      });
    } else if (visible) {
      form.setFieldsValue({
        ...workflowMeta,
        inputSchemaRef: undefined,
        outputSchemaRef: undefined,
        inputSchema: safeStringify(workflowMeta.inputSchema),
        inputSchemaFormat: workflowMeta.inputSchemaFormat || 'json-schema',
        outputSchema: safeStringify(workflowMeta.outputSchema),
        outputSchemaFormat: workflowMeta.outputSchemaFormat || 'json-schema',
        transactionConfig: safeStringify(workflowMeta.transactionConfig),
      });
    }
  }, [visible, workflowMeta, form, schemas]);

  const handleSave = useClickDebounce(() => {
    form.validateFields().then((values) => {
      const { inputSchemaRef, outputSchemaRef, ...rest } = values;
      const inputSchemaValue = typeof values.inputSchema === 'string' ? safeParse(values.inputSchema) : values.inputSchema;
      const outputSchemaValue = typeof values.outputSchema === 'string' ? safeParse(values.outputSchema) : values.outputSchema;
      const inputSchema = inputSchemaRef ? getSchemaJson(inputSchemaRef) : inputSchemaValue ?? workflowMeta.inputSchema;
      const outputSchema = outputSchemaRef ? getSchemaJson(outputSchemaRef) : outputSchemaValue ?? workflowMeta.outputSchema;
      setWorkflowMeta({
        ...rest,
        inputSchema,
        inputSchemaFormat: inputSchemaRef ? getSchemaFormat(inputSchemaRef) : values.inputSchemaFormat || workflowMeta.inputSchemaFormat || 'json-schema',
        outputSchema,
        outputSchemaFormat: outputSchemaRef ? getSchemaFormat(outputSchemaRef) : values.outputSchemaFormat || workflowMeta.outputSchemaFormat || 'json-schema',
        transactionConfig: safeParse(values.transactionConfig),
      });
      // 配置了入参 Schema 时，自动确保头部存在 paramValidate 节点
      ensureParamValidateHeadNode(inputSchema);
      onClose();
    });
  });

  /** 选中 schema 时更新预览和对应格式 */
  const handleSchemaSelectChange = (field: 'inputSchema' | 'outputSchema', schemaName: string) => {
    const json = getSchemaJson(schemaName);
    const format = getSchemaFormat(schemaName);
    form.setFieldsValue({
      [field]: safeStringify(json),
      [`${field}Format`]: format,
    });
  };

  return (
    <Drawer
      title="工作流元信息配置"
      width={480}
      open={visible}
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={handleSave}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        {/* 只读信息（由 Toolbar 编辑） */}
        <div style={{ padding: '8px 12px', background: '#f6f8fa', borderRadius: 6, marginBottom: 12, fontSize: 13 }}>
          <Typography.Text type="secondary">名称 / 方法 / 路径请在顶部工具栏编辑</Typography.Text>
          <div style={{ marginTop: 4 }}>
            <strong>{workflowMeta.name || '未命名'}</strong>
            {workflowMeta.method && <span style={{ marginLeft: 8, color: '#6366f1' }}>{workflowMeta.method}</span>}
            {workflowMeta.path && <span style={{ marginLeft: 8, fontFamily: 'monospace' }}>{workflowMeta.path}</span>}
          </div>
        </div>

        <Form.Item name="scope" label="作用域" rules={[{ required: true }]} style={COMPACT_ITEM}
          tooltip="PRIVATE: 仅所属应用分组可用；PLATFORM: 全局共享"
        >
          <Select options={SCOPE_OPTIONS} />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, curr) => prev.scope !== curr.scope}>
          {({ getFieldValue }) =>
            getFieldValue('scope') === 'PRIVATE' ? (
              <Form.Item name="appGroup" label="应用分组" rules={[{ required: true, message: '应用私有工作流需指定 app_group' }]} style={COMPACT_ITEM}>
                <Input placeholder="所属应用分组（如 my-app）" />
              </Form.Item>
            ) : null
          }
        </Form.Item>
        <Form.Item name="category" label="分类" rules={[{ required: true }]} style={COMPACT_ITEM}>
          <Select options={CATEGORY_OPTIONS} />
        </Form.Item>
        <Form.Item name="protocol" label="协议" rules={[{ required: true }]} style={COMPACT_ITEM}>
          <Select
            options={PROTOCOL_OPTIONS.map((p) => ({ value: p, label: getProtocolConfig(p).label }))}
          />
        </Form.Item>
        <Form.Item name="status" label="状态" style={COMPACT_ITEM}>
          <Select options={STATUS_OPTIONS} />
        </Form.Item>

        <Divider style={{ margin: '8px 0' }}>Schema 定义</Divider>
        <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8, fontSize: 12 }}>
          仅支持引用 Schema 管理页面中已创建的 Schema
        </Typography.Text>

        <Form.Item
          name="inputSchemaRef"
          label="入参 Schema"
          style={COMPACT_ITEM}
          tooltip="从 Schema 管理页面选择"
        >
          <Select
            showSearch
            allowClear
            placeholder="请选择入参 Schema"
            loading={schemasLoading}
            options={inputSchemaOptions}
            notFoundContent={schemasLoading ? <Spin size="small" /> : <Empty description="暂无 INPUT 类型 Schema，请前往 Schema 管理创建" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
            onChange={(v: string | undefined) => {
              if (v) handleSchemaSelectChange('inputSchema', v);
              else form.setFieldsValue({ inputSchema: '' });
            }}
            filterOption={(input, option) =>
              (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
          />
        </Form.Item>
        <Form.Item name="inputSchema" hidden>
          <Input.TextArea />
        </Form.Item>
        <Form.Item name="inputSchemaFormat" hidden>
          <Input />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, curr) => prev.inputSchema !== curr.inputSchema || prev.inputSchemaFormat !== curr.inputSchemaFormat}>
          {({ getFieldValue }) => {
            const raw = getFieldValue('inputSchema');
            if (!raw) return null;
            const fmt = getFieldValue('inputSchemaFormat') || 'json-schema';
            return (
              <pre style={{ padding: '8px 12px', background: '#f6f8fa', borderRadius: 6, border: '1px solid #e8e8e8', fontSize: 12, maxHeight: 160, overflow: 'auto', marginBottom: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all', color: '#595959' }}>
                {fmt !== 'json-schema' && <Tag color="blue" style={{ marginRight: 8 }}>{fmt}</Tag>}
                {raw}
              </pre>
            );
          }}
        </Form.Item>

        <Form.Item
          name="outputSchemaRef"
          label="出参 Schema"
          style={COMPACT_ITEM}
          tooltip="从 Schema 管理页面选择"
        >
          <Select
            showSearch
            allowClear
            placeholder="请选择出参 Schema"
            loading={schemasLoading}
            options={outputSchemaOptions}
            notFoundContent={schemasLoading ? <Spin size="small" /> : <Empty description="暂无 OUTPUT 类型 Schema，请前往 Schema 管理创建" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
            onChange={(v: string | undefined) => {
              if (v) handleSchemaSelectChange('outputSchema', v);
              else form.setFieldsValue({ outputSchema: '' });
            }}
            filterOption={(input, option) =>
              (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
          />
        </Form.Item>
        <Form.Item name="outputSchema" hidden>
          <Input.TextArea />
        </Form.Item>
        <Form.Item name="outputSchemaFormat" hidden>
          <Input />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, curr) => prev.outputSchema !== curr.outputSchema || prev.outputSchemaFormat !== curr.outputSchemaFormat}>
          {({ getFieldValue }) => {
            const raw = getFieldValue('outputSchema');
            if (!raw) return null;
            const fmt = getFieldValue('outputSchemaFormat') || 'json-schema';
            return (
              <pre style={{ padding: '8px 12px', background: '#f6f8fa', borderRadius: 6, border: '1px solid #e8e8e8', fontSize: 12, maxHeight: 160, overflow: 'auto', marginBottom: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all', color: '#595959' }}>
                {fmt !== 'json-schema' && <Tag color="blue" style={{ marginRight: 8 }}>{fmt}</Tag>}
                {raw}
              </pre>
            );
          }}
        </Form.Item>

        <Divider style={{ margin: '8px 0' }}>高级配置</Divider>

        <Form.Item name="transactionConfig" label="事务配置 (JSON)" style={COMPACT_ITEM}>
          <Input.TextArea rows={3} placeholder='{"enabled": true}' />
        </Form.Item>

        {/* 发布策略只读展示（实际发布在列表页点击"发布"按钮时选择） */}
        {workflowMeta.publishTarget && workflowMeta.publishTarget.type && (
          <>
            <Divider style={{ margin: '8px 0' }}>发布状态（只读）</Divider>
            <div style={{ padding: '8px 12px', background: '#f6f8fa', borderRadius: 6, fontSize: 13 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>发布策略</Typography.Text>
              <div style={{ marginTop: 4 }}>
                <Tag color="#6366f1">{PUBLISH_TYPE_LABEL[workflowMeta.publishTarget.type] || workflowMeta.publishTarget.type}</Tag>
                {workflowMeta.publishTarget.groups?.map((g: string) => (
                  <Tag key={g} color="#8b5cf6">{g}</Tag>
                ))}
                {workflowMeta.publishTarget.instanceIds?.map((i: string) => (
                  <Tag key={i} color="#06b6d4">{i}</Tag>
                ))}
              </div>
              <Typography.Text type="secondary" style={{ fontSize: 11, marginTop: 4, display: 'block' }}>
                发布目标请在列表页点击「发布」按钮时选择
              </Typography.Text>
            </div>
          </>
        )}
      </Form>
    </Drawer>
  );
};

export default WorkflowMetaPanel;
