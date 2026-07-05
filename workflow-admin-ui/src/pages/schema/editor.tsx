import { history, useAccess, useLocation, useParams } from '@umijs/max';
import {
  Alert,
  Breadcrumb,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Drawer,
  FloatButton,
  Form,
  Input,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Tabs,
  Tag,
  Tooltip,
  message,
} from 'antd';
import {
  EyeOutlined,
  LockOutlined,
  UnlockOutlined,
  EditOutlined,
  RollbackOutlined,
  LinkOutlined,
  ExportOutlined,
  ArrowLeftOutlined,
  HomeOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState, useRef } from 'react';
import { createSchema, getSchema, updateSchema, getCachedSchemaMap } from '@/services/schema';
import { useClickDebounce } from '@/utils/useClickDebounce';
import SchemaFormEditor from './components/SchemaFormEditor';
import SchemaJsonEditor from './components/SchemaJsonEditor';
import SchemaValidator from './components/SchemaValidator';
import SchemaDiffViewer from './components/SchemaDiffViewer';
import SchemaPreview from './components/SchemaPreview';
import type { SchemaDefinition, SchemaTypeTag, SchemaFormat } from '@/types/schema';
import {
  fieldsToJsonSchema,
  jsonSchemaToFields,
  extractSchemaRefs,
  createAjvWithSchemaRefPlaceholders,
} from '@/utils/jsonSchema';

const SCHEMA_TYPE_OPTIONS: { value: SchemaTypeTag; label: string }[] = [
  { value: 'INPUT', label: 'INPUT' },
  { value: 'OUTPUT', label: 'OUTPUT' },
  { value: 'EVENT', label: 'EVENT' },
];

const SCHEMA_FORMAT_OPTIONS: { value: SchemaFormat; label: string }[] = [
  { value: 'json-schema', label: 'JSON Schema' },
  { value: 'protobuf', label: 'Protobuf' },
  { value: 'avro', label: 'Avro' },
];

/** Protobuf FileDescriptorProto 默认示例（后端通过 messageType 自动识别格式） */
const DEFAULT_PROTOBUF_SCHEMA = JSON.stringify(
  {
    name: 'ExampleMessage',
    messageType: [
      {
        name: 'ExampleMessage',
        field: [
          { name: 'id', number: 1, label: 'LABEL_OPTIONAL', type: 'TYPE_INT64' },
          { name: 'name', number: 2, label: 'LABEL_OPTIONAL', type: 'TYPE_STRING' },
        ],
      },
    ],
  },
  null,
  2,
);

/** 合法字段名：字母/下划线开头，只含字母、数字、下划线 */
const FIELD_NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/;

/**
 * 递归收集 JSON Schema 中所有 property key，
 * 校验是否符合字段名规范，返回不合规的 key 列表。
 */
function collectInvalidPropertyNames(schema: any, invalid: Set<string> = new Set()): string[] {
  if (!schema || typeof schema !== 'object') return [];
  if (Array.isArray(schema)) {
    schema.forEach((s) => collectInvalidPropertyNames(s, invalid));
    return [...invalid];
  }
  // properties 中的 key 必须是合法字段名
  if (schema.properties && typeof schema.properties === 'object') {
    for (const key of Object.keys(schema.properties)) {
      if (!FIELD_NAME_RE.test(key)) invalid.add(key);
      collectInvalidPropertyNames(schema.properties[key], invalid);
    }
  }
  // items（array 元素 schema）
  if (schema.items) collectInvalidPropertyNames(schema.items, invalid);
  // $defs / definitions
  for (const defKey of ['$defs', 'definitions']) {
    if (schema[defKey] && typeof schema[defKey] === 'object') {
      Object.values(schema[defKey]).forEach((s) => collectInvalidPropertyNames(s, invalid));
    }
  }
  // 组合关键字
  for (const kw of ['allOf', 'anyOf', 'oneOf']) {
    if (Array.isArray(schema[kw])) {
      schema[kw].forEach((s: any) => collectInvalidPropertyNames(s, invalid));
    }
  }
  if (schema.not) collectInvalidPropertyNames(schema.not, invalid);
  return [...invalid];
}

