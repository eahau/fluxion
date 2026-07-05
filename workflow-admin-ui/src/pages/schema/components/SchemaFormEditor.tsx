import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popover,
  Radio,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Alert,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  LinkOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
  UnorderedListOutlined,
  BranchesOutlined,
  EyeOutlined,
  ExportOutlined,
} from '@ant-design/icons';
import JsonEditor from '@/components/JsonEditor';
import type { SchemaField } from '@/types/schema';
import type { SchemaDefinition } from '@/types/schema';
import { getSchemas, getSchema } from '@/services/schema';
import { SCHEMA_REF_PREFIX, collectTopLevelFields, type TopLevelField } from '@/utils/jsonSchema';
import { SchemaFieldPreview } from './SchemaFieldPreview';
import { useClickDebounce } from '@/utils/useClickDebounce';

interface SchemaFormEditorProps {
  fields: SchemaField[];
  onChange: (fields: SchemaField[]) => void;
  /** 当前 Schema 名称，用于 object 类型支持自引用 */
  schemaName?: string;
  /** 是否只读（查看模式或无权限编辑被冻结的 Schema） */
  readOnly?: boolean;
  /** 父级（editor.tsx）统一预加载的所有引用详情，优先复用以避免重复请求 */
  refSchemaMap?: Map<string, SchemaDefinition>;
  /** 引用 Tag/按钮被点击时触发：父级在辅助面板中打开该引用的 SchemaPreview */
  onPreviewRef?: (refName: string) => void;
}

const FIELD_TYPES = [
  { value: 'string', label: 'string（字符串）' },
  { value: 'integer', label: 'integer（整数）' },
  { value: 'number', label: 'number（数值）' },
  { value: 'boolean', label: 'boolean（布尔）' },
  { value: 'array', label: 'array（数组）' },
  { value: 'object', label: 'object（对象）' },
];

/** string 类型可用的 format / 业务类型映射 */
const STRING_FORMATS = [
  { value: '', label: '默认字符串' },
  { value: 'email', label: 'email（邮箱）' },
  { value: 'date', label: 'date（日期）' },
  { value: 'date-time', label: 'date-time（日期时间）' },
  { value: 'time', label: 'time（时间）' },
  { value: 'uri', label: 'uri（URL）' },
  { value: 'regex', label: 'regex（正则表达式）' },
  { value: 'phone', label: 'phone（手机号）' },
  { value: 'idcard', label: 'idcard（身份证）' },
];

/** 业务类型对应的 JSON Schema pattern（当 format 为这些值时自动填充） */
const BUSINESS_TYPE_PATTERNS: Record<string, string> = {
  phone: '^1[3-9]\\d{9}$',
  idcard: '^\\d{17}[\\dXx]$',
};

/** 将内部业务 format（如 phone）还原为 JSON Schema 标准 format */
function toJsonFormat(value: string | undefined): string | undefined {
  if (!value || value === 'phone' || value === 'idcard') return undefined;
  return value;
}

const COMPACT = { marginBottom: 12 };

interface RefPreviewInfo {
  loading: boolean;
  schema?: SchemaDefinition;
  fields?: TopLevelField[];
  totalFieldCount?: number;
  structuralNote?: string;
  error?: string;
}

