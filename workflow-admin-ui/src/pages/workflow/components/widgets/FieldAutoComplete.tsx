import { useMemo } from 'react';
import { AutoComplete, Tooltip, Tag, Space, Typography } from 'antd';
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import type { WidgetProps } from '@rjsf/utils';
import type { FieldSchemaMeta } from '@/utils/conditionSchema';

const { Text } = Typography;

/**
 * 字段完整 schema 信息的 Tooltip 内容组件（通用，可被 FieldAutoComplete / ConditionConfigEditor 复用）。
 *
 * 展示：中文名称(title)、路径、类型、required、format、description、约束列表。
 * 信息组织为 2-3 个垂直分区，最大宽度 360px，可滚动。
 */
export const FieldMetaTooltipContent: React.FC<{
  meta: FieldSchemaMeta | undefined | null;
  fieldPath: string;
}> = ({ meta, fieldPath }) => {
  if (!meta) {
    return (
      <div style={{ minWidth: 200, fontSize: 12, color: '#bfbfbf' }}>
        <Space size={4}>
          <ExclamationCircleOutlined />
          <span>未加载到字段 schema 信息</span>
        </Space>
        <div style={{ marginTop: 4, wordBreak: 'break-all' }}>路径: {fieldPath}</div>
      </div>
    );
  }

  const displayName = meta.title || meta.name;
  const typeDisplay = meta.format ? `${meta.type}·${meta.format}` : meta.type;

  return (
    <div style={{ minWidth: 260, maxWidth: 360, fontSize: 12, lineHeight: 1.5 }}>
      {/* 分区1：基本标识 */}
      <div style={{ marginBottom: 8, paddingBottom: 6, borderBottom: '1px dashed rgba(255,255,255,0.25)' }}>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>
          {displayName !== meta.name && (
            <span style={{ marginRight: 6 }}>{displayName}</span>
          )}
          <Text code style={{ color: '#bae7ff', fontSize: 11 }}>
            {meta.path}
          </Text>
        </div>
        <Space size={4} wrap style={{ marginTop: 4 }}>
          <Tag color={meta.required ? 'red' : 'default'} style={{ fontSize: 10, margin: 0, padding: '0 4px' }}>
            {meta.required ? '必填' : '非必填'}
          </Tag>
          <Tag color="blue" style={{ fontSize: 10, margin: 0, padding: '0 4px' }}>
            {typeDisplay}
          </Tag>
          {meta.itemType && (
            <Tag color="purple" style={{ fontSize: 10, margin: 0, padding: '0 4px' }}>
              元素: {meta.itemType}
            </Tag>
          )}
        </Space>
      </div>

      {/* 分区2：描述 */}
      {meta.description && (
        <div style={{ marginBottom: 8 }}>
          <div style={{ color: '#91d5ff', fontSize: 11, marginBottom: 2 }}>描述</div>
          <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: 120, overflowY: 'auto' }}>
            {meta.description}
          </div>
        </div>
      )}

      {/* 分区3：约束 */}
      {meta.constraints && meta.constraints.length > 0 && (
        <div>
          <div style={{ color: '#91d5ff', fontSize: 11, marginBottom: 2 }}>约束</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2, maxHeight: 100, overflowY: 'auto' }}>
            {meta.constraints.map((c, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 4 }}>
                <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 10, marginTop: 2 }} />
                <span>{c}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 无描述且无约束时，给个占位 */}
      {!meta.description && (!meta.constraints || meta.constraints.length === 0) && (
        <div style={{ color: '#8c8c8c', fontSize: 11 }}>（该字段无额外描述或约束）</div>
      )}
    </div>
  );
};

export interface FieldMetaOption {
  label: React.ReactNode;
  value: string;
  /** 原始字段路径字符串（用于外部检索等） */
  path: string;
}

/**
 * 基于字段列表 + 元信息映射，构造 AutoComplete options，
 * 每个 option 都被 Tooltip 包裹，hover 时展示完整 schema 信息。
 */
export function buildFieldOptionsWithMeta(
  fields: string[],
  fieldMetaMap: Record<string, FieldSchemaMeta> | undefined | null,
): FieldMetaOption[] {
  const map = fieldMetaMap || {};
  return fields.map((f) => {
    const meta = map[f];
    const optionLabel = (
      <Tooltip
        placement="right"
        mouseEnterDelay={0.15}
        title={<FieldMetaTooltipContent meta={meta} fieldPath={f} />}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 8,
            padding: '2px 0',
          }}
        >
          <Space size={4} style={{ minWidth: 0 }}>
            <CheckCircleOutlined
              style={{
                color: meta?.required ? '#ff4d4f' : '#8c8c8c',
                fontSize: 10,
                flexShrink: 0,
              }}
            />
            {meta?.title && meta.title !== meta.name && (
              <span style={{ fontSize: 12, color: '#262626', flexShrink: 0 }}>{meta.title}</span>
            )}
            <code
              style={{
                fontSize: 12,
                background: 'transparent',
                padding: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                display: 'inline-block',
                maxWidth: 260,
                color: meta?.title && meta.title !== meta.name ? '#595959' : '#262626',
              }}
            >
              {f}
            </code>
          </Space>
          <Space size={4} style={{ flexShrink: 0 }}>
            {meta?.format ? (
              <Tag style={{ fontSize: 10, margin: 0, padding: '0 4px' }}>
                {meta.type}·{meta.format}
              </Tag>
            ) : meta ? (
              <Tag style={{ fontSize: 10, margin: 0, padding: '0 4px' }}>{meta.type}</Tag>
            ) : null}
          </Space>
        </div>
      </Tooltip>
    );
    return { label: optionLabel, value: f, path: f };
  });
}

/**
 * 字段名自动补全 Widget（用于 @rjsf）。
 * 从 formContext 读取：
 *   - upstreamFields：上游可用字段路径列表
 *   - upstreamFieldMeta：字段路径 → FieldSchemaMeta 映射（可选，没有时仍能工作但 Tooltip 只显示基本信息）
 * 支持嵌套路径（如 user.name），用户也可自由输入。
 */
const FieldAutoComplete: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  placeholder,
  formContext,
}) => {
  const fields: string[] = (formContext as any)?.upstreamFields || [];
  const fieldMetaMap: Record<string, FieldSchemaMeta> | undefined =
    (formContext as any)?.upstreamFieldMeta;

  const options = useMemo<FieldMetaOption[]>(
    () => buildFieldOptionsWithMeta(fields, fieldMetaMap),
    [fields, fieldMetaMap],
  );

  return (
    <AutoComplete
      value={value || ''}
      options={options as any}
      onChange={(v) => onChange(v || undefined)}
      disabled={disabled || readonly}
      placeholder={placeholder || '输入字段名，如 status、user.name'}
      style={{ width: '100%' }}
      allowClear
      filterOption={(input, option) => {
        const v = (option as any)?.path ?? (option as any)?.value;
        return (v as string)?.toLowerCase().includes(input.toLowerCase()) ?? false;
      }}
    />
  );
};

export default FieldAutoComplete;
