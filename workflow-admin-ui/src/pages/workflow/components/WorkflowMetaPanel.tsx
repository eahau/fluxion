import { useEffect, useState, useMemo } from 'react';
import {
  Drawer,
  Form,
  Input,
  Select,
  Button,
  Space,
  Divider,
  Typography,
  Spin,
  Empty,
  Tag,
  Modal,
  App,
  Alert,
  Tooltip as AntTooltip,
  message as globalMessage,
} from 'antd';
import { RocketOutlined, UndoOutlined, ExperimentOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import {
  publishFunctionSet,
  unpublishFunctionSet,
  type FunctionSetDTO,
} from '@/services/functionSet';
import { useClickDebounce } from '@/utils/useClickDebounce';
import type { SchemaDefinition, SchemaFormat } from '@/types/schema';
import { useRequest } from '@umijs/max';
import { getSchemas } from '@/services/schema';
import { listTriggerFunctions, type WorkflowTrigger, type TriggerFunctionMeta } from '@/services/triggerFunctions';
import { listApps } from '@/services/apps';
import type { App as AppInfo } from '@/services/apps';

/**
 * 应用列表 normalize — 与 FunctionTestPanel 保持一致
 * 兜底 { list } / { data } / { items } 等多种包装结构，避免 Select 空渲染。
 */
function normalizeAppList(raw: any): AppInfo[] {
  if (raw == null) return [];
  if (Array.isArray(raw)) return raw as AppInfo[];
  if (typeof raw !== 'object') return [];

  const candidates = ['list', 'data', 'items', 'records', 'rows', 'content', 'result', 'apps', 'appList'];
  for (const k of candidates) {
    const v = (raw as any)[k];
    if (Array.isArray(v)) return v as AppInfo[];
  }
  if ((raw as AppInfo).id != null && (raw as AppInfo).appKey != null) {
    return [raw as AppInfo];
  }
  console.warn('[WorkflowMetaPanel] normalizeAppList 无法识别结构：', raw);
  return [];
}

interface WorkflowMetaPanelProps {
  visible: boolean;
  onClose: () => void;
  onOpenTrigger: () => void;
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

const DATA_SOURCE_FN_PREFIXES = [
  'db:', 'dbExecute', 'dbQuery', 'dbUpdate', 'builtin:db',
  'mysql:', 'postgres:', 'postgresql:', 'oracle:', 'sqlserver:', 'clickhouse:', 'h2:', 'jdbc:',
  'redis:', 'builtin:redis', 'redisson:', 'lettuce:',
  'kafka:', 'builtin:kafka', 'rabbitmq:', 'rocketmq:', 'mq:', 'builtin:mq',
  'dubbo:', 'grpc:', 'builtin:dubbo', 'builtin:grpc',
];

const WorkflowMetaPanel: React.FC<WorkflowMetaPanelProps> = ({ visible, onClose, onOpenTrigger }) => {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm();
  const { workflowMeta, setWorkflowMeta, ensureParamValidateHeadNode, nodes } = useWorkflowStore();
  const [schemas, setSchemas] = useState<SchemaDefinition[]>([]);
  const [schemasLoading, setSchemasLoading] = useState(false);
  const [publishModalOpen, setPublishModalOpen] = useState(false);
  const [publishLoading, setPublishLoading] = useState(false);
  const [setRefDraft, setSetRefDraft] = useState<string>('');
  const [publishForm] = Form.useForm();
  const { data: triggerMetas } = useRequest(async () => listTriggerFunctions(), {
    ready: visible,
    refreshDeps: [visible],
  });
  const { data: appListRaw, loading: appsLoading, error: appsError } = useRequest(async () => listApps(), {
    formatResult: (res) => normalizeAppList(res),
    ready: visible,
    refreshDeps: [visible],
  });
  const appList = useMemo<AppInfo[]>(() => (appListRaw as AppInfo[]) ?? [], [appListRaw]);

  useEffect(() => {
    if (appsError) {
      console.error('[WorkflowMetaPanel] 加载应用列表失败：', appsError);
      globalMessage.error('加载应用列表失败，请刷新页面重试');
    }
  }, [appsError]);

  const appOptions = appList
    .filter((a: AppInfo) => {
      if (a == null) return false;
      if (!a.status) return true;
      const s = String(a.status).toUpperCase();
      if (s === 'DISABLED' || s === 'INACTIVE' || s === '0') return false;
      return true;
    })
    .map((a: AppInfo) => ({
      value: a.appKey,
      label: `${a.appName} — ${a.appKey}${a.owner ? `（负责人：${a.owner}）` : ''}`,
    }));

  const currentTriggers = workflowMeta.triggers ?? [];
  const currentAppName = appList.find((a: AppInfo) => a.appKey === workflowMeta.appGroup)?.appName;

  /**
   * Detect data-source dependent nodes in the DAG.
   * Matches backend WfDefinitionService.validateScopeAppGroupAndDataSourceDeps.
   * Returns [{id, functionRef, label}] so we can show warnings / save-blockers.
   */
  const dataSourceRefNodes = useMemo(() => {
    const matched: Array<{ id: string; functionRef: string; label: string }> = [];
    nodes?.forEach((n: any) => {
      const functionRef = String(n?.data?.functionRef ?? n?.functionRef ?? '').trim();
      const nodeId = String(n?.id ?? '');
      if (!functionRef && !nodeId) return;
      const resourceRef =
        String(n?.data?.resourceRef ?? n?.resourceRef ?? '').trim() ||
        String(n?.data?.resourceName ?? n?.resourceName ?? '').trim();
      const isDataSourceFn =
        resourceRef.length > 0 ||
        (functionRef.length > 0 &&
          DATA_SOURCE_FN_PREFIXES.some((p) => functionRef.toLowerCase().startsWith(p.toLowerCase())));
      if (isDataSourceFn && functionRef) {
        matched.push({
          id: nodeId || functionRef,
          functionRef,
          label: `${functionRef}${resourceRef ? `（resource=${resourceRef}）` : ''}`,
        });
      }
    });
    return matched;
  }, [nodes]);

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
    const scope = form.getFieldValue('scope') || workflowMeta.scope || 'PRIVATE';
    const appGroup = form.getFieldValue('appGroup') || workflowMeta.appGroup;
    if (scope === 'PRIVATE' && !appGroup) {
      message.error(
        '应用私有（scope=PRIVATE）的函数集合必须选择「所属应用」，因为 DB/Redis/MQ 等数据源与 App 绑定。请先在下方选择所属应用后再保存。',
      );
      return;
    }
    if (scope === 'PLATFORM' && dataSourceRefNodes.length > 0) {
      const list = dataSourceRefNodes.map((n) => n.label).join('、');
      modal.error({
        title: '平台通用作用域不能包含数据源依赖函数',
        content: (
          <div>
            <div style={{ marginBottom: 8 }}>
              平台通用（scope=PLATFORM）的函数集合不能引用 DB/Redis/MQ 等依赖数据源的函数，因为此类资源必须与具体应用绑定。当前检测到 {dataSourceRefNodes.length} 个数据相关节点：
            </div>
            <div style={{ padding: '8px 12px', background: '#fff1f0', border: '1px solid #ffa39e', borderRadius: 6, fontSize: 13 }}>
              {list}
            </div>
            <div style={{ marginTop: 12, fontSize: 12, color: '#8c8c8c' }}>
              处理建议：① 改为「应用私有（PRIVATE）」并选择所属 App；或 ② 从画布中移除上述数据源依赖函数节点。
            </div>
          </div>
        ),
        okText: '我知道了',
      });
      return;
    }
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

  // ── 函数集合发布/撤销 ──────────────────────────────────────────────
  const canPublishAsSet = useMemo(() => {
    const issues: string[] = [];
    if (!workflowMeta.id) issues.push('尚未保存到服务端（没有主键）');
    if (workflowMeta.status !== 'ACTIVE') issues.push('状态需为「已发布 ACTIVE」');
    if (!workflowMeta.inputSchema) issues.push('入参 Schema 不能为空');
    if (!workflowMeta.outputSchema) issues.push('出参 Schema 不能为空');
    return { ok: issues.length === 0, issues };
  }, [workflowMeta.id, workflowMeta.status, workflowMeta.inputSchema, workflowMeta.outputSchema]);

  const applyPublishedToMeta = (dto: FunctionSetDTO) => {
    setWorkflowMeta({
      isPublishedSet: dto.published,
      setRefName: dto.setRefName ?? null,
    });
  };

  const openPublishModal = useClickDebounce(() => {
    if (!canPublishAsSet.ok) {
      message.error('不满足发布前置条件：' + canPublishAsSet.issues.join('；'));
      return;
    }
    setSetRefDraft(workflowMeta.setRefName || '');
    publishForm.setFieldsValue({ setRefName: workflowMeta.setRefName || '' });
    setPublishModalOpen(true);
  });

  const handleConfirmPublish = useClickDebounce(async () => {
    if (!workflowMeta.id) return;
    try {
      setPublishLoading(true);
      const values = await publishForm.validateFields();
      const dto = await publishFunctionSet(
        Number(workflowMeta.id),
        values.setRefName ? { setRefName: values.setRefName } : undefined,
      );
      applyPublishedToMeta(dto);
      message.success(`已发布为函数集合「${dto.setRefName || dto.workflowId}」`);
      setPublishModalOpen(false);
    } catch (e: any) {
      message.error(e?.message || '发布失败');
    } finally {
      setPublishLoading(false);
    }
  });

  const handleUnpublish = useClickDebounce(() => {
    if (!workflowMeta.id) return;
    modal.confirm({
      title: '撤销发布为函数集合？',
      content: (
        <div>
          <div>撤销后，其他工作流将不能再通过 SET_REF 引用当前 DAG。</div>
          <div style={{ marginTop: 8, color: '#d4380d' }}>
            若已有工作流节点引用 setRefName=<strong>{workflowMeta.setRefName}</strong>，执行时会抛错。
          </div>
        </div>
      ),
      okText: '确认撤销',
      okType: 'danger',
      onOk: async () => {
        try {
          const dto = await unpublishFunctionSet(Number(workflowMeta.id));
          applyPublishedToMeta(dto);
          message.success('已撤销函数集合发布');
        } catch (e: any) {
          message.error(e?.message || '撤销失败');
        }
      },
    });
  });

  return (
    <Drawer
      title="函数集合配置"
      width={480}
      open={visible}
      onClose={onClose}
      zIndex={1200}
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
          <Typography.Text type="secondary">名称请在顶部工具栏编辑；触发器点击左侧按钮配置 (schema 驱动)</Typography.Text>
          <div style={{ marginTop: 4, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <strong style={{ fontSize: 14 }}>{workflowMeta.name || '未命名函数集合'}</strong>
            <Button size="small" icon={<ThunderboltOutlined />} type="dashed" onClick={onOpenTrigger}>
              {currentTriggers.length > 0
                ? `触发器 (${currentTriggers.length})`
                : '添加触发器 (纯函数集合)'}
            </Button>
            {currentTriggers.length > 0 && currentTriggers.slice(0, 3).map((t) => {
              const meta = triggerMetas?.find((m: TriggerFunctionMeta) => m.functionRef === t.functionRef);
              const label = meta?.label || (t.functionRef ? t.functionRef.substring(t.functionRef.lastIndexOf(':') + 1) : '触发器');
              const cfg = (t.config ?? {}) as Record<string, any>;
              const summaryChips = Object.entries(cfg)
                .filter(([, v]) => v !== undefined && v !== null && v !== '')
                .slice(0, 2)
                .map(([k, v]) => `${k}=${typeof v === 'string' && v.length > 14 ? v.substring(0, 14) + '…' : String(v)}`);
              return (
                <Tag key={t.id ?? t.functionRef!} color={t.enabled ? 'geekblue' : 'default'}>
                  <span style={{ fontWeight: 600 }}>{label}</span>
                  {summaryChips.length > 0 && (
                    <span style={{ marginLeft: 6, fontFamily: 'monospace', fontSize: 11 }}>
                      {summaryChips.join(' ')}
                    </span>
                  )}
                </Tag>
              );
            })}
          </div>
        </div>

        <Form.Item name="scope" label="作用域" rules={[{ required: true }]} style={COMPACT_ITEM}
          tooltip="PRIVATE: 仅所属应用分组可用；PLATFORM: 全局共享（若涉及 DB/Redis 数据源，仍需选择绑定的数据源所属应用）"
        >
          <Select options={SCOPE_OPTIONS} />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, curr) => prev.scope !== curr.scope}>
          {({ getFieldValue }) => {
            const scope = getFieldValue('scope') || workflowMeta.scope || 'PRIVATE';
            const isPlatform = scope === 'PLATFORM';
            if (!isPlatform || dataSourceRefNodes.length === 0) return null;
            const violatedList = dataSourceRefNodes.map((n) => n.label).join('；');
            return (
              <Alert
                type="error"
                showIcon
                style={{ marginBottom: 12, borderRadius: 8 }}
                message={`PLATFORM 作用域已检测到 ${dataSourceRefNodes.length} 个数据源依赖函数`}
                description={
                  <div>
                    <div style={{ marginBottom: 6 }}>
                      平台通用工作流不能引用 DB / Redis / MQ / Dubbo / gRPC 等依赖资源绑定的函数，因为这些数据源必须归属到具体应用。
                    </div>
                    <div style={{ padding: '6px 10px', background: '#fff1f0', border: '1px solid #ffa39e', borderRadius: 6, fontSize: 12, fontFamily: '"SF Mono", Monaco, monospace', wordBreak: 'break-all' }}>
                      {violatedList}
                    </div>
                    <div style={{ marginTop: 6, fontSize: 12, color: '#8c8c8c' }}>
                      解决：① 将「作用域」切换为「应用私有 PRIVATE」并选择所属 App；或 ② 回到画布中移除上述数据源依赖节点。
                    </div>
                  </div>
                }
              />
            );
          }}
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, curr) => prev.scope !== curr.scope}>
          {({ getFieldValue }) => {
            const scope = getFieldValue('scope') || workflowMeta.scope || 'PRIVATE';
            const isPrivate = scope === 'PRIVATE';
            return (
              <Form.Item
                name="appGroup"
                label={<span><strong style={{ color: isPrivate ? '#cf1322' : '#8c8c8c' }}>{isPrivate ? '*' : ''}</strong> 所属应用（数据源绑定维度）</span>}
                rules={[{ required: isPrivate, message: '应用私有工作流必须选择所属应用' }]}
                style={COMPACT_ITEM}
                extra={
                  <span>
                    DB / Redis 数据源与 App 绑定，若后续节点用到这些函数必须先选 App。
                    {currentAppName && (
                      <span> 当前：<Tag color="geekblue" style={{ marginLeft: 4, marginBottom: 0 }}>{currentAppName} — {workflowMeta.appGroup}</Tag></span>
                    )}
                  </span>
                }
              >
                <Select
                  loading={appsLoading}
                  showSearch
                  allowClear
                  placeholder={appsLoading ? '加载应用列表中…' : '请选择所属应用（搜索应用名或 AppKey）'}
                  options={appOptions}
                  filterOption={(input, option) =>
                    (option?.label ?? '').toString().toLowerCase().includes(input.toLowerCase())
                  }
                  notFoundContent={
                    appsLoading ? <Spin size="small" /> : (
                      <Empty
                        description={<span>暂无应用，请先前往 <a href="#/app" target="_blank" rel="noreferrer">应用管理</a> 创建</span>}
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                      />
                    )
                  }
                />
              </Form.Item>
            );
          }}
        </Form.Item>
        <Form.Item name="category" label="分类" rules={[{ required: true }]} style={COMPACT_ITEM}>
          <Select options={CATEGORY_OPTIONS} />
        </Form.Item>
        <Form.Item
          label="触发器配置 (schema 驱动)"
          style={COMPACT_ITEM}
          tooltip={
            <span>
              触发参数（HTTP method/path、Kafka topic/consumerGroup、Dubbo serviceKey 等）完全由后端
              <code> TriggerFunctionMeta.paramSchema </code>驱动，
              不再 hardcode。打开触发器面板配置。
            </span>
          }
        >
          <Space>
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              onClick={onOpenTrigger}
            >
              打开触发器面板
            </Button>
            <Alert
              style={{ margin: 0, flex: 1 }}
              type={currentTriggers.length > 0 ? 'success' : 'warning'}
              showIcon
              message={
                currentTriggers.length > 0
                  ? `已配置 ${currentTriggers.length} 个触发器`
                  : '当前是「纯函数集合」：不能被 HTTP/Kafka 外部事件触发，只能被其它工作流通过 SET_REF 引用。'
              }
              description={
                currentTriggers.length > 0
                  ? currentTriggers.map((t) => {
                    const meta = triggerMetas?.find((m) => m.functionRef === t.functionRef);
                    return (
                      <div key={t.id ?? t.functionRef!} style={{ fontSize: 12 }}>
                        <strong style={{ color: '#595959' }}>{meta?.label || t.functionRef || '未命名触发器'}</strong>
                        <span style={{ margin: '0 8px', color: '#8c8c8c' }}>|</span>
                        <Typography.Text code type={t.enabled ? undefined : 'secondary'}>
                          {t.enabled ? 'ENABLED' : 'DISABLED'}
                        </Typography.Text>
                        {t.type && (
                          <>
                            <span style={{ margin: '0 8px', color: '#8c8c8c' }}>|</span>
                            <Tag color="purple" style={{ margin: 0 }}>{t.type}</Tag>
                          </>
                        )}
                      </div>
                    );
                  })
                  : '若需要被外部事件触发，请点击上方按钮选择 HTTP / Kafka / Dubbo / gRPC 等入站适配器。'
              }
            />
          </Space>
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

        <Divider style={{ margin: '8px 0' }}>函数集合发布</Divider>
        <Alert
          type="info"
          showIcon
          icon={<ExperimentOutlined />}
          style={{ marginBottom: 12 }}
          message={
            <span>
              发布为函数集合后，当前 DAG 会在函数下拉（SET_REF 分类）中可见，其它工作流可直接拖拽引用。
              <br />
              <span style={{ color: '#666' }}>
                执行时由引擎自动展开为 SUB_WORKFLOW 子工作流。组合规则：集合 B 输入 Schema 须是当前 DAG 输出的「目标子集」。
              </span>
            </span>
          }
        />
        <div
          style={{
            padding: '10px 12px',
            borderRadius: 6,
            background: workflowMeta.isPublishedSet ? '#f6ffed' : '#fafafa',
            border: '1px solid ' + (workflowMeta.isPublishedSet ? '#b7eb8f' : '#e8e8e8'),
          }}
        >
          <Space size={12} style={{ width: '100%', justifyContent: 'space-between', flexWrap: 'wrap' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {workflowMeta.isPublishedSet ? (
                  <Tag color="green" style={{ margin: 0 }}>已发布为函数集合</Tag>
                ) : (
                  <Tag style={{ margin: 0 }}>未发布</Tag>
                )}
                {workflowMeta.setRefName && (
                  <Typography.Text code style={{ margin: 0 }}>
                    setRefName = {workflowMeta.setRefName}
                  </Typography.Text>
                )}
              </div>
              {!canPublishAsSet.ok && !workflowMeta.isPublishedSet && (
                <div style={{ marginTop: 8, fontSize: 12, color: '#d4380d' }}>
                  发布前置未满足：
                  <ul style={{ margin: '4px 0 0 18px', padding: 0 }}>
                    {canPublishAsSet.issues.map((t) => (
                      <li key={t}>{t}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
            <Space wrap>
              <AntTooltip title={canPublishAsSet.ok || workflowMeta.isPublishedSet ? undefined : '请先满足全部发布前置条件'}>
                <Button
                  type="primary"
                  icon={<RocketOutlined />}
                  disabled={!canPublishAsSet.ok && !workflowMeta.isPublishedSet}
                  onClick={openPublishModal}
                >
                  {workflowMeta.isPublishedSet ? '更新 setRefName' : '发布为函数集合'}
                </Button>
              </AntTooltip>
              {workflowMeta.isPublishedSet && (
                <Button
                  danger
                  icon={<UndoOutlined />}
                  onClick={handleUnpublish}
                >
                  撤销发布
                </Button>
              )}
            </Space>
          </Space>
        </div>

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

      {/* setRefName 发布弹窗 */}
      <Modal
        title={workflowMeta.isPublishedSet ? '更新函数集合别名 (setRefName)' : '发布为函数集合'}
        open={publishModalOpen}
        confirmLoading={publishLoading}
        onOk={handleConfirmPublish}
        onCancel={() => setPublishModalOpen(false)}
        okText="确认发布"
      >
        <Form form={publishForm} layout="vertical">
          <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
            留空则按 <code>{'{appGroup}.set.{workflowId}'}</code> 自动生成别名。
          </Typography.Text>
          <Form.Item
            name="setRefName"
            label="setRefName 别名（可选）"
            rules={[{ max: 128, message: '别名不能超过 128 字符' }]}
            tooltip="该别名会写入 wf_function.function_alias，SET_REF 解析和 Designer 函数下拉均使用此名"
          >
            <Input
              placeholder="例如：biz.uc.queryUserById"
              value={setRefDraft}
              onChange={(e) => setSetRefDraft(e.target.value)}
              allowClear
            />
          </Form.Item>
          {canPublishAsSet.ok && (
            <Alert
              type="success"
              showIcon
              style={{ marginTop: 8 }}
              message="发布后，其它工作流即可在函数面板「函数集合」分类下拖入此 DAG，自动由引擎展开为 SUB_WORKFLOW 子工作流执行。"
            />
          )}
        </Form>
      </Modal>
    </Drawer>
  );
};

export default WorkflowMetaPanel;
