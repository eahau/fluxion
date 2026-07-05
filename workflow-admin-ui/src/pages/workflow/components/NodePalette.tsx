import { useEffect, useMemo, useState } from 'react';
import { Input, Spin, Tag, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { getFunctions } from '@/services/function';
import { NODE_TYPE_MAP, NODE_ROLE_GROUPS, NODE_TYPE_TO_ROLE } from '@/constants/nodeTypes';
import { getDomainMeta, sortDomains } from '@/constants/domain';
import type { FunctionDefinition } from '@/types/function';
import type { NodeType } from '@/types/workflow';

const { Text } = Typography;

const CATEGORY_META: Record<string, { label: string; color: string; icon: string }> = {
  BUILTIN: { label: '内置函数', color: '#3b82f6', icon: '⚡' },
  CUSTOM: { label: '自定义函数', color: '#10b981', icon: '🧩' },
  SCRIPT: { label: '脚本函数', color: '#8b5cf6', icon: '📝' },
  EXTERNAL: { label: '外部服务', color: '#f59e0b', icon: '🌐' },
};

interface DragPayload {
  nodeType: NodeType;
  functionRef: string;
  label: string;
}

const NodePalette: React.FC = () => {
  const [keyword, setKeyword] = useState('');
  const [functions, setFunctions] = useState<FunctionDefinition[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getFunctions({ page: 0, pageSize: 1000 }, { silent: true })
      .then((res) => setFunctions(res.list || []))
      .catch(() => setFunctions([]))
      .finally(() => setLoading(false));
  }, []);

  // 工具节点（非函数来源）
  const toolNodes = [
    { nodeType: 'STICKY_NOTE', label: '备注', icon: '📝', color: '#fbbf24', desc: '画布注释' },
  ];

  const roleGrouped = useMemo(() => {
    const filtered = functions.filter(
      (fn) => {
        const kw = keyword.toLowerCase();
        const domain = (fn.config?.domain as string) || 'other';
        const domainLabel = getDomainMeta(domain).label.toLowerCase();
        return (
          fn.name.toLowerCase().includes(kw) ||
          fn.category.toLowerCase().includes(kw) ||
          fn.nodeType.toLowerCase().includes(kw) ||
          domain.includes(kw) ||
          domainLabel.includes(kw)
        );
      },
    );
    // role -> category -> domain -> functions[]
    const roleMap = new Map<string, Map<string, Map<string, FunctionDefinition[]>>>();
    filtered.forEach((fn) => {
      const role = NODE_TYPE_TO_ROLE[fn.nodeType] || 'EXECUTOR';
      const cat = fn.category || 'OTHER';
      const domain = (fn.config?.domain as string) || 'other';
      if (!roleMap.has(role)) roleMap.set(role, new Map());
      const catMap = roleMap.get(role)!;
      if (!catMap.has(cat)) catMap.set(cat, new Map());
      const domainMap = catMap.get(cat)!;
      if (!domainMap.has(domain)) domainMap.set(domain, []);
      domainMap.get(domain)!.push(fn);
    });
    // 按 NODE_ROLE_GROUPS 的顺序转为排序后的数组
    const roleOrder = NODE_ROLE_GROUPS.map((r) => r.role);
    const sortedRoles = Array.from(roleMap.keys()).sort(
      (a, b) => (roleOrder.indexOf(a) === -1 ? 99 : roleOrder.indexOf(a)) - (roleOrder.indexOf(b) === -1 ? 99 : roleOrder.indexOf(b)),
    );
    return sortedRoles.map((role) => {
      const catMap = roleMap.get(role)!;
      const roleTotal = Array.from(catMap.values()).reduce(
        (sum, dm) => sum + Array.from(dm.values()).reduce((s, arr) => s + arr.length, 0), 0,
      );
      const categories = Array.from(catMap.entries()).map(([category, domainMap]) => ({
        category,
        total: Array.from(domainMap.values()).reduce((sum, arr) => sum + arr.length, 0),
        domains: sortDomains(Array.from(domainMap.keys())).map((domain) => ({
          domain,
          items: domainMap.get(domain)!,
        })),
      }));
      return { role, roleTotal, categories };
    });
  }, [functions, keyword]);

  const onDragStart = (event: React.DragEvent, fn: FunctionDefinition) => {
    const payload: DragPayload = {
      nodeType: fn.nodeType as NodeType,
      functionRef: fn.name,
      label: fn.name,
    };
    event.dataTransfer.setData('application/reactflow', JSON.stringify(payload));
    event.dataTransfer.effectAllowed = 'move';
  };

  const onToolDragStart = (event: React.DragEvent, nodeType: string) => {
    event.dataTransfer.setData('application/reactflow', JSON.stringify({ nodeType }));
    event.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: 'var(--fluxion-palette-bg)' }}>
      {/* 搜索框 */}
      <div style={{ padding: 16, borderBottom: '1px solid var(--fluxion-palette-border)', background: 'var(--fluxion-palette-surface)' }}>
        <Input
          size="middle"
          placeholder="搜索函数..."
          prefix={<SearchOutlined style={{ color: '#94a3b8' }} />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          allowClear
          style={{
            borderRadius: 10,
            border: '1px solid var(--fluxion-palette-border)',
          }}
        />
      </div>

      <div style={{ position: 'relative', flex: 1, overflow: 'hidden' }}>
        {loading && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1, background: 'var(--fluxion-palette-loading-bg)' }}>
            <Spin size="small" />
          </div>
        )}
        <div style={{ 
          width: '100%', 
          padding: '12px 16px', 
          overflow: 'auto', 
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}>
          {/* 工具节点 */}
          {!keyword && (
            <div>
              <div style={{ 
                display: 'flex', 
                alignItems: 'center', 
                gap: 6, 
                marginBottom: 10 
              }}>
                <span style={{ fontSize: 12 }}>🛠️</span>
                <Text style={{ 
                  fontSize: 11, 
                  fontWeight: 600, 
                  color: 'var(--fluxion-palette-text)',
                  textTransform: 'uppercase',
                  letterSpacing: '0.5px',
                }}>
                  工具
                </Text>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {toolNodes.map((tool) => (
                  <div
                    key={tool.nodeType}
                    draggable
                    onDragStart={(e) => onToolDragStart(e, tool.nodeType)}
                    style={{
                      padding: '10px 12px',
                      borderRadius: 10,
                      cursor: 'grab',
                      background: 'var(--fluxion-palette-surface)',
                      border: '1px solid var(--fluxion-palette-border)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.borderColor = tool.color;
                      e.currentTarget.style.boxShadow = `0 4px 12px ${tool.color}20`;
                      e.currentTarget.style.transform = 'translateY(-1px)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.borderColor = 'var(--fluxion-palette-border)';
                      e.currentTarget.style.boxShadow = 'none';
                      e.currentTarget.style.transform = 'translateY(0)';
                    }}
                  >
                    <div
                      style={{
                        width: 36,
                        height: 36,
                        borderRadius: 10,
                        background: `${tool.color}15`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 18,
                        flexShrink: 0,
                      }}
                    >
                      {tool.icon}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ 
                        fontWeight: 500, 
                        fontSize: 13, 
                        color: 'var(--fluxion-palette-title)',
                        lineHeight: 1.3,
                      }}>
                        {tool.label}
                      </div>
                      <div style={{ 
                        fontSize: 11, 
                        color: 'var(--fluxion-palette-muted)',
                        lineHeight: 1.3,
                      }}>
                        {tool.desc}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 函数节点（按角色分组） */}
          {roleGrouped.length === 0 ? (
            <div style={{ 
              textAlign: 'center', 
              padding: '40px 20px',
              color: 'var(--fluxion-palette-muted)',
            }}>
              <div style={{ fontSize: 32, marginBottom: 12 }}>🔍</div>
              <Text type="secondary" style={{ fontSize: 13 }}>
                {keyword ? '未找到匹配的函数' : '暂无函数，请先在函数管理中创建'}
              </Text>
            </div>
          ) : (
            roleGrouped.map(({ role, roleTotal, categories }) => {
              const roleMeta = NODE_ROLE_GROUPS.find((r) => r.role === role);
              return (
                <div key={role}>
                  {/* 角色标题 */}
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    marginBottom: 12,
                    paddingBottom: 6,
                    borderBottom: '1px solid var(--fluxion-palette-border)',
                  }}>
                    <span style={{ fontSize: 14 }}>{roleMeta?.icon || '📦'}</span>
                    <Text style={{
                      fontSize: 12,
                      fontWeight: 700,
                      color: 'var(--fluxion-palette-title)',
                    }}>
                      {roleMeta?.label || role}
                    </Text>
                    <Text style={{ fontSize: 10, color: 'var(--fluxion-palette-muted)' }}>
                      {roleMeta?.desc}
                    </Text>
                    <Tag
                      style={{
                        marginLeft: 'auto',
                        borderRadius: 10,
                        fontSize: 10,
                        lineHeight: '16px',
                        padding: '0 6px',
                        background: 'var(--fluxion-palette-tag-bg)',
                        border: 'none',
                        color: 'var(--fluxion-palette-text)',
                      }}
                    >
                      {roleTotal}
                    </Tag>
                  </div>

                  {/* 角色下的分类 */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 14, paddingLeft: 4 }}>
                    {categories.map(({ category, total, domains }) => {
                      const meta = CATEGORY_META[category] || { label: category, color: '#64748b', icon: '📦' };
                      const showDomainHeader = domains.length > 1;
                      return (
                        <div key={category}>
                          {/* 分类标题 */}
                          <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 6,
                            marginBottom: 8,
                          }}>
                            <span style={{ fontSize: 11 }}>{meta.icon}</span>
                            <Text style={{
                              fontSize: 11,
                              fontWeight: 600,
                              color: 'var(--fluxion-palette-text)',
                              textTransform: 'uppercase',
                              letterSpacing: '0.5px',
                            }}>
                              {meta.label}
                            </Text>
                            <Tag
                              style={{
                                marginLeft: 'auto',
                                borderRadius: 10,
                                fontSize: 10,
                                lineHeight: '16px',
                                padding: '0 6px',
                                background: 'var(--fluxion-palette-tag-bg)',
                                border: 'none',
                                color: 'var(--fluxion-palette-text)',
                              }}
                            >
                              {total}
                            </Tag>
                          </div>

                          {/* 各领域的函数 */}
                          <div style={{ display: 'flex', flexDirection: 'column', gap: showDomainHeader ? 12 : 6 }}>
                            {domains.map(({ domain, items }) => {
                              const domainMeta = getDomainMeta(domain);
                              return (
                                <div key={domain}>
                                  {/* 领域子标题（仅当有多个领域时显示） */}
                                  {showDomainHeader && (
                                    <div style={{
                                      display: 'flex',
                                      alignItems: 'center',
                                      gap: 5,
                                      marginBottom: 6,
                                      paddingLeft: 4,
                                    }}>
                                      <span style={{ fontSize: 11 }}>{domainMeta.emoji}</span>
                                      <Text style={{
                                        fontSize: 11,
                                        fontWeight: 500,
                                        color: '#8c8c8c',
                                      }}>
                                        {domainMeta.label}
                                      </Text>
                                      <Text style={{
                                        fontSize: 10,
                                        color: 'var(--fluxion-palette-muted)',
                                        marginLeft: 'auto',
                                      }}>
                                        {items.length}
                                      </Text>
                                    </div>
                                  )}
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                                    {items.map((fn) => {
                                      const nodeMeta = NODE_TYPE_MAP[fn.nodeType];
                                      const color = nodeMeta?.color || meta.color;
                                      return (
                                        <div
                                          key={`${fn.category}:${fn.name}`}
                                          draggable
                                          onDragStart={(e) => onDragStart(e, fn)}
                                          style={{
                                            padding: '10px 12px',
                                            borderRadius: 10,
                                            cursor: 'grab',
                                            background: 'var(--fluxion-palette-surface)',
                                            border: '1px solid var(--fluxion-palette-border)',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 12,
                                            transition: 'all 0.15s ease',
                                          }}
                                          onMouseEnter={(e) => {
                                            e.currentTarget.style.borderColor = color;
                                            e.currentTarget.style.boxShadow = `0 4px 12px ${color}20`;
                                            e.currentTarget.style.transform = 'translateY(-1px)';
                                          }}
                                          onMouseLeave={(e) => {
                                            e.currentTarget.style.borderColor = 'var(--fluxion-palette-border)';
                                            e.currentTarget.style.boxShadow = 'none';
                                            e.currentTarget.style.transform = 'translateY(0)';
                                          }}
                                        >
                                          <div
                                            style={{
                                              width: 36,
                                              height: 36,
                                              borderRadius: 10,
                                              background: `${color}12`,
                                              display: 'flex',
                                              alignItems: 'center',
                                              justifyContent: 'center',
                                              fontSize: 16,
                                              flexShrink: 0,
                                            }}
                                          >
                                            {nodeMeta?.icon || '⚙️'}
                                          </div>
                                          <div style={{ flex: 1, minWidth: 0 }}>
                                            <div style={{
                                              fontWeight: 500,
                                              fontSize: 13,
                                              color: 'var(--fluxion-palette-title)',
                                              lineHeight: 1.3,
                                              whiteSpace: 'nowrap',
                                              overflow: 'hidden',
                                              textOverflow: 'ellipsis',
                                            }}>
                                              {fn.name}
                                            </div>
                                            <div style={{
                                              display: 'flex',
                                              alignItems: 'center',
                                              gap: 6,
                                              fontSize: 11,
                                              color: 'var(--fluxion-palette-muted)',
                                              lineHeight: 1.3,
                                            }}>
                                              <span>{nodeMeta?.label || fn.nodeType}</span>
                                              {/* 单领域时显示领域徽标 */}
                                              {!showDomainHeader && (
                                                <span
                                                  style={{
                                                    fontSize: 10,
                                                    color: domainMeta.color,
                                                    background: `${domainMeta.color}15`,
                                                    padding: '0 4px',
                                                    borderRadius: 4,
                                                    lineHeight: '14px',
                                                  }}
                                                >
                                                  {domainMeta.label}
                                                </span>
                                              )}
                                              {fn.status !== 'ACTIVE' && (
                                                <Tag
                                                  style={{
                                                    fontSize: 10,
                                                    lineHeight: '14px',
                                                    padding: '0 4px',
                                                    borderRadius: 4,
                                                    background: '#fef2f2',
                                                    border: 'none',
                                                    color: '#ef4444',
                                                    margin: 0,
                                                  }}
                                                >
                                                  停用
                                                </Tag>
                                              )}
                                            </div>
                                          </div>
                                        </div>
                                      );
                                    })}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};

export default NodePalette;
