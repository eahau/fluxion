import { useState, useMemo, useEffect } from 'react';
import {
  Button,
  Input,
  Alert,
  List,
  Tag,
  Space,
  Tooltip,
  Popover,
  Descriptions,
  Empty,
} from 'antd';
import type { ErrorObject } from 'ajv';
import {
  SCHEMA_REF_PREFIX,
  createAjvWithSchemaRefPlaceholders,
  collectTopLevelFields,
  type TopLevelField,
} from '@/utils/jsonSchema';
import { SchemaFieldPreview } from './SchemaFieldPreview';
import { getSchemas, getSchema } from '@/services/schema';
import type { SchemaDefinition } from '@/types/schema';
import {
  FileTextOutlined,
  LinkOutlined,
  CheckCircleOutlined,
  InfoCircleOutlined,
  BranchesOutlined,
  UnorderedListOutlined,
  EyeOutlined,
  ExportOutlined,
} from '@ant-design/icons';

interface SchemaValidatorProps {
  schema: any;
  /** 从父级（editor.tsx）统一预加载的所有引用详情，优先复用，避免重复请求 */
  refSchemaMap?: Map<string, SchemaDefinition>;
  /** 当前正在编辑的 schema 名（用于引用关系展示） */
  selfSchemaName?: string;
  /** 引用 Tag/按钮被点击时触发：父级在辅助面板中打开该引用的 SchemaPreview */
  onPreviewRef?: (refName: string) => void;
}

export interface ValidationError {
  path: string;
  message: string;
}

interface SchemaCompileResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationError[];
  info: ValidationError[];
  /** 收集到的 schema:xxx 引用，用于 UI 展示 */
  schemaRefs: string[];
  /** 用于样例数据校验的「宽容编译版」AJV 验证函数（已注入空 schema 占位） */
  tolerantValidateFn?: ((data: any) => boolean) | null;
}

/**
 * 编译 schema。
 * 将「无法解析自定义 schema 引用」从语法错误降级为 info 级别提示，
 * 并返回一个「宽容编译版」验证函数供样例数据校验使用。
 */
function compileSchema(schema: any): SchemaCompileResult {
  const { ajv, refs } = createAjvWithSchemaRefPlaceholders(schema);

  try {
    const validateFn = ajv.compile(schema);
    return {
      valid: true,
      errors: [],
      warnings: [],
      info: refs.map((ref) => ({
        path: '/',
        message: `检测到引用 ${ref}（引用有效性将在保存时由后端校验）`,
      })),
      schemaRefs: refs,
      tolerantValidateFn: validateFn,
    };
  } catch (rawErr) {
    const errMsg = rawErr instanceof Error ? rawErr.message : String(rawErr);
    // can't resolve reference 错误理论上在占位注入后不会再出现，
    // 但为了稳健性仍然做一个过滤和区分
    const isResolvedExternalRefError =
      errMsg.includes("can't resolve reference") &&
      errMsg.includes(`${SCHEMA_REF_PREFIX}`);

    if (isResolvedExternalRefError) {
      return {
        valid: true,
        errors: [],
        warnings: [
          {
            path: '/',
            message: errMsg,
          },
        ],
        info: [],
        schemaRefs: refs,
        tolerantValidateFn: null,
      };
    }

    return {
      valid: false,
      errors: [{ path: '/', message: errMsg }],
      warnings: [],
      info: [],
      schemaRefs: refs,
      tolerantValidateFn: null,
    };
  }
}

/**
 * 宽容校验样例数据：使用占位 schema 编译后的验证函数，
 * 对于 schema: 引用的字段不做强类型校验，只校验其它部分。
 */
