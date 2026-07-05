import React from 'react';
import { Popover, Space, Tag, Tooltip } from 'antd';
import {
  CheckCircleOutlined,
  LinkOutlined,
  UnorderedListOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import type { TopLevelField } from '@/utils/jsonSchema';
import { collectTopLevelFields } from '@/utils/jsonSchema';

export interface SchemaFieldPreviewProps {
  fields: TopLevelField[];
  refSchemaMap: Map<string, { schemaJson?: string | null }>;
  /** 显示"字段预览"标题和条数，默认 true（Popover 递归嵌套时可以关掉，避免重复标题） */
  showHeader?: boolean;
  /** Popover 展开层级限制，默认 0 层（复杂字段 Tag 本身可 hover 看类型/描述摘要，不再递归浮层展开，走父级右侧完整预览面板查看） */
  maxPopoverDepth?: number;
}

const FIELD_STYLE: React.CSSProperties = {
  fontSize: 12,
};

const COMPLEX_TYPE_SET = new Set(['object', 'ref', 'array']);
const isComplexType = (f: TopLevelField) => {
  if (f.type.startsWith('ref:')) return true;
  if (f.type === 'object') return true;
  if (f.type === 'array') {
    // 数组元素是 object/ref 的才算复杂，简单 string[]/number[] 不用 hover 展开
    if (f.itemType && (f.itemType.startsWith('ref:') || f.itemType === 'object')) return true;
    if (f.innerSchemaForPreview) return true;
  }
  return f.innerSchemaForPreview !== undefined;
};

const renderTypeTag = (f: TopLevelField) => {
  if (f.type.startsWith('ref:')) {
    return (
      <Tag color="#6366f1" icon={<LinkOutlined />} style={{ fontSize: 11, margin: 0, flexShrink: 0 }}>
        {f.type.slice(4)}
      </Tag>
    );
  }
  if (f.type === 'array') {
    return (
      <Space size={4} style={{ flexShrink: 0 }}>
        <Tag style={{ fontSize: 11, padding: '0 4px', margin: 0 }}>array</Tag>
        {f.itemType && (
          f.itemType.startsWith('ref:') ? (
            <Tag color="#6366f1" icon={<LinkOutlined />} style={{ fontSize: 10, margin: 0, padding: '0 4px' }}>
              {f.itemType.slice(4)}
            </Tag>
          ) : (
            <Tag color="default" style={{ fontSize: 10, margin: 0, padding: '0 4px' }}>
              {f.itemType}
            </Tag>
          )
        )}
      </Space>
    );
  }
  const displayType = f.format ? `${f.type}·${f.format}` : f.type;
  return <Tag style={{ fontSize: 11, padding: '0 4px', margin: 0, flexShrink: 0 }}>{displayType}</Tag>;
};

/**
 * 渲染字段列表：每行 = required 标记 + 名称/描述 + 类型 + 约束摘要
 * 复杂字段行本身包 Popover，hover 后递归显示内部字段。
 */
const Inner: React.FC<SchemaFieldPreviewProps & { depth: number }> = (props) => {
  const { fields, refSchemaMap, showHeader = true, maxPopoverDepth = 0, depth } = props;
  if (!fields || fields.length === 0) {
    return (
      <div
        style={{
          padding: '8px 10px',
          border: '1px dashed #e5e7eb',
          borderRadius: 6,
          color: '#8c8c8c',
          fontSize: 12,
          display: 'flex',
          alignItems: 'center',
          gap: 6,
        }}
      >
        <InfoCircleOutlined style={{ color: '#1677ff' }} />
        <span>该层无可展开字段（纯组合 / 循环引用保护 / 引用未加载）</span>
      </div>
    );
  }

  return (
    <div>
      {showHeader && (
        <div
          style={{
            fontSize: 12,
            color: '#595959',
            marginBottom: 6,
            fontWeight: 500,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Space size={4}>
            <UnorderedListOutlined />
            <span>字段预览</span>
          </Space>
          <Tag color="blue" style={{ fontSize: 11, margin: 0 }}>
            共 {fields.length} 个
          </Tag>
        </div>
      )}
      <div
        style={{
          maxHeight: 280,
          overflow: 'auto',
          border: '1px solid #f0f0f0',
          borderRadius: 6,
          padding: 4,
          ...FIELD_STYLE,
        }}
      >
        {fields.map((f, idx) => {
          const complex = isComplexType(f);
          const canPop = complex && depth <= maxPopoverDepth && f.innerSchemaForPreview;
          const children = (
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                gap: 8,
                padding: '4px 8px',
                borderBottom:
                  idx < fields.length - 1 ? '1px dashed #f5f5f5' : 'none',
                borderRadius: 4,
                cursor: canPop ? 'zoom-in' : 'default',
                background: canPop ? 'transparent' : 'transparent',
                transition: 'background .15s',
              }}
              className="schema-field-preview-row"
              onMouseEnter={(e) => {
                if (canPop) {
                  (e.currentTarget as HTMLElement).style.background = '#f9fafb';
                }
              }}
              onMouseLeave={(e) => {
                if (canPop) {
                  (e.currentTarget as HTMLElement).style.background = 'transparent';
                }
              }}
            >
              <Space size={6} style={{ flex: 1, minWidth: 0 }} align="start">
                <CheckCircleOutlined
                  style={{
                    color: f.required ? '#ff4d4f' : '#8c8c8c',
                    fontSize: 10,
                    marginTop: 4,
                    flexShrink: 0,
                  }}
                />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <Tooltip
                    title={
                      <div style={{ maxWidth: 280 }}>
                        <div style={{ fontWeight: 600, marginBottom: 4 }}>{f.path || f.name}</div>
                        {f.description ? (
                          <div style={{ whiteSpace: 'pre-wrap' }}>{f.description}</div>
                        ) : (
                          <div style={{ color: '#bfbfbf' }}>（该字段未写描述）</div>
                        )}
                        <div
                          style={{
                            marginTop: 6,
                            paddingTop: 4,
                            borderTop: '1px dashed rgba(255,255,255,0.3)',
                            fontSize: 11,
                            color: '#e6f0ff',
                          }}
                        >
                          {f.required ? <span style={{ color: '#ffccc7' }}>必填字段 · </span> : '非必填 · '}
                          类型 {f.type}
                          {f.format ? ` · 格式 ${f.format}` : ''}
                        </div>
                      </div>
                    }
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                        flexWrap: 'wrap',
                        minWidth: 0,
                      }}
                    >
                      {f.required && (
                        <Tag color="red" style={{ fontSize: 10, padding: '0 4px', margin: 0, flexShrink: 0 }}>
                          必填
                        </Tag>
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
                          maxWidth: 180,
                          verticalAlign: 'text-bottom',
                          fontWeight: 500,
                        }}
                      >
                        {f.path || f.name}
                      </code>
                    </div>
                  </Tooltip>
                  {f.description && (
                    <div
                      style={{
                        marginTop: 2,
                        color: '#8c8c8c',
                        fontSize: 11,
                        lineHeight: 1.4,
                        whiteSpace: 'normal',
                        wordBreak: 'break-word',
                        paddingLeft: 2,
                      }}
                    >
                      {f.description.length > 80 ? `${f.description.slice(0, 80)}…` : f.description}
                    </div>
                  )}
                </div>
              </Space>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
                {renderTypeTag(f)}
                {f.constraints && f.constraints.length > 0 && (
                  <Space size={4} wrap style={{ justifyContent: 'flex-end' }}>
                    {f.constraints.map((c) => (
                      <Tag color="default" key={c} style={{ fontSize: 10, margin: 0, padding: '0 4px' }}>
                        {c}
                      </Tag>
                    ))}
                  </Space>
                )}
              </div>
            </div>
          );

          if (!canPop) return <React.Fragment key={`${f.path}-${idx}`}>{children}</React.Fragment>;

          const popContent = (
            <div style={{ minWidth: 320 }}>
              <Inner
                fields={collectTopLevelFields(
                  f.innerSchemaForPreview!,
                  refSchemaMap,
                  '',
                  new Set(f.schemaRef ? [f.schemaRef] : []),
                )}
                refSchemaMap={refSchemaMap}
                showHeader
                maxPopoverDepth={maxPopoverDepth}
                depth={depth + 1}
              />
            </div>
          );

          return (
            <Popover
              key={`${f.path}-${idx}`}
              content={popContent}
              trigger="hover"
              placement="rightTop"
              arrow
              overlayStyle={{ maxWidth: 420 }}
              title={
                <Space size={4}>
                  <LinkOutlined style={{ color: '#6366f1' }} />
                  <strong>{f.path || f.name}</strong>
                  <span style={{ color: '#8c8c8c', fontWeight: 'normal', fontSize: 12 }}>
                    · 预览内部字段
                  </span>
                </Space>
              }
            >
              {children}
            </Popover>
          );
        })}
      </div>
    </div>
  );
};

export const SchemaFieldPreview: React.FC<SchemaFieldPreviewProps> = (p) => (
  <Inner {...p} depth={1} />
);

export default SchemaFieldPreview;