const SchemaEditor: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const access = useAccess();
  const queryMode = useMemo(() => new URLSearchParams(location.search).get('mode'), [location.search]);
  const [form] = Form.useForm();
  const [mode, setMode] = useState<'form' | 'json' | 'view'>(queryMode === 'view' ? 'view' : 'form');
  const [fields, setFields] = useState<any[]>([]);
  const [jsonValue, setJsonValue] = useState<string>('{}');
  const [schemaFormat, setSchemaFormat] = useState<SchemaFormat>('json-schema');
  const [loading, setLoading] = useState(false);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [activeTab, setActiveTab] = useState('preview');
  const [frozen, setFrozen] = useState(false);
  const [scope, setScope] = useState<string>('PLATFORM');
  const [appGroup, setAppGroup] = useState<string>('');
  /**
   * 引用预览跳转栈：栈的最后一个元素 = 当前右侧 SchemaPreview 面板正在预览的 schemaName
   * - 空数组 / undefined / 等于当前编辑中的 schemaName：都视为预览「当前编辑中的 Schema」
   * - 点引用 Tag 会 push 新目标；面包屑可逐级点击 / 上一步返回
   */
  const [previewStack, setPreviewStack] = useState<string[]>([]);
  const isEdit = !!id;
  const schemaNameValue = Form.useWatch('name', form) || id;
  /**
   * 所有层级引用 schema 的预加载详情 Map。
   * - 3 个子组件（SchemaPreview / SchemaValidator / SchemaFormEditor）共享同一份 Map，避免重复请求
   * - BFS 递归扫描引用的引用，收集全量 schema 后并行 Promise.all 拉取详情
   * - services 层还有 Promise 级别去重，保证即便多个组件并行也只发 1 次 HTTP
   */
  const [refSchemaMap, setRefSchemaMap] = useState<Map<string, SchemaDefinition>>(new Map());
  const [refSchemaMapLoading, setRefSchemaMapLoading] = useState(false);
  // 避免重复加载：用 ref 记录上次已处理的根 schema JSON 字符串
  const lastLoadedSigRef = useRef<string>('');

  // 当前 Schema 是否因「已冻结且用户无解锁权限」而只读
  const isLocked = isEdit && frozen && !access.canUnlockSchema;
  // 查看模式或锁定状态均不可编辑
  const readOnly = mode === 'view' || isLocked;
  // 是否允许当前用户切换到编辑模式（查看模式下显示"编辑"按钮）
  const canSwitchToEdit = access.canEditSchema && !isLocked;

  useEffect(() => {
    if (id) {
      setLoading(true);
      getSchema(id, { silent: true })
        .then((data) => {
          const isSchemaFrozen = data.frozen ?? false;
          setFrozen(isSchemaFrozen);
          setScope(data.scope || 'PLATFORM');
          setAppGroup(data.appGroup || '');
          const fmt: SchemaFormat = data.schemaFormat || 'json-schema';
          setSchemaFormat(fmt);
          form.setFieldsValue({
            name: data.schemaName,
            types: (data.schemaType || 'INPUT').split(',').map((t) => t.trim()),
            schemaFormat: fmt,
            description: data.description,
            scope: data.scope || 'PLATFORM',
            appGroup: data.appGroup || '',
          });
          const schema = data.schemaJson ? JSON.parse(data.schemaJson) : {};
          setJsonValue(JSON.stringify(schema, null, 2));
          setFields(jsonSchemaToFields(schema));
          // 已冻结且当前用户无解锁权限时，默认进入只读查看模式
          if (isSchemaFrozen && !access.canUnlockSchema) {
            setMode('view');
          }
        })
        .catch((e) => {
          message.error('加载 Schema 失败：' + (e?.message || '未知错误'));
        })
        .finally(() => setLoading(false));
    } else {
      form.resetFields();
      setFields([]);
      setJsonValue('{}');
      setSchemaFormat('json-schema');
      setFrozen(false);
      setScope('PLATFORM');
      setAppGroup('');
      setMode(queryMode === 'view' ? 'view' : 'form');
    }
  }, [id, access.canUnlockSchema, queryMode]);

  /**
   * BFS 预加载所有层级引用 Schema（递归处理引用的引用），
   * 并发拉详情后塞到 refSchemaMap，三个子组件共享同一份数据。
   */
  useEffect(() => {
    if (!parsedSchema || schemaFormat !== 'json-schema') {
      setRefSchemaMap(new Map());
      lastLoadedSigRef.current = '';
      return;
    }
    const sig = JSON.stringify(parsedSchema);
    if (sig === lastLoadedSigRef.current && refSchemaMap.size > 0) return;
    lastLoadedSigRef.current = sig;
    let cancelled = false;
    setRefSchemaMapLoading(true);

    (async () => {
      const finalMap = new Map<string, SchemaDefinition>();
      const fullyDiscovered = new Set<string>();
      // 先用 services 层内存缓存做热启动：所有已经被任何组件拉过的 schema 直接入 map
      try {
        const memoryCache = getCachedSchemaMap();
        memoryCache.forEach((def, key) => {
          if (def && key !== schemaNameValue) {
            finalMap.set(key, def);
            fullyDiscovered.add(key);
          }
        });
      } catch {
        /* ignore */
      }
      let scanRound = 0;
      while (scanRound < 10) {
        const scanTargets: any[] = [parsedSchema];
        finalMap.forEach((def) => {
          try {
            if (def?.schemaJson) scanTargets.push(JSON.parse(def.schemaJson));
          } catch {
            /* ignore */
          }
        });
        const namesThisRound = new Set<string>();
        scanTargets.forEach((t) => {
          extractSchemaRefs(t).forEach((n) => {
            if (n === schemaNameValue) return;
            if (!fullyDiscovered.has(n)) namesThisRound.add(n);
          });
        });
        if (namesThisRound.size === 0) break;
        const namesArr = Array.from(namesThisRound);
        const results = await Promise.all(
          namesArr.map((name) =>
            getSchema(name, { silent: true }).catch(() => null as SchemaDefinition | null),
          ),
        );
        namesArr.forEach((name, idx) => {
          const def = results[idx];
          fullyDiscovered.add(name);
          if (def) finalMap.set(name, def);
        });
        scanRound++;
      }
      if (cancelled) return;
      setRefSchemaMap(finalMap);
      setRefSchemaMapLoading(false);
    })().catch(() => {
      if (!cancelled) setRefSchemaMapLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [parsedSchema, schemaFormat, schemaNameValue]);

  const syncFromForm = (newFields: any[]) => {
    setFields(newFields);
    setJsonValue(JSON.stringify(fieldsToJsonSchema(newFields), null, 2));
  };

  const syncFromJson = (value: string) => {
    setJsonValue(value);
    try {
      const schema = JSON.parse(value);
      // 仅 JSON Schema 支持表单可视化编辑
      if (schemaFormat === 'json-schema') {
        setFields(jsonSchemaToFields(schema));
      }
    } catch (e) {
      // ignore invalid JSON
    }
  };

  const parsedSchema = useMemo(() => {
    try {
      return JSON.parse(jsonValue || '{}');
    } catch (e) {
      return null;
    }
  }, [jsonValue]);

  const currentPreviewName = previewStack.length > 0 ? previewStack[previewStack.length - 1] : (schemaNameValue || '');

  /**
   * 根据 previewStack 顶的目标 schemaName，解析出传给 SchemaPreview 的 { schema, schemaName, description, etc }。
   * - 如果目标是当前编辑 schema 或栈空：用 parsedSchema / schemaNameValue
   * - 否则从 refSchemaMap 取；取不到就先补一次 getSchema（因为子组件点引用时，引用可能是首次出现，BFS 预加载还没跑完，懒补一次）
   */
  const [previewOverride, setPreviewOverride] = useState<{
    schemaName: string;
    schema: any;
    description?: string | null;
  } | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  useEffect(() => {
    if (!currentPreviewName || currentPreviewName === schemaNameValue) {
      setPreviewOverride(null);
      setPreviewLoading(false);
      return;
    }
    // 目标不是当前编辑中的 schema
    const existed = refSchemaMap.get(currentPreviewName);
    if (existed && existed.schemaJson) {
      try {
        setPreviewOverride({
          schemaName: existed.schemaName || currentPreviewName,
          schema: JSON.parse(existed.schemaJson),
          description: existed.description,
        });
        setPreviewLoading(false);
        return;
      } catch {
        // JSON 解析失败，fallback 到下面空 schema
      }
    }
    // 没拿到：先给用户 loading，然后懒加载一次
    setPreviewOverride(null);
    setPreviewLoading(true);
    let cancelled = false;
    getSchema(currentPreviewName, { silent: true })
      .then((def) => {
        if (cancelled) return;
        let inner: any = {};
        try {
          if (def?.schemaJson) inner = JSON.parse(def.schemaJson);
        } catch {
          inner = {};
        }
        // 顺便塞到 refSchemaMap，后面其它组件直接用
        setRefSchemaMap((prev) => {
          if (prev.has(currentPreviewName)) return prev;
          const n = new Map(prev);
          n.set(currentPreviewName, def);
          return n;
        });
        setPreviewOverride({
          schemaName: def?.schemaName || currentPreviewName,
          schema: inner,
          description: def?.description,
        });
      })
      .catch(() => {
        if (cancelled) return;
        setPreviewOverride({ schemaName: currentPreviewName, schema: null as any });
      })
      .finally(() => {
        if (!cancelled) setPreviewLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [currentPreviewName, refSchemaMap, schemaNameValue]);

  /**
   * 子组件（SchemaFormEditor / SchemaValidator）的引用 Tag 被点击时触发的回调。
   * - 把目标 schemaName 推入 previewStack 顶（如果已经在栈里，跳到对应层级）
   * - 自动打开 Drawer 并切到 preview Tab
   */
  const handlePreviewRef = (refName: string) => {
    if (!refName) return;
    setPreviewStack((prev) => {
      const existedIdx = prev.indexOf(refName);
      if (existedIdx >= 0) return prev.slice(0, existedIdx + 1);
      // 把当前正在编辑的 schema 当作"Home"层，如果是自己本身不用 push
      if (refName === schemaNameValue) return [];
      return [...prev, refName];
    });
    setActiveTab('preview');
    if (!drawerVisible) setDrawerVisible(true);
  };

  /** 在当前预览栈里跳转：idx = -1 表示跳回首页（当前编辑 schema），否则保留 0..idx */
  const jumpPreviewStack = (idx: number) => {
    if (idx < 0) {
      setPreviewStack([]);
      return;
    }
    setPreviewStack((prev) => {
      if (idx >= prev.length) return prev;
      return prev.slice(0, idx + 1);
    });
  };

  const tabItems = useMemo(
    () => [
      {
        key: 'preview',
        label: 'Schema 预览',
        children: (
          schemaFormat !== 'json-schema' ? (
            <Alert
              type="info"
              showIcon
              message={`${SCHEMA_FORMAT_OPTIONS.find((o) => o.value === schemaFormat)?.label || schemaFormat} 暂不支持可视化 Schema 预览`}
            />
          ) : (
            <SchemaPreview
              schema={previewOverride?.schema ?? parsedSchema}
              schemaName={previewOverride?.schemaName ?? schemaNameValue ?? ''}
              refSchemaMap={refSchemaMap}
              loading={refSchemaMapLoading || previewLoading}
            />
          )
        ),
      },
      {
        key: 'validate',
        label: '数据校验',
        children:
          schemaFormat !== 'json-schema' ? (
            <Alert
              type="info"
              showIcon
              message={`${SCHEMA_FORMAT_OPTIONS.find((o) => o.value === schemaFormat)?.label || schemaFormat} 暂不支持可视化数据校验`}
            />
          ) : parsedSchema ? (
            <SchemaValidator
              schema={parsedSchema}
              refSchemaMap={refSchemaMap}
              selfSchemaName={schemaNameValue}
              onPreviewRef={handlePreviewRef}
            />
          ) : (
            <div style={{ color: '#ff4d4f' }}>JSON 格式错误，无法校验</div>
          ),
      },
      {
        key: 'diff',
        label: '版本对比',
        children:
          schemaFormat !== 'json-schema' ? (
            <Alert
              type="info"
              showIcon
              message={`${SCHEMA_FORMAT_OPTIONS.find((o) => o.value === schemaFormat)?.label || schemaFormat} 暂不支持版本对比`}
            />
          ) : parsedSchema ? (
            <SchemaDiffViewer currentSchema={parsedSchema} schemaName={id} />
          ) : (
            <div style={{ color: '#ff4d4f' }}>JSON 格式错误，无法对比</div>
          ),
      },
    ],
    [parsedSchema, id, schemaFormat, refSchemaMap, schemaNameValue, refSchemaMapLoading, previewOverride, previewLoading, handlePreviewRef],
  );

  const handleSave = useClickDebounce(async () => {
    // 防御性检查：即便按钮隐藏了，也阻止被冻结且无权限用户的提交
    if (isLocked) {
      message.error('Schema 已冻结，您暂无 schema:unlock 权限，无法保存修改');
      return;
    }
    if (mode === 'view') {
      message.error('当前为查看模式，无法保存，请先切换到编辑模式');
      return;
    }
    const values = await form.validateFields();
    const fmt: SchemaFormat = values.schemaFormat || 'json-schema';
    let schema: any;
    try {
      schema = fmt === 'json-schema' && mode === 'form' ? fieldsToJsonSchema(fields) : JSON.parse(jsonValue);
    } catch (e) {
      message.error('Schema JSON 格式不合法，请检查后再保存');
      return;
    }

    if (fmt === 'json-schema') {
      // 先对当前 schema 创建「宽容编译版」AJV：
      //   扫描所有 schema:xxx 引用 → addSchema 占位，避免 AJV 抛 can't resolve reference
      //   真实的引用合法性由后端 validateSchemaReferences 保存时兜底校验
      const { ajv: tolerantAjv, refs: schemaRefUris } = createAjvWithSchemaRefPlaceholders(schema);

      // ① AJV 编译校验（结构合法性）
      try {
        tolerantAjv.compile(schema);
      } catch (e) {
        const errMsg = e instanceof Error ? e.message : '未知错误';
        message.error(`Schema 定义不合法：${errMsg}`);
        return;
      }

      // ② Meta-schema 校验（检查 $schema 声明及关键字合规性）
      //    必须用同一个注入了占位的 tolerantAjv，因为 meta 校验内部也会遇到 $ref
      const metaValid = tolerantAjv.validateSchema(schema);
      if (metaValid !== true) {
        const details =
          tolerantAjv.errors?.map((e) => e.message).filter(Boolean).join('；') ||
          'Schema 不符合 JSON Schema 规范';
        message.error(`Schema 元校验失败：${details}`);
        return;
      }

      // ②.5 快速前端存在性检查：若已加载到 schemaOptions（列表 API 返回的所有已注册 Schema 名），
      //      对每一个引用做一次「本地存在」快速判断，减少保存到后端才发现引用不存在的摩擦
      if (schemaRefUris.length > 0) {
        const referencedNames = schemaRefUris
          .filter((r) => r.startsWith('schema:'))
          .map((r) => r.slice(7));
        const selfName = values.name || schemaNameValue;
        const registeredNames = new Set<string>();
        // 可以从 refSchemaMap（已预加载的引用详情 Map）拿到已存在的引用名
        if (refSchemaMap && refSchemaMap.size > 0) {
          refSchemaMap.forEach((_, n) => registeredNames.add(n));
        }
        const missingFrontend = referencedNames.filter(
          (n) => n !== selfName && registeredNames.size > 0 && !registeredNames.has(n),
        );
        if (missingFrontend.length > 0) {
          // 仅做 warning 提醒（避免列表接口还没刷的时候误报），真实拦截交给后端
          message.warning(
            `检测到 ${missingFrontend.length} 个可能不存在的引用：${missingFrontend.join('、')}，` +
              '如确认拼写无误可忽略（保存时后端会做最终合法性拦截）。',
          );
        }
      }

      // ③ 字段名规范校验（所有 property key 必须符合命名规则）
      const invalidNames = collectInvalidPropertyNames(schema);
      if (invalidNames.length > 0) {
        message.error(
          `字段名不合法：${invalidNames.map((n) => `"${n}"`).join('、')}。` +
            '字段名只能包含字母、数字和下划线，且必须以字母或下划线开头',
        );
        return;
      }
    } else if (fmt === 'protobuf') {
      // Protobuf 要求内容能被解析为 FileDescriptorProto，且包含 messageType
      if (!schema || typeof schema !== 'object' || !Array.isArray(schema.messageType)) {
        message.error('Protobuf Schema 必须是一个包含 messageType 数组的 FileDescriptorProto JSON');
        return;
      }
    } else if (fmt === 'avro') {
      // Avro 基础校验：必须包含 type 字段
      if (!schema || typeof schema !== 'object' || !schema.type) {
        message.error('Avro Schema 必须是一个包含 type 字段的 JSON');
        return;
      }
    }

    const data: SchemaDefinition = {
      schemaName: values.name,
      schemaType: (values.types || ['INPUT']).join(','),
      schemaFormat: fmt,
      schemaJson: JSON.stringify(schema),
      description: values.description,
      frozen: isEdit ? frozen : false,
      scope: values.scope || 'PLATFORM',
      appGroup: values.appGroup || undefined,
    };
    if (isEdit) {
      await updateSchema(id!, data);
    } else {
      await createSchema(data);
    }
    message.success('保存成功');
    // 保存后跳转到详情页（查看模式），确保用户立即看到最新状态（含冻结标记）
    const targetName = isEdit ? id : data.schemaName;
    history.push(`/schema/editor/${targetName}?mode=view`);
  });

  return (
    <Spin spinning={loading}>
      <Card
        styles={{ body: { padding: '12px 16px' } }}
        title={
          <Space>
            {isEdit ? (mode === 'view' ? '查看 Schema' : '编辑 Schema') : '新建 Schema'}
            {frozen && (
              <Tooltip title={access.canUnlockSchema ? 'Schema 已冻结，可手动解锁' : 'Schema 已冻结，仅拥有 schema:unlock 权限的用户可编辑'}>
                <Tag icon={<LockOutlined />} color="orange">
                  已冻结
                </Tag>
              </Tooltip>
            )}
          </Space>
        }
        extra={
          <Space>
            {isEdit && access.canUnlockSchema && mode !== 'view' && (
              <Tooltip title={frozen ? '取消冻结以允许普通用户编辑' : '冻结后将禁止无权限用户修改/删除'}>
                <Switch
                  checked={frozen}
                  checkedChildren={<><LockOutlined /> 冻结</>}
                  unCheckedChildren={<><UnlockOutlined /> 正常</>}
                  onChange={setFrozen}
                />
              </Tooltip>
            )}
            {mode === 'view' ? (
              <>
                {isEdit && canSwitchToEdit && (
                  <Button type="primary" icon={<EditOutlined />} onClick={() => setMode('form')}>
                    编辑
                  </Button>
                )}
                {isEdit && !canSwitchToEdit && isLocked && (
                  <Tooltip title="Schema 已冻结，您暂无编辑权限">
                    <Button icon={<EditOutlined />} disabled>编辑</Button>
                  </Tooltip>
                )}
                <Button onClick={() => history.push('/schema')}>返回</Button>
              </>
            ) : (
              <>
                <Radio.Group
                  value={mode}
                  onChange={(e) => {
                    const newMode = e.target.value;
                    if (newMode !== 'view' && !canSwitchToEdit && isEdit) return;
                    // Protobuf / Avro 仅支持 JSON 模式
                    if (newMode === 'form' && schemaFormat !== 'json-schema') {
                      message.warning('Protobuf / Avro Schema 仅支持 JSON 模式编辑');
                      return;
                    }
                    setMode(newMode);
                  }}
                >
                  <Radio.Button value="form" disabled={schemaFormat !== 'json-schema'}>表单模式</Radio.Button>
                  <Radio.Button value="json">JSON 模式</Radio.Button>
                </Radio.Group>
                <Button onClick={() => isEdit ? setMode('view') : history.push('/schema')}>取消</Button>
                {!readOnly && (
                  <Button type="primary" onClick={handleSave}>保存</Button>
                )}
              </>
            )}
          </Space>
        }
      >
        {isLocked && (
          <Alert
            type="warning"
            showIcon
            message="Schema 已冻结"
            description="当前 Schema 已被锁定，您暂无 schema:unlock 权限，无法对其进行编辑或删除。如需修改，请联系管理员解锁。"
            style={{ marginBottom: 16 }}
          />
        )}

        {mode === 'view' ? (
          <>
            <Descriptions column={2} bordered size="small" style={{ marginTop: 8 }}>
              <Descriptions.Item label="名称">{form.getFieldValue('name') || id || '-'}</Descriptions.Item>
              <Descriptions.Item label="类型标签">
                {(form.getFieldValue('types') as string[] | undefined || []).map((t) => (
                  <Tag key={t}>{t}</Tag>
                ))}
              </Descriptions.Item>
              <Descriptions.Item label="Schema 格式">
                {SCHEMA_FORMAT_OPTIONS.find((o) => o.value === form.getFieldValue('schemaFormat'))?.label || 'JSON Schema'}
              </Descriptions.Item>
              <Descriptions.Item label="描述">{form.getFieldValue('description') || '-'}</Descriptions.Item>
              <Descriptions.Item label="作用域">
                {form.getFieldValue('scope') === 'PRIVATE' ? '应用私有 (PRIVATE)' : '平台通用 (PLATFORM)'}
              </Descriptions.Item>
              {form.getFieldValue('scope') === 'PRIVATE' && (
                <Descriptions.Item label="所属应用分组">{form.getFieldValue('appGroup') || '-'}</Descriptions.Item>
              )}
            </Descriptions>
            <div style={{ marginTop: 16 }}>
              {schemaFormat === 'json-schema' ? (
                <SchemaPreview
                  schema={parsedSchema}
                  schemaName={form.getFieldValue('name') || id || ''}
                  refSchemaMap={refSchemaMap}
                  loading={refSchemaMapLoading}
                />
              ) : (
                <pre style={{ padding: '8px 12px', background: '#f6f8fa', borderRadius: 6, border: '1px solid #e8e8e8', fontSize: 12, maxHeight: 480, overflow: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                  {jsonValue}
                </pre>
              )}
            </div>
          </>
        ) : (
          <>
            <Form form={form} layout="vertical" initialValues={{ types: ['INPUT'] }}>
              <Row gutter={16} align="top">
                <Col flex="auto">
                  <Form.Item name="name" label="名称" rules={[{ required: !readOnly }]} style={{ marginBottom: 12 }}>
                    <Input placeholder="请输入 Schema 名称" disabled={readOnly} />
                  </Form.Item>
                </Col>
                <Col flex="none">
                  <Form.Item
                    name="types"
                    label="类型标签"
                    rules={[{ required: !readOnly, message: '请至少选择一个类型' }]}
                    style={{ marginBottom: 12 }}
                    tooltip="可多选，同一个 Schema 可同时用于入参和出参"
                  >
                    <Checkbox.Group options={SCHEMA_TYPE_OPTIONS} disabled={readOnly} />
                  </Form.Item>
                </Col>
                <Col flex="none">
                  <Form.Item
                    name="schemaFormat"
                    label="Schema 格式"
                    rules={[{ required: !readOnly }]}
                    style={{ marginBottom: 12 }}
                    tooltip="JSON Schema 支持表单和 JSON 两种编辑模式；Protobuf / Avro 仅支持 JSON 模式"
                  >
                    <Select
                      options={SCHEMA_FORMAT_OPTIONS}
                      disabled={readOnly}
                      onChange={(value: SchemaFormat) => {
                        setSchemaFormat(value);
                        // 切换到非 JSON Schema 时，如果当前是表单模式则自动切到 JSON 模式
                        if (value !== 'json-schema' && mode === 'form') {
                          setMode('json');
                        }
                        // 新建 Protobuf Schema 时给默认示例
                        if (value === 'protobuf' && !isEdit && jsonValue === '{}') {
                          setJsonValue(DEFAULT_PROTOBUF_SCHEMA);
                        }
                      }}
                    />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item name="description" label="描述" style={{ marginBottom: 8 }}>
                <Input.TextArea
                  autoSize={{ minRows: 1, maxRows: 4 }}
                  placeholder="简要描述该 Schema 的用途"
                  disabled={readOnly}
                />
              </Form.Item>
              <Row gutter={16} align="top">
                <Col flex="auto">
                  <Form.Item name="scope" label="作用域" style={{ marginBottom: 12 }}>
                    <Select
                      disabled={readOnly}
                      options={[
                        { label: '平台通用 (PLATFORM)', value: 'PLATFORM' },
                        { label: '应用私有 (PRIVATE)', value: 'PRIVATE' },
                      ]}
                      onChange={(v) => setScope(v)}
                    />
                  </Form.Item>
                </Col>
                {scope === 'PRIVATE' && (
                  <Col flex="auto">
                    <Form.Item
                      name="appGroup"
                      label="所属应用分组"
                      rules={[{ required: scope === 'PRIVATE' && !readOnly, message: 'PRIVATE 作用域必须指定应用分组' }]}
                      style={{ marginBottom: 12 }}
                    >
                      <Input placeholder="请输入应用分组名称" disabled={readOnly} />
                    </Form.Item>
                  </Col>
                )}
              </Row>
            </Form>

            {mode === 'form' ? (
              <>
                <div style={{ marginTop: 12 }}>
                  <SchemaFormEditor
                    fields={fields}
                    onChange={syncFromForm}
                    schemaName={schemaNameValue}
                    readOnly={readOnly}
                    refSchemaMap={refSchemaMap}
                    onPreviewRef={handlePreviewRef}
                  />
                </div>
                {!readOnly && (
                  <FloatButton
                    tooltip="Schema 预览 / 校验 / 对比"
                    icon={<EyeOutlined />}
                    onClick={() => {
                      setDrawerVisible(true);
                      setActiveTab((t) => t || 'preview');
                    }}
                    style={{ right: 24, bottom: 24 }}
                  />
                )}
                <Drawer
                  title={
                    <Space direction="vertical" size={4} style={{ width: '100%' }}>
                      <Space size={4}>
                        <EyeOutlined style={{ color: '#1677ff' }} />
                        <strong>Schema 辅助面板</strong>
                      </Space>
                      <Breadcrumb
                        style={{ marginTop: 2 }}
                        items={[
                          {
                            title: (
                              <Space size={4} style={{ cursor: 'pointer', color: previewStack.length === 0 ? '#1677ff' : undefined, fontWeight: previewStack.length === 0 ? 600 : 400 }} onClick={() => jumpPreviewStack(-1)}>
                                <HomeOutlined />
                                编辑中：{schemaNameValue || '（未命名）'}
                              </Space>
                            ),
                          },
                          ...previewStack.map((n, idx) => ({
                            title: (
                              <Space
                                size={4}
                                style={{
                                  cursor: 'pointer',
                                  color: idx === previewStack.length - 1 ? '#1677ff' : undefined,
                                  fontWeight: idx === previewStack.length - 1 ? 600 : 400,
                                }}
                                onClick={() => jumpPreviewStack(idx)}
                              >
                                {idx === previewStack.length - 1 && <LinkOutlined style={{ color: '#6366f1' }} />}
                                {idx < previewStack.length - 1 ? n : <strong>{n}</strong>}
                              </Space>
                            ),
                          })),
                        ]}
                      />
                    </Space>
                  }
                  placement="right"
                  width={820}
                  open={drawerVisible}
                  onClose={() => setDrawerVisible(false)}
                  extra={
                    <Space size={8}>
                    {previewStack.length > 0 && (
                      <Button
                        size="small"
                        icon={<ArrowLeftOutlined />}
                        onClick={() => jumpPreviewStack(previewStack.length - 2)}
                      >
                        返回上一级
                      </Button>
                    )}
                    {currentPreviewName && currentPreviewName !== schemaNameValue && (
                      <Button
                        size="small"
                        icon={<ExportOutlined />}
                        onClick={() => window.open(`/schema/editor/${encodeURIComponent(currentPreviewName)}?mode=view`, '_blank', 'noopener,noreferrer')}
                      >
                        新标签打开
                      </Button>
                    )}
                  </Space>
                  }
                >
                  <Tabs
                    activeKey={activeTab}
                    onChange={(k) => setActiveTab(k)}
                    items={tabItems}
                  />
                </Drawer>
              </>
            ) : (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 480px',
                  gap: 16,
                  marginTop: 12,
                  alignItems: 'start',
                }}
              >
                <SchemaJsonEditor value={jsonValue} onChange={syncFromJson} readOnly={readOnly} />
                <div style={{ maxHeight: 'calc(100vh - 300px)', overflow: 'auto' }}>
                  <Tabs defaultActiveKey="validate" items={tabItems} />
                </div>
              </div>
            )}
          </>
        )}
      </Card>
    </Spin>
  );
};

export default SchemaEditor;