function validateDataTolerant(
  schema: any,
  data: any,
  precompiled: ((data: any) => boolean) | null | undefined,
): { valid: boolean; errors: ValidationError[] } {
  // 优先用预编译好的宽容版（schema 引用已被占位）
  if (typeof precompiled === 'function') {
    try {
      const valid = precompiled(data) as boolean;
      const errors =
        // @ts-expect-error - ajv validate fn attaches .errors
        (precompiled.errors as ErrorObject[] | null | undefined)?.map((err) => ({
          path: err.instancePath || '/',
          message: err.message || '校验失败',
        })) || [];
      return { valid, errors };
    } catch (e) {
      return {
        valid: false,
        errors: [{ path: '/', message: e instanceof Error ? e.message : '校验异常' }],
      };
    }
  }

  // 回退：如果没有宽容版，就再创建一次
  const { ajv } = createAjvWithSchemaRefPlaceholders(schema);
  try {
    const validate = ajv.compile(schema);
    const valid = validate(data) as boolean;
    const errors =
      (validate.errors as ErrorObject[] | null | undefined)?.map((err) => ({
        path: err.instancePath || '/',
        message: err.message || '校验失败',
      })) || [];
    return { valid, errors };
  } catch (e) {
    return {
      valid: false,
      errors: [{ path: '/', message: e instanceof Error ? e.message : '校验异常' }],
    };
  }
}

