import { useMemo } from 'react';
import {
  Alert,
  Badge,
  Card,
  Collapse,
  Descriptions,
  Empty,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import Editor from '@monaco-editor/react';
import {
  FileTextOutlined,
  LinkOutlined,
  InfoCircleOutlined,
  TableOutlined,
  BranchesOutlined,
  UnorderedListOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { SchemaDefinition } from '@/types/schema';
import {
  SCHEMA_REF_PREFIX,
  flattenSchemaFields,
  dereferenceSchema,
  extractSchemaRefs,
  type FlatFieldInfo,
} from '@/utils/jsonSchema';

const { Text } = Typography;

interface SchemaPreviewProps {
  schema: any;
  schemaName?: string;
  refSchemaMap?: Map<string, SchemaDefinition>;
  loading?: boolean;
}

/**
 * 计算所有引用 schema 中"在某个引用内部出现"的 schema 集合（用于标记二级/多级引用）。
 * 深度保护：若循环引用则停止。
 */
function classifyReferences(
  rootSchema: any,
  refMap: Map<string, SchemaDefinition>,
): { firstLevel: string[]; transitive: string[] } {
  const firstLevel = new Set<string>(extractSchemaRefs(rootSchema));
  const transitive = new Set<string>();
  const visited = new Set<string>();
  const visit = (schemaName: string) => {
    if (visited.has(schemaName)) return;
    visited.add(schemaName);
    const def = refMap.get(schemaName);
    if (!def?.schemaJson) return;
    let inner: any;
    try {
      inner = JSON.parse(def.schemaJson);
    } catch {
      return;
    }
    extractSchemaRefs(inner).forEach((ref) => {
      if (!firstLevel.has(ref)) transitive.add(ref);
      visit(ref);
    });
  };
  firstLevel.forEach((name) => visit(name));
  return {
    firstLevel: Array.from(firstLevel),
    transitive: Array.from(transitive).filter((n) => !firstLevel.has(n)),
  };
}

const SchemaPreview: React.FC<SchemaPreviewProps> = ({
  schema,
  schemaName,
  refSchemaMap = new Map(),
  loading = false,
}) => {
  /** 所有字段（拍平，引用会展开） */
  const flatFields: FlatFieldInfo[] = useMemo(
    () => (schema ? flattenSchemaFields(schema, refSchemaMap, { expandRefs: true }) : []),
    [schema, refSchemaMap],
  );

  /** 只含当前 schema 直接字段，不展开引用（用户可以对比） */
  const directFields: FlatFieldInfo[] = useMemo(
    () => (schema ? flattenSchemaFields(schema, new Map(), { expandRefs: false }) : []),
    [schema],
  );

  /** 递归展开所有引用后的"完整组合 Schema" */
  const combinedSchema = useMemo(
    () => (schema ? dereferenceSchema(schema, refSchemaMap) : null),
    [schema, refSchemaMap],
  );

  const { firstLevel, transitive } = useMemo(
    () => classifyReferences(schema ?? {}, refSchemaMap),
    [schema, refSchemaMap],
  );

  const totalFieldsAll = flatFields.length;
  const requiredCountAll = flatFields.filter((f) => f.required).length;
  const typeSummary = useMemo(() => {
    const counts = new Map<string, number>();
    flatFields.forEach((f) => {
      const key = f.type.startsWith('ref:') ? '引用' : f.type;
      counts.set(key, (counts.get(key) ?? 0) + 1);
    });
    return Array.from(counts.entries()).sort((a, b) => b[1] - a[1]);
  }, [flatFields]);

  const fieldColumns: TableColumnsType<FlatFieldInfo> = [
    {
      title: '路径 / 字段名',
      dataIndex: 'path',
      key: 'path',
      fixed: 'left',
      width: 260,
      sorter: (a, b) => a.path.localeCompare(b.path),
      defaultSortOrder: 'ascend',
      render: (_, rec) => {
        const nameOnly = !rec.path.includes('.') && !rec.path.includes('[');
        return (
          <Space direction="vertical" size={0} style={{ lineHeight: 1.35 }}>
            <Text code style={{ fontSize: 12, background: 'transparent', padding: 0 }}>
              {rec.path || rec.name}
            </Text>
            {!nameOnly && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                (字段名：{rec.name})
              </Text>
            )}
          </Space>
        );
      },
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 160,
      render: (type: string, rec) => {
        if (type.startsWith('ref:')) {
          const refName = type.slice(4);
          const def = refSchemaMap.get(refName);
          return (
            <Tooltip
              title={
                def
                  ? `${def.schemaName} / ${def.schemaType || 'INPUT'} / ${def.scope || 'PLATFORM'}`
                  : '引用未加载'
              }
            >
              <Tag color="#6366f1" icon={<LinkOutlined />} style={{ margin: 0 }}>
                {refName}
              </Tag>
            </Tooltip>
          );
        }
        const colors: Record<string, string> = {
          string: 'blue',
          integer: 'purple',
          number: 'geekblue',
          boolean: 'cyan',
          object: 'magenta',
          array: 'orange',
          null: 'default',
          unknown: 'default',
        };
        return (
          <Space size={4} direction="vertical">
            <Tag color={colors[type] ?? 'default'} style={{ margin: 0 }}>
              {type}
            </Tag>
            {type === 'array' && rec.itemType && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                items: {rec.itemType}
              </Text>
            )}
          </Space>
        );
      },
    },
    {
      title: '格式',
      dataIndex: 'format',
      key: 'format',
      width: 110,
      render: (fmt?: string) => (fmt ? <Tag color="geekblue">{fmt}</Tag> : <span style={{ color: '#bfbfbf' }}>-</span>),
    },
    {
      title: '必填',
      dataIndex: 'required',
      key: 'required',
      width: 72,
      align: 'center',
      render: (req: boolean) => (req ? <Badge status="error" text="是" /> : <span style={{ color: '#8c8c8c' }}>否</span>),
    },
    {
      title: '范围 / 约束',
      key: 'constraints',
      width: 220,
      render: (_, rec) => {
        const parts: string[] = [];
        if (rec.minimum !== undefined) parts.push(`≥ ${rec.minimum}`);
        if (rec.maximum !== undefined) parts.push(`≤ ${rec.maximum}`);
        if (rec.minLength !== undefined) parts.push(`len ≥ ${rec.minLength}`);
        if (rec.maxLength !== undefined) parts.push(`len ≤ ${rec.maxLength}`);
        if (rec.pattern) parts.push(`正则：${rec.pattern}`);
        if (!parts.length && !rec.enum?.length) return <span style={{ color: '#bfbfbf' }}>-</span>;
        return (
          <Space wrap size={[4, 4]}>
            {parts.map((p) => (
              <Tag key={p} color="purple" style={{ fontSize: 11, margin: 0 }}>
                {p}
              </Tag>
            ))}
            {rec.enum?.length ? (
              <Tooltip title={`可选值：${rec.enum.map((v) => String(v)).join(', ')}`}>
                <Tag color="orange" style={{ fontSize: 11, margin: 0 }}>
                  enum ({rec.enum.length} 项)
                </Tag>
              </Tooltip>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (desc?: string) =>
        desc ? (
          <Tooltip title={desc}>
            <span>{desc}</span>
          </Tooltip>
        ) : (
          <span style={{ color: '#bfbfbf' }}>-</span>
        ),
    },
  ];

  const renderFieldTable = (mode: 'expanded' | 'direct') => {
    const rows = mode === 'expanded' ? flatFields : directFields;
    return (
      <Table<FlatFieldInfo>
        rowKey={(r) => `${mode}-${r.path}-${r.type}`}
        size="small"
        columns={fieldColumns}
        dataSource={rows}
        pagination={{
          pageSize: 20,
          size: 'small',
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 个字段`,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        scroll={{ x: 1100 }}
      />
    );
  };

  const renderStatsStrip = () => (
    <Space wrap size={[8, 8]} style={{ marginBottom: 14 }}>
      <Tag icon={<TableOutlined />} color="blue" style={{ margin: 0, padding: '4px 10px', fontSize: 12 }}>
        展开后字段总数：<Text strong>{totalFieldsAll}</Text>
      </Tag>
      <Tag color="red" style={{ margin: 0, padding: '4px 10px', fontSize: 12 }}>
        必填：<Text strong>{requiredCountAll}</Text>
      </Tag>
      <Tag color="purple" style={{ margin: 0, padding: '4px 10px', fontSize: 12 }}>
        直接字段：<Text strong>{directFields.length}</Text>
      </Tag>
      <Tag icon={<LinkOutlined />} color="#6366f1" style={{ margin: 0, padding: '4px 10px', fontSize: 12 }}>
        引用 Schema：<Text strong>{firstLevel.length + transitive.length}</Text>
      </Tag>
      {transitive.length > 0 && (
        <Tag icon={<BranchesOutlined />} color="magenta" style={{ margin: 0, padding: '4px 10px', fontSize: 12 }}>
          含二级/多级引用：<Text strong>{transitive.length}</Text>
        </Tag>
      )}
      {typeSummary.map(([t, c]) => (
        <Tag key={t} color="default" style={{ margin: 0, padding: '2px 8px', fontSize: 11 }}>
          {t}: <Text strong>{c}</Text>
        </Tag>
      ))}
    </Space>
  );

  const renderJsonEditor = (value: any, height = 420, note?: string) => (
    <div style={{ borderRadius: 8, overflow: 'hidden', border: '1px solid #f0f0f0' }}>
      {note && (
        <div
          style={{
            padding: '6px 12px',
            background: '#fafafa',
            borderBottom: '1px solid #f0f0f0',
            fontSize: 12,
            color: '#595959',
            fontWeight: 500,
          }}
        >
          {note}
        </div>
      )}
      <Editor
        height={height}
        language="json"
        value={JSON.stringify(value, null, 2)}
        options={{
          readOnly: true,
          minimap: { enabled: false },
          automaticLayout: true,
          scrollBeyondLastLine: false,
        }}
      />
    </div>
  );

  /** 被引用 schema 的详情卡片，可递归展示其自身的引用摘要 */
  const renderReferencedSchemaDetail = (name: string, depth: number) => {
    const def = refSchemaMap.get(name);
    if (!def) {
      return (
        <Alert
          type="warning"
          showIcon
          message={`引用 Schema「${name}」详情尚未加载`}
          description="可能引用名称不存在、或接口请求未完成，请稍后再试或回到编辑器校验引用关系。"
        />
      );
    }
    let inner: any = null;
    try {
      inner = def.schemaJson ? JSON.parse(def.schemaJson) : null;
    } catch {
      inner = null;
    }
    const occurrenceCount = schema ? extractSchemaRefs(schema).filter((n) => n === name).length : 0;
    const selfRefs = inner ? extractSchemaRefs(inner) : [];
    const topLevelFieldCount = inner
      ? flattenSchemaFields(inner, refSchemaMap, { expandRefs: false }).length
      : 0;
    const expandedFieldCount = inner
      ? flattenSchemaFields(inner, refSchemaMap, { expandRefs: true }).length
      : 0;
    return (
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Descriptions column={2} size="small" bordered style={{ fontSize: 12 }}>
          <Descriptions.Item label="名称">
            <Space>
              <Text strong>{def.schemaName || '-'}</Text>
              {depth > 0 && <Tag color="magenta">深度 {depth + 1} 级引用</Tag>}
              {def.frozen && <Tag color="orange">已冻结</Tag>}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="类型标签">
            <Space size={4}>
              {(def.schemaType || 'INPUT')
                .split(',')
                .map((t) => t.trim())
                .filter(Boolean)
                .map((t) => (
                  <Tag key={t}>{t}</Tag>
                ))}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="格式">{def.schemaFormat || '-'}</Descriptions.Item>
          <Descriptions.Item label="作用域">
            {def.scope === 'PRIVATE' ? (
              <Tag color="blue">
                PRIVATE {def.appGroup ? `· ${def.appGroup}` : ''}
              </Tag>
            ) : (
              <Tag color="purple">PLATFORM</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="顶层字段数 / 展开后字段数">
            {topLevelFieldCount} / {expandedFieldCount}
          </Descriptions.Item>
          {occurrenceCount > 0 ? (
            <Descriptions.Item label="当前 Schema 中引用次数">{occurrenceCount}</Descriptions.Item>
          ) : (
            <Descriptions.Item label="引用来源">被其他引用 Schema 间接引用</Descriptions.Item>
          )}
          {selfRefs.length > 0 && (
            <Descriptions.Item label="内部引用（递归）" span={2}>
              <Space wrap size={4}>
                {selfRefs.map((r) => {
                  const rDef = refSchemaMap.get(r);
                  return (
                    <Tooltip
                      key={r}
                      title={
                        rDef
                          ? `${rDef.schemaName} · ${rDef.schemaType || 'INPUT'} · ${rDef.scope || 'PLATFORM'}`
                          : '未加载'
                      }
                    >
                      <Tag icon={<LinkOutlined />} color="#6366f1" style={{ fontSize: 11 }}>
                        {r}
                      </Tag>
                    </Tooltip>
                  );
                })}
              </Space>
            </Descriptions.Item>
          )}
          {def.description && (
            <Descriptions.Item label="描述" span={2}>
              {def.description}
            </Descriptions.Item>
          )}
        </Descriptions>

        {inner ? (
          <Collapse
            size="small"
            items={[
              {
                key: 'fields',
                label: (
                  <Space>
                    <UnorderedListOutlined />
                    <span>字段列表 (顶层 {topLevelFieldCount} 项，展开引用共 {expandedFieldCount} 项)</span>
                  </Space>
                ),
                children: (() => {
                  const cols: TableColumnsType<FlatFieldInfo> = [
                    { title: '路径 / 字段名', dataIndex: 'path', key: 'name', width: 220, render: (_: string, r) => (
                      <Space direction="vertical" size={0} style={{ lineHeight: 1.3 }}>
                        <Text code style={{ fontSize: 12 }}>{r.path || r.name}</Text>
                        {(r.path.includes('.') || r.path.includes('[')) && (
                          <Text type="secondary" style={{ fontSize: 11 }}>字段名: {r.name}</Text>
                        )}
                      </Space>
                    )},
                    {
                      title: '类型', dataIndex: 'type', key: 'type', width: 140,
                      render: (t: string, rec) => {
                        if (t.startsWith('ref:')) {
                          const refName = t.slice(4);
                          const refDef = refSchemaMap.get(refName);
                          return (
                            <Tooltip title={refDef ? `${refDef.schemaName} · ${refDef.schemaType || 'INPUT'}` : '未加载'}>
                              <Tag color="#6366f1" icon={<LinkOutlined />} style={{ margin: 0 }}>{refName}</Tag>
                            </Tooltip>
                          );
                        }
                        return (
                          <Space direction="vertical" size={0}>
                            <Tag style={{ margin: 0 }}>{t}</Tag>
                            {t === 'array' && rec.itemType && (
                              <Text type="secondary" style={{ fontSize: 11 }}>items: {rec.itemType}</Text>
                            )}
                          </Space>
                        );
                      },
                    },
                    { title: '格式', dataIndex: 'format', key: 'fmt', width: 90, render: (f?: string) => f ? <Tag color="geekblue">{f}</Tag> : <span style={{color:'#bfbfbf'}}>-</span> },
                    {
                      title: '必填', dataIndex: 'required', key: 'req', width: 72, align: 'center',
                      render: (r: boolean) => r ? <Badge status="error" text="是" /> : <span style={{ color: '#8c8c8c' }}>否</span>,
                    },
                    { title: '描述', dataIndex: 'description', key: 'desc', ellipsis: true, render: (d?: string) => d ?? <span style={{ color: '#bfbfbf' }}>-</span> },
                  ];
                  return (
                    <Table<FlatFieldInfo>
                      size="small"
                      rowKey={(r) => `${name}-fields-${r.path}-${r.type}`}
                      columns={cols}
                      dataSource={flattenSchemaFields(inner, refSchemaMap, { expandRefs: true })}
                      pagination={{ pageSize: 10, size: 'small', showSizeChanger: true, pageSizeOptions:['10','20','50'], showTotal:(t)=>`共 ${t} 项` }}
                      scroll={{ x: 860 }}
                    />
                  );
                })(),
              },
              {
                key: 'json',
                label: (
                  <Space>
                    <FileTextOutlined />
                    <span>JSON 定义</span>
                  </Space>
                ),
                children: renderJsonEditor(inner, 280),
              },
            ]}
          />
        ) : (
          <Empty description="Schema JSON 内容为空或格式异常" />
        )}
      </Space>
    );
  };

  const refCollapseItems = () => {
    const allNames = Array.from(new Set([...firstLevel, ...transitive]));
    if (allNames.length === 0) return [];
    return allNames.map((name) => {
      const depth = firstLevel.includes(name) ? 0 : 1;
      const def = refSchemaMap.get(name);
      const label = (
        <Space wrap size={[8, 4]}>
          <LinkOutlined style={{ color: '#6366f1' }} />
          <Text strong>{name}</Text>
          {def?.frozen && <Tag color="orange">已冻结</Tag>}
          {depth > 0 && <Tag color="magenta">二级+ 引用</Tag>}
          {def?.schemaType && (
            <Space size={2}>
              {def.schemaType
                .split(',')
                .map((t) => t.trim())
                .filter(Boolean)
                .map((t) => (
                  <Tag key={t} color="blue" style={{ fontSize: 11 }}>
                    {t}
                  </Tag>
                ))}
            </Space>
          )}
          {def?.scope === 'PRIVATE' ? (
            <Tag color="purple" style={{ fontSize: 11 }}>
              私有
            </Tag>
          ) : (
            <Tag color="default" style={{ fontSize: 11 }}>
              平台
            </Tag>
          )}
        </Space>
      );
      return {
        key: name,
        label,
        children: renderReferencedSchemaDetail(name, depth),
      };
    });
  };

  const allRefItems = refCollapseItems();

  return (
    <Spin spinning={loading} tip="正在加载引用 Schema 的完整预览...">
      <Card
        title={
          <Space wrap>
            <span>Schema 完整预览</span>
            {schemaName && (
              <Tooltip title="当前 Schema">
                <Tag color="geekblue" icon={<ThunderboltOutlined />}>
                  {schemaName}
                </Tag>
              </Tooltip>
            )}
            {loading && <Tag color="processing">引用加载中...</Tag>}
          </Space>
        }
        styles={{ body: { paddingTop: 14, paddingBottom: 4 } }}
      >
        {renderStatsStrip()}

        <Collapse
          defaultActiveKey={['fields-expanded', 'combined', 'refs', 'original']}
          size="small"
          items={[
            {
              key: 'fields-expanded',
              label: (
                <Space>
                  <TableOutlined style={{ color: '#1677ff' }} />
                  <span>
                    <Text strong>展开后完整字段表</Text>（所有引用递归展开，共 {flatFields.length} 条）
                  </span>
                </Space>
              ),
              children: renderFieldTable('expanded'),
              extra: <Text type="secondary" style={{ fontSize: 12, marginRight: 12 }}>推荐阅读视图</Text>,
            },
            {
              key: 'fields-direct',
              label: (
                <Space>
                  <UnorderedListOutlined style={{ color: '#52c41a' }} />
                  <span>
                    当前 Schema 直接字段（{directFields.length} 条，不展开引用）
                  </span>
                </Space>
              ),
              children: renderFieldTable('direct'),
            },
            {
              key: 'combined',
              label: (
                <Space>
                  <BranchesOutlined style={{ color: '#722ed1' }} />
                  <span>
                    <Text strong>组合后完整 Schema（所有引用内联展开）</Text>
                  </span>
                </Space>
              ),
              children:
                combinedSchema ? (
                  <Space direction="vertical" size={12} style={{ width: '100%' }}>
                    <Alert
                      type="info"
                      showIcon
                      icon={<InfoCircleOutlined />}
                      message={
                        firstLevel.length + transitive.length === 0
                          ? '当前 Schema 没有外部引用，组合视图即原 Schema。'
                          : `将所有 schema: 前缀的 $ref 递归替换为目标 schema 的内容；循环引用或未加载的引用会保留 \$ref 字符串并标注 _note。`
                      }
                    />
                    {renderJsonEditor(combinedSchema, 560, '组合后 JSON Schema（可直接复制用于外部校验/文档）')}
                  </Space>
                ) : (
                  <Empty description="无 Schema 内容" />
                ),
            },
            allRefItems.length > 0
              ? {
                  key: 'refs',
                  label: (
                    <Space>
                      <LinkOutlined style={{ color: '#6366f1' }} />
                      <span>
                        <Text strong>所有引用 Schema 详情</Text>（含被引用的引用，共 {allRefItems.length} 个）
                      </span>
                    </Space>
                  ),
                  children: <Collapse size="small" defaultActiveKey={firstLevel.slice(0, 1)} items={allRefItems} />,
                }
              : {
                  key: 'refs',
                  label: (
                    <Space>
                      <LinkOutlined />
                      <span>引用 Schema</span>
                    </Space>
                  ),
                  children: (
                    <Empty
                      description={
                        <Space direction="vertical" align="center" size={4}>
                          <Text>当前 Schema 未引用任何已注册的 Schema（通过 $ref: schema:XXX 引用）</Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            若需要复用类型，请先创建其他 Schema，然后在字段类型中选择「引用 Schema」
                          </Text>
                        </Space>
                      }
                    />
                  ),
                },
            {
              key: 'original',
              label: (
                <Space>
                  <FileTextOutlined />
                  <span>当前 Schema 原始 JSON</span>
                </Space>
              ),
              children: schema ? renderJsonEditor(schema, 480, '原始定义（保留 $ref: schema:xxx 形式）') : <Empty />,
            },
          ]}
        />
      </Card>
    </Spin>
  );
};

export default SchemaPreview;