const SchemaFormEditor: React.FC<SchemaFormEditorProps> = ({
  fields,
  onChange,
  schemaName,
  readOnly = false,
  refSchemaMap,
  onPreviewRef,
}) => {
  const [form] = Form.useForm();
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const type = Form.useWatch('type', form);
  const schemaRefMode = Form.useWatch('schemaRefMode', form);
  const itemsSchemaRefMode = Form.useWatch('itemsSchemaRefMode', form);

  /**
   * 从任意结构的 schema 定义里提取「直接顶层字段」列表，用于 hover 预览小卡片。
   * - 用 collectTopLevelFields：只拿 1 层，不把嵌套铺成一长串路径
   * - object / ref / 复杂数组字段 会带 innerSchemaForPreview，交给 UI 包 Popover 递归预览
   * - 顺带生成 structuralNote 结构描述（顶层是数组 / 组合 / 顶层引用）
   */
  const extractFieldsFromDef = (schemaDef: SchemaDefinition) => {
    const result: {
      fields: TopLevelField[];
      totalCount: number;
      note?: string;
    } = { fields: [], totalCount: 0 };
    let inner: any = null;
    try {
      inner = schemaDef.schemaJson ? JSON.parse(schemaDef.schemaJson) : null;
    } catch {
      return result;
    }
    if (!inner) return result;

    const list = collectTopLevelFields(inner, refSchemaMap ?? new Map());
    result.fields = list;
    result.totalCount = list.length;

    if (typeof inner === 'object') {
      const parts: string[] = [];
      if (
        (inner.type === 'array' && inner.items) ||
        (inner.items && typeof inner.items === 'object' && !inner.properties)
      ) {
        parts.push('顶层为数组');
      }
      if (Array.isArray(inner.allOf)) parts.push(`allOf 组合（${inner.allOf.length} 段）`);
      if (Array.isArray(inner.anyOf)) parts.push(`anyOf 组合（${inner.anyOf.length} 段）`);
      if (Array.isArray(inner.oneOf)) parts.push(`oneOf 组合（${inner.oneOf.length} 段）`);
      if (typeof inner.$ref === 'string' && inner.$ref.startsWith(SCHEMA_REF_PREFIX)) {
        parts.push(`顶层本身为引用 ${inner.$ref}`);
      }
      if (parts.length) result.note = parts.join('；');
    }
    return result;
  };

  // ── 加载已注册的 Schema 列表，供 object 类型引用 ──
  const [schemaOptions, setSchemaOptions] = useState<{ value: string; label: string }[]>([]);
  const [allSchemaNames, setAllSchemaNames] = useState<Set<string>>(new Set());
  const [refPreviews, setRefPreviews] = useState<Record<string, RefPreviewInfo>>({});

  /**
   * 父级（editor）统一预加载了所有引用详情后，直接同步到本地 refPreviews，
   * 保证悬浮预览一上来就有内容，不需要再发 HTTP。
   */
  useEffect(() => {
    if (!refSchemaMap || refSchemaMap.size === 0) return;
    setRefPreviews((prev) => {
      const next = { ...prev };
      refSchemaMap.forEach((def, name) => {
        if (!next[name] && def) {
          const parsed = extractFieldsFromDef(def);
          next[name] = {
            loading: false,
            schema: def,
            fields: parsed.fields,
            totalFieldCount: parsed.totalCount,
            structuralNote: parsed.note,
          };
        }
      });
      return next;
    });
  }, [refSchemaMap]);

  useEffect(() => {
    getSchemas({ pageSize: 500 }, { silent: true })
      .then((res) => {
        const list: SchemaDefinition[] = res?.list ?? [];
        const options = list.map((s: SchemaDefinition) => ({
          value: s.schemaName!,
          label: s.schemaName!,
        }));
        const names = new Set(list.map((s) => s.schemaName!));
        // 确保当前 Schema 自身在列表中，支持自引用
        if (schemaName) {
          if (!names.has(schemaName)) {
            options.unshift({ value: schemaName, label: `${schemaName}（自身）` });
            names.add(schemaName);
          }
        }
        setSchemaOptions(options);
        setAllSchemaNames(names);
      })
      .catch(() => {
        // 即使 API 失败，也要保证当前 Schema 可选
        if (schemaName) {
          setSchemaOptions([{ value: schemaName, label: `${schemaName}（自身）` }]);
          setAllSchemaNames(new Set([schemaName]));
        } else {
          setSchemaOptions([]);
          setAllSchemaNames(new Set());
        }
      });
  }, [schemaName]);

  /** 加载单个被引用 schema 的预览信息（用于悬浮预览） */
  const loadRefPreview = useCallback((refName: string) => {
    if (refPreviews[refName]) return;
    // 优先走父级传过来的统一缓存
    const fromProp = refSchemaMap?.get(refName);
    if (fromProp) {
      setRefPreviews((prev) => {
        if (prev[refName]) return prev;
        const parsed = extractFieldsFromDef(fromProp);
        return {
          ...prev,
          [refName]: {
            loading: false,
            schema: fromProp,
            fields: parsed.fields,
            totalFieldCount: parsed.totalCount,
            structuralNote: parsed.note,
          },
        };
      });
      return;
    }
    setRefPreviews((prev) => ({ ...prev, [refName]: { loading: true } }));
    getSchema(refName, { silent: true })
      .then((schemaDef) => {
        const parsed = extractFieldsFromDef(schemaDef);
        setRefPreviews((prev) => ({
          ...prev,
          [refName]: {
            loading: false,
            schema: schemaDef,
            fields: parsed.fields,
            totalFieldCount: parsed.totalCount,
            structuralNote: parsed.note,
          },
        }));
      })
      .catch((e: any) => {
        setRefPreviews((prev) => ({
          ...prev,
          [refName]: { loading: false, error: e?.message || '加载失败' },
        }));
      });
  }, [refPreviews, refSchemaMap]);

  const dataSource = useMemo(
    () => (fields || []).map((f, idx) => ({ ...f, key: idx })),
    [fields],
  );

  const inferStringFormat = (field: SchemaField): string => {
    if (field.format) {
      const found = STRING_FORMATS.find((f) => f.value === field.format);
      if (found) return field.format;
    }
    if (field.pattern) {
      if (field.pattern === BUSINESS_TYPE_PATTERNS.phone) return 'phone';
      if (field.pattern === BUSINESS_TYPE_PATTERNS.idcard) return 'idcard';
    }
    return '';
  };

  const openModal = (field: SchemaField, idx?: number) => {
    if (readOnly) return;
    setEditingIndex(idx ?? null);

    // 推断 array items 是否引用已有 Schema
    const itemsRef =
      typeof field.items === 'object' && field.items?.$ref?.startsWith(SCHEMA_REF_PREFIX)
        ? field.items.$ref.slice(SCHEMA_REF_PREFIX.length)
        : undefined;

    form.setFieldsValue({
      name: field.name,
      type: field.type,
      required: !!field.required,
      description: field.description,
      format: field.type === 'string' ? inferStringFormat(field) : undefined,
      minimum: field.minimum,
      maximum: field.maximum,
      minLength: field.minLength,
      maxLength: field.maxLength,
      pattern: field.pattern,
      enum: field.enum,
      items: itemsRef ? undefined : field.items,
      properties: field.properties,
      // 若有 schemaRef 则默认进入「引用」模式
      schemaRefMode: field.schemaRef ? 'ref' : 'inline',
      schemaRef: field.schemaRef,
      // array items 引用模式
      itemsSchemaRefMode: itemsRef ? 'ref' : 'inline',
      itemsSchemaRef: itemsRef,
    });
    setModalVisible(true);
  };

  /**
   * 校验引用的 schema 是否存在（前端二次校验，作为后端第一道防线的补充）
   */
  const validateRefExists = (refName: string): boolean => {
    if (!refName) return true;
    // 自引用允许
    if (schemaName && refName === schemaName) return true;
    return allSchemaNames.has(refName);
  };

  const handleAdd = useClickDebounce(() => {
    if (readOnly) return;
    openModal({ name: '', type: 'string', required: false, schemaRefMode: 'inline' } as any);
  });

  const handleSave = useClickDebounce(async () => {
    if (readOnly) {
      message.error('当前 Schema 为只读状态，无法保存修改');
      return;
    }
    const values = await form.validateFields();

    // 二次校验字段名重复
    const isDuplicate = (fields || []).some(
      (f, idx) => f.name === values.name && idx !== editingIndex,
    );
    if (isDuplicate) {
      message.error(`字段名 "${values.name}" 已存在，请使用其他名称`);
      return;
    }

    // ── 前端引用合法性校验 ──
    if (values.type === 'object' && values.schemaRefMode === 'ref' && values.schemaRef) {
      if (!validateRefExists(values.schemaRef)) {
        message.error(
          `引用校验失败：Schema「${values.schemaRef}」不存在。请先创建该 Schema，或修正引用关系。`,
        );
        return;
      }
    }
    if (values.type === 'array' && values.itemsSchemaRefMode === 'ref' && values.itemsSchemaRef) {
      if (!validateRefExists(values.itemsSchemaRef)) {
        message.error(
          `引用校验失败：Schema「${values.itemsSchemaRef}」不存在。请先创建该 Schema，或修正引用关系。`,
        );
        return;
      }
    }

    const newField: SchemaField = {
      name: values.name,
      type: values.type,
      required: !!values.required,
      description: values.description,
    };
    if (values.type === 'string' && values.format) {
      const jsonFormat = toJsonFormat(values.format);
      if (jsonFormat) newField.format = jsonFormat;
    }
    if (values.minimum !== undefined && values.minimum !== null) {
      newField.minimum = values.minimum;
    }
    if (values.maximum !== undefined && values.maximum !== null) {
      newField.maximum = values.maximum;
    }
    if (values.minLength !== undefined && values.minLength !== null) {
      newField.minLength = values.minLength;
    }
    if (values.maxLength !== undefined && values.maxLength !== null) {
      newField.maxLength = values.maxLength;
    }
    if (values.pattern) newField.pattern = values.pattern;
    if (values.enum?.length) newField.enum = values.enum;

    // array 类型：区分「引用 Schema」与「内联定义」
    if (values.type === 'array') {
      if (values.itemsSchemaRefMode === 'ref' && values.itemsSchemaRef) {
        newField.items = { $ref: `${SCHEMA_REF_PREFIX}${values.itemsSchemaRef}` };
      } else if (values.items) {
        newField.items = values.items;
      }
    }

    // object 类型：区分「引用 Schema」与「内联定义」
    if (values.type === 'object' && values.schemaRefMode === 'ref' && values.schemaRef) {
      newField.schemaRef = values.schemaRef;
    } else if (values.properties) {
      newField.properties = values.properties;
    }

    const list = [...(fields || [])];
    if (editingIndex !== null) {
      list[editingIndex] = newField;
    } else {
      list.push(newField);
    }
    onChange(list);
    setModalVisible(false);
  });

  const handleDelete = useClickDebounce((idx: number) => {
    if (readOnly) {
      message.error('当前 Schema 为只读状态，无法删除字段');
      return;
    }
    const list = [...fields];
    list.splice(idx, 1);
    onChange(list);
  });

  const move = (idx: number, direction: number) => {
    if (readOnly) return;
    const list = [...fields];
    const target = idx + direction;
    if (target < 0 || target >= list.length) return;
    [list[idx], list[target]] = [list[target], list[idx]];
    onChange(list);
  };

  const validateUniqueName = useCallback(
    (_: any, value: string) => {
      if (!value) return Promise.resolve();
      const isDuplicate = (fields || []).some(
        (f, idx) => f.name === value && idx !== editingIndex,
      );
      if (isDuplicate) {
        return Promise.reject(new Error(`字段名 "${value}" 已存在`));
      }
      return Promise.resolve();
    },
    [fields, editingIndex],
  );

  /** 渲染引用标签（带悬浮预览） */
  const renderRefTag = (refName: string, isArray: boolean = false) => {
    const displayName = isArray ? `[${refName}]` : refName;
    const isValid = validateRefExists(refName);
    const color = isValid ? '#6366f1' : '#ff4d4f';
    const icon = isValid ? <LinkOutlined /> : <ExclamationCircleOutlined />;

    const handleClick = (e?: React.MouseEvent) => {
      e?.stopPropagation?.();
      if (!isValid) return;
      if (typeof onPreviewRef === 'function') onPreviewRef(refName);
    };

    const tag = (
      <Tag
        color={color}
        icon={icon}
        style={{ cursor: isValid ? 'pointer' : 'help', userSelect: 'none' }}
        onClick={handleClick}
      >
        {displayName}
        {!isValid && !readOnly && <span style={{ marginLeft: 4 }}>（不存在）</span>}
      </Tag>
    );

    const preview = refPreviews[refName];

    const popoverContent =
      !isValid ? (
        <Alert
          type="error"
          showIcon
          message="引用不存在"
          description={`Schema「${refName}」不存在，保存时将被后端拒绝。请先创建该 Schema，或修正引用。`}
        />
      ) : preview?.loading ? (
        <div style={{ padding: '12px 16px', color: '#8c8c8c' }}>正在加载预览...</div>
      ) : preview?.error ? (
        <Alert
          type="warning"
          showIcon
          message="预览加载失败"
          description={preview.error}
        />
      ) : preview?.schema ? (
        <div style={{ minWidth: 320 }}>
          <Descriptions column={1} size="small" bordered style={{ marginBottom: 10 }}>
            <Descriptions.Item label="名称">
              <Space>
                <FileTextOutlined style={{ color: '#6366f1' }} />
                {preview.schema.schemaName}
                {preview.schema.frozen && <Tag color="orange">已冻结</Tag>}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="类型">
              <Space size={4}>
                {(preview.schema.schemaType || 'INPUT')
                  .split(',')
                  .map((t) => t.trim())
                  .filter(Boolean)
                  .map((t) => (
                    <Tag key={t} style={{ margin: 0 }}>
                      {t}
                    </Tag>
                  ))}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="格式">
              <Space size={4}>
                <span>{preview.schema.schemaFormat}</span>
                {preview.schema.scope === 'PRIVATE' ? (
                  <Tag color="purple" style={{ margin: 0 }}>
                    私有{preview.schema.appGroup ? `· ${preview.schema.appGroup}` : ''}
                  </Tag>
                ) : (
                  <Tag color="default" style={{ margin: 0 }}>平台</Tag>
                )}
              </Space>
            </Descriptions.Item>
            {preview.structuralNote && (
              <Descriptions.Item label="结构">
                <Tag color="#6366f1" icon={<BranchesOutlined />} style={{ margin: 0 }}>
                  {preview.structuralNote}
                </Tag>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="字段摘要">
              <Space size={6} wrap>
                <Tag color="blue" icon={<UnorderedListOutlined />} style={{ margin: 0 }}>
                  共 {preview.totalFieldCount ?? 0} 个顶层字段
                </Tag>
                {preview.fields && preview.fields.length > (preview.totalFieldCount ?? 0) && (
                  <Tag color="default" style={{ margin: 0 }}>
                    仅展示前 {preview.fields.length} 项
                  </Tag>
                )}
              </Space>
            </Descriptions.Item>
            {preview.schema.description && (
              <Descriptions.Item label="描述">
                <div style={{ whiteSpace: 'pre-wrap', maxHeight: 120, overflow: 'auto' }}>
                  {preview.schema.description}
                </div>
              </Descriptions.Item>
            )}
          </Descriptions>
          {typeof onPreviewRef === 'function' ? (
            <Space size={8} wrap style={{ marginTop: 2 }}>
              <Button type="primary" size="small" icon={<EyeOutlined />} onClick={() => onPreviewRef(refName)}>
                在右侧预览中打开
              </Button>
              <Button
                size="small"
                icon={<ExportOutlined />}
                onClick={() => {
                  window.open(
                    `/schema/editor/${encodeURIComponent(refName)}?mode=view`,
                    '_blank',
                    'noopener,noreferrer',
                  );
                }}
              >
                新标签页打开
              </Button>
              <Tooltip title="字段详情：递归展开查看、复制完整 JSON、引用关系图等功能已内置到右侧 Schema 预览面板，不再在小弹层中重复展示">
                <InfoCircleOutlined style={{ color: '#8c8c8c' }} />
              </Tooltip>
            </Space>
          ) : (
            <div style={{ marginTop: 2 }}>
              {preview.fields && preview.fields.length > 0 ? (
                <SchemaFieldPreview
                  fields={preview.fields}
                  refSchemaMap={refSchemaMap ?? new Map()}
                  showHeader
                  maxPopoverDepth={0}
                />
              ) : (
                <Alert
                  type="info"
                  showIcon
                  icon={<InfoCircleOutlined />}
                  message="未检测到顶层字段"
                  description="该引用 Schema 可能仅包含 allOf/anyOf 组合逻辑，或需要进一步展开引用才能看到字段列表。"
                />
              )}
            </div>
          )}
        </div>
      ) : (
        // hover 触发时会立刻调用 onOpenChange 开始加载，这里用 loading 文案兜底一次渲染
        <div style={{ padding: '12px 16px', color: '#8c8c8c' }}>
          {isValid ? '正在加载预览...' : '引用校验失败'}
        </div>
      );

    return (
      <Popover
        title={
          <Space>
            <LinkOutlined style={{ color: '#6366f1' }} />
            <span style={{ fontWeight: 500 }}>
              {isArray ? `数组元素引用 Schema：${refName}` : `引用 Schema：${refName}`}
            </span>
          </Space>
        }
        content={popoverContent}
        trigger="hover"
        mouseEnterDelay={0.15}
        mouseLeaveDelay={0.6}
        onOpenChange={(open) => {
          if (open && isValid && !refPreviews[refName]) {
            loadRefPreview(refName);
          }
        }}
      >
        {tag}
      </Popover>
    );
  };

  const columns = [
    { title: '字段名', dataIndex: 'name', key: 'name', width: 180 },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 280,
      render: (_: any, record: SchemaField) => {
        const formatLabel = record.format
          ? STRING_FORMATS.find((f) => f.value === record.format)?.label
          : undefined;
        const itemsRef =
          typeof record.items === 'object' && record.items?.$ref?.startsWith(SCHEMA_REF_PREFIX)
            ? record.items.$ref.slice(SCHEMA_REF_PREFIX.length)
            : undefined;
        return (
          <Space size={4} wrap>
            <Tag>{record.type}</Tag>
            {formatLabel && <Tag color="blue">{formatLabel}</Tag>}
            {record.pattern && !record.format && <Tag color="purple">正则</Tag>}
            {record.enum && <Tag color="cyan">枚举</Tag>}
            {record.schemaRef && renderRefTag(record.schemaRef, false)}
            {itemsRef && renderRefTag(itemsRef, true)}
          </Space>
        );
      },
    },
    {
      title: '必填',
      dataIndex: 'required',
      key: 'required',
      width: 70,
      render: (v: boolean) => (v ? <Tag color="red">是</Tag> : <Tag>否</Tag>),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_: any, record: SchemaField & { key: number }) => (
        <Space size={4}>
          <Button
            size="small"
            icon={<ArrowUpOutlined />}
            disabled={record.key === 0 || readOnly}
            onClick={() => move(record.key, -1)}
          />
          <Button
            size="small"
            icon={<ArrowDownOutlined />}
            disabled={record.key === fields.length - 1 || readOnly}
            onClick={() => move(record.key, 1)}
          />
          <Button
            size="small"
            icon={<EditOutlined />}
            disabled={readOnly}
            onClick={() => openModal(record, record.key)}
          />
          <Button
            size="small"
            danger
            icon={<DeleteOutlined />}
            disabled={readOnly}
            onClick={() => handleDelete(record.key)}
          />
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Button
          type="dashed"
          icon={<PlusOutlined />}
          onClick={handleAdd}
          disabled={readOnly}
        >
          添加字段
        </Button>
        {readOnly && (
          <Tag color="orange" icon={<FileTextOutlined />}>
            只读模式（Schema 已冻结或处于查看状态）
          </Tag>
        )}
      </div>
      <Table
        columns={readOnly ? columns.filter((c) => c.key !== 'action') : columns}
        dataSource={dataSource}
        pagination={false}
        size="small"
        bordered
        scroll={{ x: 640 }}
      />
      <Modal
        title={editingIndex !== null ? '编辑字段' : '新增字段'}
        open={modalVisible}
        onOk={handleSave}
        onCancel={() => setModalVisible(false)}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="字段名"
            rules={[
              { required: true, message: '请输入字段名' },
              {
                pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/,
                message: '字段名只能包含字母、数字和下划线，且必须以字母或下划线开头',
              },
              { validator: validateUniqueName },
            ]}
            style={COMPACT}
          >
            <Input placeholder="如 id" disabled={readOnly} />
          </Form.Item>
          <Form.Item
            name="type"
            label="类型"
            rules={[{ required: true, message: '请选择类型' }]}
            style={COMPACT}
          >
            <Select options={FIELD_TYPES} disabled={readOnly} />
          </Form.Item>
          <Form.Item
            name="required"
            label="必填"
            valuePropName="checked"
            style={COMPACT}
          >
            <Switch checkedChildren="是" unCheckedChildren="否" disabled={readOnly} />
          </Form.Item>
          <Form.Item name="description" label="描述" style={COMPACT}>
            <Input.TextArea
              autoSize={{ minRows: 2, maxRows: 4 }}
              placeholder="字段说明"
              disabled={readOnly}
            />
          </Form.Item>
          {(type === 'integer' || type === 'number') && (
            <>
              <Form.Item name="minimum" label="最小值" style={COMPACT}>
                <InputNumber
                  style={{ width: '100%' }}
                  placeholder="minimum"
                  disabled={readOnly}
                />
              </Form.Item>
              <Form.Item name="maximum" label="最大值" style={COMPACT}>
                <InputNumber
                  style={{ width: '100%' }}
                  placeholder="maximum"
                  disabled={readOnly}
                />
              </Form.Item>
            </>
          )}
          {type === 'string' && (
            <>
              <Form.Item name="format" label="字符串格式 / 业务类型" style={COMPACT}>
                <Select
                  allowClear
                  placeholder="选择格式或业务类型"
                  options={STRING_FORMATS}
                  disabled={readOnly}
                  onChange={(value) => {
                    if (value && BUSINESS_TYPE_PATTERNS[value as string]) {
                      form.setFieldsValue({ pattern: BUSINESS_TYPE_PATTERNS[value as string] });
                    }
                  }}
                />
              </Form.Item>
              <Form.Item name="enum" label="枚举值（可选）" style={COMPACT}>
                <Select
                  mode="tags"
                  allowClear
                  placeholder="输入后按回车添加枚举值"
                  tokenSeparators={[',']}
                  disabled={readOnly}
                />
              </Form.Item>
              <Form.Item name="minLength" label="最小长度" style={COMPACT}>
                <InputNumber
                  style={{ width: '100%' }}
                  placeholder="minLength"
                  disabled={readOnly}
                />
              </Form.Item>
              <Form.Item name="maxLength" label="最大长度" style={COMPACT}>
                <InputNumber
                  style={{ width: '100%' }}
                  placeholder="maxLength"
                  disabled={readOnly}
                />
              </Form.Item>
              <Form.Item name="pattern" label="正则表达式" style={COMPACT}>
                <Input placeholder="pattern" disabled={readOnly} />
              </Form.Item>
            </>
          )}
          {type === 'array' && (
            <>
              <Form.Item
                name="itemsSchemaRefMode"
                label="数组元素定义方式"
                style={COMPACT}
                initialValue="inline"
              >
                <Radio.Group
                  disabled={readOnly}
                  onChange={(e) => {
                    if (e.target.value === 'ref') {
                      form.setFieldsValue({ items: undefined });
                    } else {
                      form.setFieldsValue({ itemsSchemaRef: undefined });
                    }
                  }}
                >
                  <Radio.Button value="inline">内联定义</Radio.Button>
                  <Radio.Button value="ref">
                    <LinkOutlined style={{ marginRight: 4 }} />
                    引用已有 Schema
                  </Radio.Button>
                </Radio.Group>
              </Form.Item>

              {itemsSchemaRefMode === 'ref' ? (
                <Form.Item
                  name="itemsSchemaRef"
                  label="引用 Schema"
                  rules={[{ required: true, message: '请选择要引用的 Schema' }]}
                  style={COMPACT}
                >
                  <Select
                    showSearch
                    placeholder="选择已注册的 Schema"
                    options={schemaOptions}
                    filterOption={(input, option) =>
                      (option?.value ?? '').toLowerCase().includes(input.toLowerCase())
                    }
                    allowClear
                    disabled={readOnly}
                  />
                </Form.Item>
              ) : (
                <Form.Item name="items" label="数组元素 Schema" style={COMPACT}>
                  <JsonEditor height={160} readOnly={readOnly} />
                </Form.Item>
              )}
            </>
          )}
          {type === 'object' && (
            <>
              <Form.Item
                name="schemaRefMode"
                label="嵌套对象定义方式"
                style={COMPACT}
                initialValue="inline"
              >
                <Radio.Group
                  disabled={readOnly}
                  onChange={(e) => {
                    // 切换模式时清空对方的值，避免脏数据
                    if (e.target.value === 'ref') {
                      form.setFieldsValue({ properties: undefined });
                    } else {
                      form.setFieldsValue({ schemaRef: undefined });
                    }
                  }}
                >
                  <Radio.Button value="inline">内联定义</Radio.Button>
                  <Radio.Button value="ref">
                    <LinkOutlined style={{ marginRight: 4 }} />
                    引用已有 Schema
                  </Radio.Button>
                </Radio.Group>
              </Form.Item>

              {schemaRefMode === 'ref' ? (
                <Form.Item
                  name="schemaRef"
                  label="引用 Schema"
                  rules={[{ required: true, message: '请选择要引用的 Schema' }]}
                  style={COMPACT}
                >
                  <Select
                    showSearch
                    placeholder="选择已注册的 Schema"
                    options={schemaOptions}
                    filterOption={(input, option) =>
                      (option?.value ?? '').toLowerCase().includes(input.toLowerCase())
                    }
                    allowClear
                    disabled={readOnly}
                  />
                </Form.Item>
              ) : (
                <Form.Item name="properties" label="嵌套属性 Schema" style={COMPACT}>
                  <JsonEditor height={160} readOnly={readOnly} />
                </Form.Item>
              )}
            </>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default SchemaFormEditor;