const SchemaValidator: React.FC<SchemaValidatorProps> = ({ schema, refSchemaMap, selfSchemaName, onPreviewRef }) => {
  const [sampleData, setSampleData] = useState<string>('{}');
  const [result, setResult] = useState<{ valid: boolean; errors: ValidationError[] } | null>(null);

  const schemaStatus = useMemo(() => compileSchema(schema), [schema]);

  /**
   * 从任意结构的 schema 定义里提取「直接顶层字段」列表，用于 hover 预览小卡片。
   * - 用 collectTopLevelFields：只拿 1 层，不把嵌套铺成一长串路径
   * - object / ref / 复杂数组字段 会带 innerSchemaForPreview，交给 UI 包 Popover 递归预览
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

  // 引用名称 -> Schema 定义 + 字段预览（懒加载：hover 时触发）
  const [refDetailMap, setRefDetailMap] = useState<
    Record<
      string,
      {
        loading: boolean;
        schema?: SchemaDefinition;
        fields?: TopLevelField[];
        totalFieldCount?: number;
        structuralNote?: string;
        error?: string;
      }
    >
  >({});

  /**
   * 1) 父级传了 refSchemaMap 过来：直接同步填充本地 state（不需要再发请求）
   *    保证 refDetailMap 一上来就有内容，hover 无需等待
   * 2) refSchemaMap 没有的，后面 loadRefDetail 会兜底调接口
   */
  useEffect(() => {
    if (!refSchemaMap || refSchemaMap.size === 0) return;
    setRefDetailMap((prev) => {
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

  const loadRefDetail = (refName: string) => {
    if (refDetailMap[refName]) return;
    // 父级已经传了 refSchemaMap 的话，用它直接填（理论上 useEffect 已经填了，这里兜底）
    const fromProp = refSchemaMap?.get(refName);
    if (fromProp) {
      setRefDetailMap((prev) => {
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
    setRefDetailMap((prev) => ({ ...prev, [refName]: { loading: true } }));
    getSchema(refName, { silent: true })
      .then((schemaDef) => {
        const parsed = extractFieldsFromDef(schemaDef);
        setRefDetailMap((prev) => ({
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
        setRefDetailMap((prev) => ({
          ...prev,
          [refName]: { loading: false, error: e?.message || '加载失败' },
        }));
      });
  };

  const handleValidate = () => {
    let data: any;
    try {
      data = JSON.parse(sampleData);
    } catch (e) {
      setResult({ valid: false, errors: [{ path: '/', message: '样例数据不是合法的 JSON' }] });
      return;
    }
    setResult(validateDataTolerant(schema, data, schemaStatus.tolerantValidateFn));
  };

  const overallStatus = schemaStatus.valid
    ? schemaStatus.errors.length === 0
      ? schemaStatus.warnings.length > 0
        ? 'warning'
        : 'success'
      : 'error'
    : 'error';

  const statusTitle = overallStatus === 'success'
    ? '当前 Schema 语法合法'
    : overallStatus === 'warning'
      ? '当前 Schema 语法合法（含提示信息）'
      : '当前 Schema 语法有误';

  const renderRefPreviewTag = (refUri: string) => {
    const refName = refUri.slice(SCHEMA_REF_PREFIX.length);
    const found = refDetailMap[refName]?.schema;
    const color = found ? '#52c41a' : '#faad14';

    const detail = refDetailMap[refName];

    const handleClick = (e?: React.MouseEvent) => {
      e?.stopPropagation?.();
      if (!found) return;
      if (typeof onPreviewRef === 'function') onPreviewRef(refName);
    };

    const tag = (
      <Tag
        color={color}
        icon={<LinkOutlined />}
        style={{ cursor: found ? 'pointer' : 'help', margin: 0, userSelect: 'none' }}
        onClick={handleClick}
      >
        {SCHEMA_REF_PREFIX}{refName} · {found ? '已注册' : '未注册'}
      </Tag>
    );

    const content =
      detail?.loading ? (
        <div style={{ padding: '12px 16px', color: '#8c8c8c' }}>正在加载预览...</div>
      ) : detail?.error ? (
        <Alert
          type="warning"
          showIcon
          message="预览加载失败"
          description={`Schema「${refName}」详情加载失败：${detail.error}。`}
        />
      ) : detail?.schema ? (
        <div style={{ minWidth: 340 }}>
          <Descriptions column={1} size="small" bordered style={{ marginBottom: 10 }}>
            <Descriptions.Item label="名称">
              <Space>
                <FileTextOutlined style={{ color: '#6366f1' }} />
                {detail.schema.schemaName}
                {detail.schema.frozen && <Tag color="orange">已冻结</Tag>}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="类型">
              <Space size={4}>
                {(detail.schema.schemaType || 'INPUT')
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
                <span>{detail.schema.schemaFormat || '-'}</span>
                {detail.schema.scope === 'PRIVATE' ? (
                  <Tag color="purple" style={{ margin: 0 }}>
                    私有{detail.schema.appGroup ? `· ${detail.schema.appGroup}` : ''}
                  </Tag>
                ) : (
                  <Tag color="default" style={{ margin: 0 }}>平台</Tag>
                )}
              </Space>
            </Descriptions.Item>
            {detail.structuralNote && (
              <Descriptions.Item label="结构">
                <Tag color="#6366f1" icon={<BranchesOutlined />} style={{ margin: 0 }}>
                  {detail.structuralNote}
                </Tag>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="字段摘要">
              <Space size={6} wrap>
                <Tag color="blue" icon={<UnorderedListOutlined />} style={{ margin: 0 }}>
                  共 {detail.totalFieldCount ?? 0} 个顶层字段
                </Tag>
                {detail.fields && detail.fields.length > (detail.totalFieldCount ?? 0) && (
                  <Tag color="default" style={{ margin: 0 }}>
                    仅展示前 {detail.fields.length} 项
                  </Tag>
                )}
              </Space>
            </Descriptions.Item>
            {detail.schema.description && (
              <Descriptions.Item label="描述">
                <div style={{ whiteSpace: 'pre-wrap', maxHeight: 120, overflow: 'auto' }}>
                  {detail.schema.description}
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
              {detail.fields && detail.fields.length > 0 ? (
                <SchemaFieldPreview
                  fields={detail.fields}
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
        <div style={{ padding: '12px 16px', color: '#8c8c8c' }}>
          {found ? '正在加载预览...' : `${refName} 未在当前已注册列表中找到`}
        </div>
      );

    return (
      <Popover
        key={refUri}
        title={
          <Space>
            <LinkOutlined style={{ color: '#6366f1' }} />
            <span style={{ fontWeight: 500 }}>引用 Schema：{refName}</span>
            {!found && <Tag color="red">⚠ 未注册</Tag>}
          </Space>
        }
        content={content}
        trigger="hover"
        mouseEnterDelay={0.15}
        mouseLeaveDelay={0.6}
        onOpenChange={(open) => {
          if (open && !refDetailMap[refName] && found) {
            loadRefDetail(refName);
          } else if (open && !found && !refDetailMap[refName]) {
            loadRefDetail(refName);
          }
        }}
      >
        {tag}
      </Popover>
    );
  };

  return (
    <div>
      <Alert
        type={overallStatus as any}
        message={statusTitle}
        description={
          <Space direction="vertical" size={6} style={{ width: '100%' }}>
            {/* 语法错误列表 */}
            {schemaStatus.errors.length > 0 && (
              <List
                size="small"
                header={<Tag color="red">语法错误 {schemaStatus.errors.length} 项</Tag>}
                dataSource={schemaStatus.errors}
                renderItem={(err) => (
                  <List.Item>
                    <Tag color="red">{err.path}</Tag>
                    <span>{err.message}</span>
                  </List.Item>
                )}
                style={{ background: '#fff2f0', borderRadius: 8 }}
              />
            )}

            {/* 引用信息展示（带 hover 预览） */}
            {schemaStatus.schemaRefs.length > 0 && (
              <div
                style={{
                  padding: '10px 12px',
                  background: '#f5f3ff',
                  border: '1px solid #e9e7ff',
                  borderRadius: 8,
                }}
              >
                <div style={{ marginBottom: 6, fontWeight: 500, color: '#6366f1' }}>
                  检测到 {schemaStatus.schemaRefs.length} 个 Schema 引用（鼠标悬浮查看预览）：
                </div>
                <Space size={6} wrap>
                  {schemaStatus.schemaRefs.map((refUri) => renderRefPreviewTag(refUri))}
                </Space>
              </div>
            )}

            {/* Info 级别提示 */}
            {schemaStatus.info.length > 0 && (
              <List
                size="small"
                header={<Tag color="blue">提示信息 {schemaStatus.info.length} 项</Tag>}
                dataSource={schemaStatus.info}
                renderItem={(info) => (
                  <List.Item>
                    <Tag color="blue">{info.path}</Tag>
                    <span style={{ color: '#595959' }}>{info.message}</span>
                  </List.Item>
                )}
                style={{ background: '#e6f7ff', borderRadius: 8 }}
              />
            )}

            {/* Warning 级别提示 */}
            {schemaStatus.warnings.length > 0 && (
              <List
                size="small"
                header={<Tag color="orange">警告 {schemaStatus.warnings.length} 项</Tag>}
                dataSource={schemaStatus.warnings}
                renderItem={(wrn) => (
                  <List.Item>
                    <Tag color="orange">{wrn.path}</Tag>
                    <span>{wrn.message}</span>
                  </List.Item>
                )}
                style={{ background: '#fff7e6', borderRadius: 8 }}
              />
            )}
          </Space>
        }
        style={{ marginBottom: 16 }}
        showIcon
      />

      <div style={{ marginBottom: 8, fontWeight: 500 }}>输入样例数据（JSON）：</div>
      <Input.TextArea
        rows={8}
        value={sampleData}
        onChange={(e) => setSampleData(e.target.value)}
        placeholder='{"field": "value"}'
      />
      <Space style={{ marginTop: 12 }}>
        <Button type="primary" onClick={handleValidate}>
          校验样例数据
        </Button>
        <Button onClick={() => setSampleData('{}')}>重置</Button>
      </Space>

      {result && (
        <div style={{ marginTop: 16 }}>
          {result.valid ? (
            <Alert type="success" message="样例数据校验通过" showIcon />
          ) : (
            <Alert
              type="error"
              message="样例数据校验失败"
              description={
                <List
                  size="small"
                  dataSource={result.errors}
                  renderItem={(err) => (
                    <List.Item>
                      <Tag color="red">{err.path || '/'}</Tag>
                      {err.message}
                    </List.Item>
                  )}
                />
              }
              showIcon
            />
          )}
        </div>
      )}
    </div>
  );
};

export default SchemaValidator;
