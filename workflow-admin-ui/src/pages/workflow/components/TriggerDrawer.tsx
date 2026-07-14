import { useEffect, useMemo, useState } from 'react';
import {
  Drawer,
  Form,
  Input,
  Select,
  Switch,
  Button,
  Space,
  Typography,
  Empty,
  Spin,
  InputNumber,
  Divider,
  Alert,
  List,
  Tag,
  Tooltip,
  App,
  Radio,
} from 'antd';
import { PlusOutlined, DeleteOutlined, ThunderboltOutlined, EditOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import {
  listTriggerFunctions,
  buildTriggerDefaults,
  findByLegacyProtocol,
  type TriggerFunctionMeta,
  type WorkflowTrigger,
  type TriggerFunctionParam,
  type TriggerType,
} from '@/services/triggerFunctions';

interface TriggerDrawerProps {
  visible: boolean;
  onClose: () => void;
}

interface EditingState {
  index: number;
  trigger: WorkflowTrigger;
}

const TRIGGER_TYPE_OPTIONS: { value: TriggerType; label: string }[] = [
  { value: 'API', label: 'API (外部事件)' },
  { value: 'EVENT', label: 'EVENT (内部消息)' },
  { value: 'SCHEDULE', label: 'SCHEDULE (定时任务)' },
  { value: 'MANUAL', label: 'MANUAL (人工触发)' },
];

function resolveLabelOrName(meta: TriggerFunctionMeta, fallbackFnRef: string): string {
  return meta.label || fallbackFnRef.substring(fallbackFnRef.lastIndexOf(':') + 1);
}

function renderFormFieldForParam(
  param: TriggerFunctionParam,
  form: any,
  fieldPrefix: string[] = [],
) {
  const fieldName = [...fieldPrefix, param.name];
  const type = param.type?.toLowerCase() ?? 'string';
  const options = (param.options ?? []).map((entry: any) => {
    const [value, label] = Array.isArray(entry) ? entry : [entry, entry];
    return { value, label: label ?? value };
  });
  const rules: any[] = [];
  if (param.required) {
    rules.push({ required: true, message: `${param.label || param.name} 必填` });
  }

  const baseItemProps = {
    label: param.label || param.name,
    tooltip: param.description || undefined,
    name: fieldName,
    rules,
  };

  if (param.type?.toUpperCase() === 'ENUM' || options.length > 0) {
    return (
      <Form.Item key={param.name} {...baseItemProps}>
        <Select
          placeholder={`请选择${param.label || param.name}`}
          allowClear={!param.required}
          options={options}
        />
      </Form.Item>
    );
  }

  switch (type) {
    case 'boolean':
      return (
        <Form.Item key={param.name} {...baseItemProps} valuePropName="checked">
          <Switch />
        </Form.Item>
      );
    case 'number':
    case 'integer':
    case 'long':
    case 'int':
      return (
        <Form.Item key={param.name} {...baseItemProps}>
          <InputNumber
            style={{ width: '100%' }}
            placeholder={`请输入${param.label || param.name}`}
            step={type === 'integer' || type === 'int' || type === 'long' ? 1 : 'any'}
            stringMode={type === 'long'}
          />
        </Form.Item>
      );
    case 'textarea':
    case 'string_long':
      return (
        <Form.Item key={param.name} {...baseItemProps}>
          <Input.TextArea
            rows={3}
            placeholder={`请输入${param.label || param.name}`}
          />
        </Form.Item>
      );
    case 'object':
    case 'json':
    case 'map':
      return (
        <Form.Item key={param.name} {...baseItemProps}>
          <Input.TextArea
            rows={4}
            placeholder='{"key": "value"}'
            style={{ fontFamily: 'monospace', fontSize: 12 }}
          />
        </Form.Item>
      );
    case 'array':
    case 'list':
      return (
        <Form.Item key={param.name} {...baseItemProps}>
          <Input.TextArea
            rows={3}
            placeholder='["item1", "item2"]'
            style={{ fontFamily: 'monospace', fontSize: 12 }}
          />
        </Form.Item>
      );
    case 'select':
    case 'radio':
      if (type === 'radio') {
        return (
          <Form.Item key={param.name} {...baseItemProps}>
            <Radio.Group options={options} />
          </Form.Item>
        );
      }
      return (
        <Form.Item key={param.name} {...baseItemProps}>
          <Select allowClear={!param.required} options={options} />
        </Form.Item>
      );
    default:
      return (
        <Form.Item key={param.name} {...baseItemProps}>
          <Input placeholder={`请输入${param.label || param.name}`} />
        </Form.Item>
      );
  }
}

const TriggerDrawer: React.FC<TriggerDrawerProps> = ({ visible, onClose }) => {
  const { message } = App.useApp();
  const { workflowMeta, setWorkflowMeta } = useWorkflowStore();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<EditingState | null>(null);

  const { data: metaList, loading } = useRequest(
    async () => listTriggerFunctions(),
    { ready: visible, refreshDeps: [visible] },
  );

  const currentTriggers = useMemo<WorkflowTrigger[]>(
    () => (workflowMeta.triggers && workflowMeta.triggers.length > 0 ? workflowMeta.triggers : []),
    [workflowMeta.triggers],
  );

  const functionRefOptions = useMemo(() => {
    if (!metaList) return [];
    return metaList.map((m) => ({
      value: m.functionRef,
      label: (
        <span>
          {m.icon && <span style={{ marginRight: 6 }}>{m.icon}</span>}
          <strong>{resolveLabelOrName(m, m.functionRef)}</strong>
          <span style={{ marginLeft: 6, color: '#8c8c8c', fontSize: 12 }}>
            ({m.functionRef})
          </span>
        </span>
      ),
    }));
  }, [metaList]);

  const persistTriggers = (triggers: WorkflowTrigger[]) => {
    const baseMeta: Partial<any> = { triggers: triggers.slice() };
    if (triggers.length > 0) {
      const head = triggers[0];
      if (head.config) {
        const cfg: Record<string, any> = head.config as any;
        if (!workflowMeta.name || workflowMeta.name === '未命名' || workflowMeta.name === '') {
          const meta = metaList?.find((m) => m.functionRef === head.functionRef);
          if (meta) {
            baseMeta.name = `${resolveLabelOrName(meta, head.functionRef!)} 流程`;
          }
        }
        if (head.functionRef) {
          const meta = metaList?.find((m) => m.functionRef === head.functionRef);
          const protocolLegacy = meta?.legacyProtocol;
          if (protocolLegacy && !workflowMeta.protocol) {
            baseMeta.protocol = protocolLegacy;
          }
          if (protocolLegacy === 'HTTP' || protocolLegacy === 'HTTPS') {
            if (!workflowMeta.method && typeof cfg.method === 'string') {
              baseMeta.method = cfg.method.toUpperCase();
            }
            if (!workflowMeta.path && typeof cfg.path === 'string') {
              baseMeta.path = cfg.path;
            }
          } else if (!workflowMeta.path) {
            const bindKeyCandidates = ['bindKey', 'topic', 'serviceKey', 'queue'];
            for (const k of bindKeyCandidates) {
              if (typeof cfg[k] === 'string' && cfg[k]) {
                baseMeta.path = cfg[k];
                break;
              }
            }
          }
        }
      }
    }
    setWorkflowMeta(baseMeta);
  };

  const openEditor = (index: number) => {
    const existing = currentTriggers[index];
    const meta = metaList?.find((m) => m.functionRef === existing?.functionRef);
    const config = existing?.config && Object.keys(existing.config).length > 0
      ? { ...(existing.config as Record<string, any>) }
      : buildTriggerDefaults(meta ?? ({} as TriggerFunctionMeta));
    setEditing({
      index,
      trigger: {
        id: existing?.id ?? `trigger-${Date.now()}-${index}`,
        enabled: existing?.enabled ?? true,
        type: existing?.type ?? 'API',
        functionRef: existing?.functionRef ?? undefined,
        config,
      },
    });
  };

  const openCreateNew = (presetRef?: string) => {
    const index = currentTriggers.length;
    const ref = presetRef ?? metaList?.[0]?.functionRef;
    const meta = metaList?.find((m) => m.functionRef === ref);
    setEditing({
      index,
      trigger: {
        id: `trigger-${Date.now()}-${index}`,
        enabled: true,
        type: meta?.legacyProtocol ? 'API' : 'API',
        functionRef: ref,
        config: buildTriggerDefaults(meta ?? ({} as TriggerFunctionMeta)),
      },
    });
  };

  const handleSaveEditing = async () => {
    if (!editing) return;
    try {
      const values = await form.validateFields();
      const { functionRef, type, enabled, config } = values;
      const next: WorkflowTrigger = {
        id: editing.trigger.id ?? `trigger-${Date.now()}-${editing.index}`,
        functionRef,
        type: type ?? 'API',
        enabled: enabled ?? true,
        config: config ?? {},
      };
      const copy = currentTriggers.slice();
      copy[editing.index] = next;
      persistTriggers(copy);
      message.success('触发器配置已更新');
      setEditing(null);
    } catch {
      /* validation already shows error */
    }
  };

  const handleDelete = (index: number) => {
    const copy = currentTriggers.slice();
    copy.splice(index, 1);
    persistTriggers(copy);
    if (editing && editing.index === index) setEditing(null);
    else if (editing && editing.index > index) setEditing({ ...editing, index: editing.index - 1 });
  };

  const onSelectFunctionRef = (value: string) => {
    const meta = metaList?.find((m) => m.functionRef === value);
    const prev = form.getFieldsValue(true);
    const newDefaults = buildTriggerDefaults(meta ?? ({} as TriggerFunctionMeta));
    form.setFieldsValue({
      functionRef: value,
      config: { ...newDefaults, ...(prev.config ?? {}) },
    });
  };

  const ensureEditingSynced = () => {
    if (!editing) return;
    const meta = metaList?.find((m) => m.functionRef === editing.trigger.functionRef);
    const baseCfg = editing.trigger.config && Object.keys(editing.trigger.config).length > 0
      ? editing.trigger.config
      : buildTriggerDefaults(meta ?? ({} as TriggerFunctionMeta));
    form.setFieldsValue({
      functionRef: editing.trigger.functionRef,
      type: editing.trigger.type,
      enabled: editing.trigger.enabled,
      config: baseCfg,
    });
  };

  useEffect(() => {
    if (editing) ensureEditingSynced();
    else form.resetFields();
  }, [editing, visible, metaList]);

  useEffect(() => {
    if (!visible) {
      setEditing(null);
      return;
    }
    if (currentTriggers.length === 0 && !editing && workflowMeta.protocol) {
      const meta = findByLegacyProtocol(metaList ?? [], workflowMeta.protocol);
      if (meta) {
        const cfg: Record<string, any> = { ...buildTriggerDefaults(meta) };
        if (workflowMeta.method && !cfg.method) cfg.method = workflowMeta.method;
        if (workflowMeta.path) {
          if (!cfg.path) cfg.path = workflowMeta.path;
          const legacyProtocol = meta.legacyProtocol;
          if (legacyProtocol && legacyProtocol !== 'HTTP' && legacyProtocol !== 'HTTPS') {
            const bindKeyCandidates = ['bindKey', 'topic', 'serviceKey', 'queue'];
            let consumed = false;
            for (const k of bindKeyCandidates) {
              if (k in cfg) {
                cfg[k] = workflowMeta.path;
                consumed = true;
                break;
              }
            }
            if (!consumed) cfg.path = workflowMeta.path;
          }
        }
        const head: WorkflowTrigger = {
          id: `trigger-legacy-${meta.functionRef.substring(meta.functionRef.lastIndexOf(':') + 1)}`,
          type: 'API',
          functionRef: meta.functionRef,
          enabled: true,
          config: cfg,
        };
        setWorkflowMeta({ triggers: [head] });
      }
    }
  }, [visible, workflowMeta.protocol, workflowMeta.method, workflowMeta.path, metaList, currentTriggers.length, editing]);

  const currentEditingMeta = useMemo(
    () => (editing ? metaList?.find((m) => m.functionRef === editing.trigger.functionRef) : undefined),
    [editing, metaList],
  );

  return (
    <Drawer
      title={editing ? '编辑触发器配置' : '触发器配置 (schema 驱动)'}
      width={520}
      open={visible}
      onClose={onClose}
      zIndex={1200}
      extra={
        <Space>
          <Button onClick={onClose}>关闭</Button>
          {editing ? (
            <>
              <Button onClick={() => setEditing(null)}>返回列表</Button>
              <Button type="primary" onClick={handleSaveEditing}>保存触发器</Button>
            </>
          ) : null}
        </Space>
      }
    >
      {!editing && (
        <>
          <Alert
            style={{ marginBottom: 12 }}
            type="info"
            showIcon
            icon={<ThunderboltOutlined />}
            message={
              <span>
                触发函数参数完全由后端 <code>TriggerFunctionMeta.paramSchema</code> 驱动，
                前端根据 schema 动态渲染表单，不再硬编码 method / topic / serviceKey 等字段。
              </span>
            }
          />
          <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography.Text strong>触发器列表 ({currentTriggers.length})</Typography.Text>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openCreateNew()}
              disabled={!metaList || metaList.length === 0}
            >
              新增触发器
            </Button>
          </div>
          <Spin spinning={loading}>
            {!metaList || metaList.length === 0 ? (
              <Empty
                description={loading ? '加载触发器元数据中…' : '后端尚未注册任何 TriggerFunctionMeta'}
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ) : currentTriggers.length === 0 ? (
              <Alert
                type="warning"
                message="尚未配置任何触发器（当前是「纯函数集合」）"
                description="可在下方快速选择一个常用协议添加触发器"
                showIcon
                style={{ marginBottom: 12 }}
              />
            ) : (
              <List
                bordered
                dataSource={currentTriggers}
                locale={{ emptyText: '暂无触发器，可点击右上角新增' }}
                renderItem={(item, index) => {
                  const meta = metaList.find((m) => m.functionRef === item.functionRef);
                  const label = meta ? resolveLabelOrName(meta, item.functionRef!) : (item.functionRef ?? '未指定类型');
                  const cfg = (item.config ?? {}) as Record<string, any>;
                  const summaryParts = Object.entries(cfg)
                    .filter(([, v]) => v !== undefined && v !== null && v !== '')
                    .slice(0, 4)
                    .map(([k, v]) => {
                      const displayV = typeof v === 'object' ? JSON.stringify(v) : String(v);
                      return (
                        <Tag key={k} color="geekblue">
                          {k}={displayV.length > 20 ? displayV.substring(0, 20) + '…' : displayV}
                        </Tag>
                      );
                    });
                  return (
                    <List.Item
                      key={item.id ?? `${index}-${item.functionRef}`}
                      onClick={() => openEditor(index)}
                      actions={[
                        <Button type="primary" ghost size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); openEditor(index); }}>
                          编辑
                        </Button>,
                        <Button danger type="link" size="small" icon={<DeleteOutlined />} onClick={(e) => { e.stopPropagation(); handleDelete(index); }}>
                          删除
                        </Button>,
                      ]}
                      style={{ alignItems: 'flex-start', cursor: 'pointer', transition: 'background .15s' }}
                      onMouseEnter={(e) => (e.currentTarget.style.background = '#fafcff')}
                      onMouseLeave={(e) => (e.currentTarget.style.background = '')}
                    >
                      <List.Item.Meta
                        avatar={<ThunderboltOutlined style={{ color: '#6366f1' }} />}
                        title={
                          <Space>
                            <span>{label}</span>
                            {!item.enabled ? <Tag color="default">已禁用</Tag> : <Tag color="green">已启用</Tag>}
                            <Tag color="purple">{item.type ?? 'API'}</Tag>
                            <Tag color="blue" style={{ border: '1px dashed #91caff' }}>点击卡片 → 编辑</Tag>
                          </Space>
                        }
                        description={
                          <div style={{ maxWidth: 340, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                            {summaryParts.length > 0 ? summaryParts : <Typography.Text type="secondary">（暂无参数）</Typography.Text>}
                          </div>
                        }
                      />
                    </List.Item>
                  );
                }}
              />
            )}
            {metaList && metaList.length > 0 && (
              <>
                <Divider orientation="left" plain style={{ marginTop: 24 }}>快速添加（按协议）</Divider>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {metaList.map((m) => (
                    <Tooltip key={m.functionRef} title={m.description || m.functionRef}>
                      <Button onClick={() => openCreateNew(m.functionRef)} icon={<PlusOutlined />}>
                        {m.icon && <span style={{ marginRight: 4 }}>{m.icon}</span>}
                        {resolveLabelOrName(m, m.functionRef)}
                      </Button>
                    </Tooltip>
                  ))}
                </div>
              </>
            )}
          </Spin>
        </>
      )}
      {editing && (
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item label="functionRef" name="functionRef" rules={[{ required: true, message: '请选择触发函数类型' }]}>
            <Select
              loading={loading}
              options={functionRefOptions}
              onChange={onSelectFunctionRef}
              placeholder="选择要使用的触发函数（入站协议）"
            />
          </Form.Item>
          <Space style={{ marginBottom: 12 }} wrap>
            <Form.Item name="type" label="触发器类型" style={{ marginBottom: 0, minWidth: 220 }}>
              <Select
                options={TRIGGER_TYPE_OPTIONS}
                placeholder="触发类型"
              />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked" style={{ marginBottom: 0 }}>
              <Switch defaultChecked />
            </Form.Item>
          </Space>
          {currentEditingMeta?.description && (
            <Alert style={{ marginBottom: 12 }} type="info" showIcon message={currentEditingMeta.description} />
          )}
          <Divider style={{ margin: '8px 0' }} plain orientation="left">
            参数（由 schema 驱动）
          </Divider>
          {(!currentEditingMeta || !currentEditingMeta.paramSchema || currentEditingMeta.paramSchema.length === 0) ? (
            <Empty description="该触发函数没有需要配置的参数" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <>
              {currentEditingMeta.paramSchema.map((p) => renderFormFieldForParam(p, form, ['config']))}
            </>
          )}
          <Divider orientation="left" plain>
            原始配置预览 (只读)
          </Divider>
          <Form.Item noStyle shouldUpdate>
            {({ getFieldValue }) => {
              const cfg = getFieldValue('config') ?? {};
              return (
                <pre style={{ padding: 12, background: '#f6f8fa', borderRadius: 6, fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all', margin: 0 }}>
                  {JSON.stringify(cfg, null, 2)}
                </pre>
              );
            }}
          </Form.Item>
        </Form>
      )}
    </Drawer>
  );
};

export default TriggerDrawer;
