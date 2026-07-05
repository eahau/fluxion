import {
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  PlusOutlined,
  DeleteOutlined,
  StarFilled,
  EditOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import type { SchemaDefinition } from '@/types/api';
import { getSchemas } from '@/services/schema';
import { useClickDebounce } from '@/utils/useClickDebounce';

export type FieldType = 'string' | 'number' | 'integer' | 'boolean' | 'object' | 'array';

export interface SchemaField {
  id: string;
  name: string;
  type: FieldType;
  required: boolean;
  description?: string;
  defaultValue?: string;
  /** JSON Schema format（如 email、date、date-time、uri 等） */
  format?: string;
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  enum?: any[];
  /** 引用已注册的 Schema 名称（object/array 类型生效） */
  schemaRef?: string;
}

interface SchemaFieldEditorProps {
  value?: Record<string, any>;
  onChange?: (schema: Record<string, any>) => void;
  readOnly?: boolean;
}

const typeColorMap: Record<string, string> = {
  string: 'blue',
  number: 'cyan',
  integer: 'geekblue',
  boolean: 'purple',
  object: 'orange',
  array: 'green',
};

const typeOptions = [
  { value: 'string', label: '字符串' },
  { value: 'number', label: '数字' },
  { value: 'integer', label: '整数' },
  { value: 'boolean', label: '布尔' },
  { value: 'object', label: '对象' },
  { value: 'array', label: '数组' },
];

const stringFormatOptions = [
  { value: '', label: '默认' },
  { value: 'email', label: '邮箱' },
  { value: 'date', label: '日期' },
  { value: 'date-time', label: '日期时间' },
  { value: 'time', label: '时间' },
  { value: 'uri', label: 'URL' },
  { value: 'regex', label: '正则' },
  { value: 'phone', label: '手机号' },
  { value: 'idcard', label: '身份证' },
];

const businessTypePatterns: Record<string, string> = {
  phone: '^1[3-9]\\d{9}$',
  idcard: '^\\d{17}[\\dXx]$',
};

function toJsonFormat(value: string | undefined): string | undefined {
  if (!value || value === 'phone' || value === 'idcard') return undefined;
  return value;
}

function inferStringFormat(prop: any): string {
  if (prop?.format) return prop.format;
  if (prop?.pattern) {
    if (prop.pattern === businessTypePatterns.phone) return 'phone';
    if (prop.pattern === businessTypePatterns.idcard) return 'idcard';
  }
  return '';
}

function generateId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

function parseFields(schema?: Record<string, any>): SchemaField[] {
  if (!schema || schema.type !== 'object' || !schema.properties) return [];
  const required = new Set<string>(Array.isArray(schema.required) ? schema.required : []);
  return Object.entries(schema.properties).map(([name, prop]: [string, any]) => {
    const ref: string | undefined = prop?.$ref;
    const isRef = typeof ref === 'string' && ref.startsWith('schema:');
    return {
      id: generateId(),
      name,
      type: isRef ? (ref.startsWith('schema:') ? 'object' : 'array') : prop?.type || 'string',
      required: required.has(name),
      description: prop?.description || '',
      defaultValue: prop?.default !== undefined ? String(prop.default) : '',
      format: prop?.type === 'string' ? inferStringFormat(prop) : undefined,
      minimum: prop?.minimum,
      maximum: prop?.maximum,
      minLength: prop?.minLength,
      maxLength: prop?.maxLength,
      pattern: prop?.pattern,
      enum: prop?.enum,
      schemaRef: isRef ? ref.slice(7) : undefined,
    };
  });
}

function buildSchema(fields: SchemaField[]): Record<string, any> {
  if (fields.length === 0) return { type: 'object' };
  const properties: Record<string, any> = {};
  const required: string[] = [];
  fields.forEach((field) => {
    // object/array 引用已注册 Schema 时直接输出 $ref
    if (field.schemaRef && (field.type === 'object' || field.type === 'array')) {
      properties[field.name] = {
        $ref: `schema:${field.schemaRef}`,
        description: field.description,
      };
      if (field.required) required.push(field.name);
      return;
    }

    const prop: Record<string, any> = { type: field.type };
    if (field.description) prop.description = field.description;
    if (field.type === 'string' && field.format) {
      const jsonFormat = toJsonFormat(field.format);
      if (jsonFormat) prop.format = jsonFormat;
    }
    if (field.minimum !== undefined) prop.minimum = field.minimum;
    if (field.maximum !== undefined) prop.maximum = field.maximum;
    if (field.minLength !== undefined) prop.minLength = field.minLength;
    if (field.maxLength !== undefined) prop.maxLength = field.maxLength;
    if (field.pattern) prop.pattern = field.pattern;
    if (field.enum?.length) prop.enum = field.enum;
    if (field.defaultValue !== undefined && field.defaultValue !== '') {
      try {
        prop.default = JSON.parse(field.defaultValue);
      } catch {
        prop.default = field.defaultValue;
      }
    }
    properties[field.name] = prop;
    if (field.required) required.push(field.name);
  });
  return { type: 'object', properties, required };
}

const COMPACT = { marginBottom: 12 };

const SchemaFieldEditor: React.FC<SchemaFieldEditorProps> = ({ value, onChange, readOnly }) => {
  const [fields, setFields] = useState<SchemaField[]>(() => parseFields(value));
  const skipNextSyncRef = useRef(false);
  const [form] = Form.useForm();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const type = Form.useWatch('type', form);
  const schemaRefMode = Form.useWatch('schemaRefMode', form);

  // 加载已注册 Schema 列表
  const [schemaOptions, setSchemaOptions] = useState<{ value: string; label: string }[]>([]);
  useEffect(() => {
    getSchemas({ pageSize: 500 }, { silent: true })
      .then((res) => {
        const list = (res?.list ?? []).map((s: SchemaDefinition) => ({
          value: s.schemaName!,
          label: s.schemaName!,
        }));
        setSchemaOptions(list);
      })
      .catch(() => setSchemaOptions([]));
  }, []);

  useEffect(() => {
    if (skipNextSyncRef.current) {
      skipNextSyncRef.current = false;
      return;
    }
    setFields(parseFields(value));
  }, [value]);

  const updateFields = (newFields: SchemaField[]) => {
    setFields(newFields);
    skipNextSyncRef.current = true;
    onChange?.(buildSchema(newFields));
  };

  const openModal = (field: SchemaField) => {
    setEditingId(field.id);
    form.setFieldsValue({
      name: field.name,
      type: field.type,
      required: !!field.required,
      description: field.description,
      defaultValue: field.defaultValue,
      format: field.type === 'string' ? field.format || '' : undefined,
      enum: field.enum,
      minimum: field.minimum,
      maximum: field.maximum,
      minLength: field.minLength,
      maxLength: field.maxLength,
      pattern: field.pattern,
      schemaRefMode: field.schemaRef ? 'ref' : 'inline',
      schemaRef: field.schemaRef,
    });
    setModalVisible(true);
  };

  const handleAdd = useClickDebounce(() => {
    const newField: SchemaField = {
      id: generateId(),
      name: `field${fields.length + 1}`,
      type: 'string',
      required: false,
    };
    // 先插入空占位，保存时再替换
    setFields((prev) => [...prev, newField]);
    setEditingId(newField.id);
    form.resetFields();
    form.setFieldsValue({
      name: newField.name,
      type: 'string',
      required: false,
      schemaRefMode: 'inline',
    });
    setModalVisible(true);
  });

  const handleModalSave = useClickDebounce(async () => {
    const values = await form.validateFields();

    const isDuplicate = fields.some(
      (f) => f.name === values.name && f.id !== editingId,
    );
    if (isDuplicate) {
      return;
    }

    const updated: SchemaField = {
      id: editingId || generateId(),
      name: values.name,
      type: values.type,
      required: !!values.required,
      description: values.description,
      defaultValue: values.defaultValue,
    };

    if (values.type === 'string' && values.format) {
      const jsonFormat = toJsonFormat(values.format);
      if (jsonFormat) updated.format = jsonFormat;
      if (businessTypePatterns[values.format]) {
        updated.pattern = businessTypePatterns[values.format];
      }
    }
    if (values.enum?.length) updated.enum = values.enum;
    if (values.minimum !== undefined) updated.minimum = values.minimum;
    if (values.maximum !== undefined) updated.maximum = values.maximum;
    if (values.minLength !== undefined) updated.minLength = values.minLength;
    if (values.maxLength !== undefined) updated.maxLength = values.maxLength;
    if (values.pattern && !updated.pattern) updated.pattern = values.pattern;

    if ((values.type === 'object' || values.type === 'array') && values.schemaRefMode === 'ref' && values.schemaRef) {
      updated.schemaRef = values.schemaRef;
    }

    const next = fields.map((f) => (f.id === editingId ? updated : f));
    updateFields(next);
    setModalVisible(false);
    setEditingId(null);
  });

  const handleDelete = useClickDebounce((index: number) => {
    const next = fields.filter((_, i) => i !== index);
    updateFields(next);
  });

  const handleInlineChange = (index: number, key: keyof SchemaField, val: any) => {
    const next = fields.map((f, i) => (i === index ? { ...f, [key]: val } : f));
    updateFields(next);
  };

  const columns = [
    {
      title: '字段名',
      dataIndex: 'name',
      render: (_: any, record: SchemaField, index: number) => (
        <Input
          value={record.name}
          onChange={(e) => handleInlineChange(index, 'name', e.target.value)}
          disabled={readOnly}
          placeholder="如 userId"
          size="small"
        />
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 120,
      render: (_: any, record: SchemaField) => {
        const formatLabel = record.format
          ? stringFormatOptions.find((f) => f.value === record.format)?.label
          : undefined;
        return (
          <Space size={4} wrap>
            <Tag color={typeColorMap[record.type] || 'default'} style={{ margin: 0 }}>
              {record.type}
            </Tag>
            {formatLabel && <Tag color="blue">{formatLabel}</Tag>}
            {record.schemaRef && (
              <Tag color="#6366f1" icon={<LinkOutlined />}>
                {record.schemaRef}
              </Tag>
            )}
          </Space>
        );
      },
    },
    {
      title: '必填',
      dataIndex: 'required',
      width: 50,
      align: 'center' as const,
      render: (_: any, record: SchemaField, index: number) =>
        readOnly ? (
          record.required ? (
            <StarFilled style={{ color: '#faad14', fontSize: 14 }} />
          ) : (
            <span style={{ color: '#d9d9d9' }}>-</span>
          )
        ) : (
          <Checkbox
            checked={record.required}
            onChange={(e) => handleInlineChange(index, 'required', e.target.checked)}
            disabled={readOnly}
          />
        ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      render: (_: any, record: SchemaField, index: number) =>
        readOnly ? (
          <Tooltip title={record.description}>
            <div
              style={{
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: 120,
                color: '#595959',
                fontSize: 13,
              }}
            >
              {record.description || <span style={{ color: '#bfbfbf' }}>-</span>}
            </div>
          </Tooltip>
        ) : (
          <Input
            value={record.description}
            onChange={(e) => handleInlineChange(index, 'description', e.target.value)}
            disabled={readOnly}
            placeholder="字段说明"
            size="small"
          />
        ),
    },
    {
      title: '默认值',
      dataIndex: 'defaultValue',
      render: (_: any, record: SchemaField, index: number) => (
        <Input
          value={record.defaultValue}
          onChange={(e) => handleInlineChange(index, 'defaultValue', e.target.value)}
          disabled={readOnly}
          placeholder="字符串直接填，对象/数组用 JSON"
          size="small"
        />
      ),
    },
    {
      title: '操作',
      width: 100,
      render: (_: any, record: SchemaField, index: number) => (
        <Space size={4}>
          {!readOnly && (
            <Button
              icon={<EditOutlined />}
              size="small"
              onClick={() => openModal(record)}
            />
          )}
          <Button
            icon={<DeleteOutlined />}
            size="small"
            danger
            disabled={readOnly}
            onClick={() => handleDelete(index)}
          />
        </Space>
      ),
    },
  ];

  return (
    <div className="fn-schema-field-editor">
      <Table
        rowKey={(record) => record.id}
        size="small"
        columns={columns}
        dataSource={fields}
        pagination={false}
        className="fn-schema-table"
        footer={() =>
          readOnly ? null : (
            <Button
              type="dashed"
              icon={<PlusOutlined />}
              onClick={handleAdd}
              block
              style={{ borderColor: '#6366f1', color: '#6366f1' }}
            >
              添加字段
            </Button>
          )
        }
      />
      <Modal
        title="编辑字段"
        open={modalVisible}
        onOk={handleModalSave}
        onCancel={() => {
          setModalVisible(false);
          // 若新增字段未保存，则移除占位
          if (editingId && !fields.find((f) => f.id === editingId)?.name) {
            updateFields(fields.filter((f) => f.id !== editingId));
          }
          setEditingId(null);
        }}
        width={520}
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
                message: '字段名只能包含字母、数字和下划线',
              },
            ]}
            style={COMPACT}
          >
            <Input placeholder="如 userId" size="small" />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]} style={COMPACT}>
            <Select options={typeOptions} size="small" />
          </Form.Item>
          <Form.Item
            name="required"
            label="必填"
            valuePropName="checked"
            style={COMPACT}
          >
            <Checkbox>必填</Checkbox>
          </Form.Item>
          <Form.Item name="description" label="描述" style={COMPACT}>
            <Input.TextArea
              autoSize={{ minRows: 2, maxRows: 4 }}
              placeholder="字段说明"
              size="small"
            />
          </Form.Item>
          <Form.Item name="defaultValue" label="默认值" style={COMPACT}>
            <Input placeholder="字符串直接填，对象/数组用 JSON" size="small" />
          </Form.Item>

          {type === 'string' && (
            <>
              <Form.Item name="format" label="字符串格式 / 业务类型" style={COMPACT}>
                <Select
                  allowClear
                  placeholder="选择格式"
                  options={stringFormatOptions}
                  size="small"
                />
              </Form.Item>
              <Form.Item name="enum" label="枚举值" style={COMPACT}>
                <Select
                  mode="tags"
                  allowClear
                  placeholder="输入后按回车"
                  tokenSeparators={[',']}
                  size="small"
                />
              </Form.Item>
              <Form.Item name="minLength" label="最小长度" style={COMPACT}>
                <InputNumber style={{ width: '100%' }} placeholder="minLength" size="small" />
              </Form.Item>
              <Form.Item name="maxLength" label="最大长度" style={COMPACT}>
                <InputNumber style={{ width: '100%' }} placeholder="maxLength" size="small" />
              </Form.Item>
              <Form.Item name="pattern" label="正则表达式" style={COMPACT}>
                <Input placeholder="pattern" size="small" />
              </Form.Item>
            </>
          )}

          {(type === 'integer' || type === 'number') && (
            <>
              <Form.Item name="minimum" label="最小值" style={COMPACT}>
                <InputNumber style={{ width: '100%' }} placeholder="minimum" size="small" />
              </Form.Item>
              <Form.Item name="maximum" label="最大值" style={COMPACT}>
                <InputNumber style={{ width: '100%' }} placeholder="maximum" size="small" />
              </Form.Item>
            </>
          )}

          {(type === 'object' || type === 'array') && (
            <>
              <Form.Item
                name="schemaRefMode"
                label="嵌套对象定义方式"
                style={COMPACT}
                initialValue="inline"
              >
                <Radio.Group>
                  <Radio.Button value="inline">内联定义</Radio.Button>
                  <Radio.Button value="ref">
                    <LinkOutlined style={{ marginRight: 4 }} />
                    引用已有 Schema
                  </Radio.Button>
                </Radio.Group>
              </Form.Item>
              {schemaRefMode === 'ref' && (
                <Form.Item
                  name="schemaRef"
                  label="引用 Schema"
                  rules={[{ required: true, message: '请选择 Schema' }]}
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
                    size="small"
                  />
                </Form.Item>
              )}
            </>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default SchemaFieldEditor;
