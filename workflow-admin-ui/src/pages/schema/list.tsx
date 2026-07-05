import { history, useAccess, useRequest } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Empty,
  Input,
  Modal,
  Row,
  Segmented,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  HistoryOutlined,
  FileTextOutlined,
  LockOutlined,
  ClockCircleOutlined,
  AppstoreOutlined,
  UnorderedListOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { getSchemas, deleteSchema } from '@/services/schema';
import { useClickDebounce } from '@/utils/useClickDebounce';
import type { SchemaDefinition } from '@/types/schema';

const { Text } = Typography;

const TYPE_TAG_COLOR: Record<string, string> = {
  INPUT: '#6366f1',
  OUTPUT: '#52c41a',
  EVENT: '#f59e0b',
};

const scopeConfig: Record<string, { label: string; color: string }> = {
  PLATFORM: { label: '平台通用', color: '#722ed1' },
  PRIVATE: { label: '应用私有', color: '#1890ff' },
};

const FORMAT_LABEL: Record<string, string> = {
  'json-schema': 'JSON Schema',
  protobuf: 'Protobuf',
  avro: 'Avro',
};

const SchemaList: React.FC = () => {
  const access = useAccess();
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [viewMode, setViewMode] = useState<string>('card');

  const { data, loading, refresh } = useRequest(
    () => getSchemas({ keyword }),
    { formatResult: (res) => res, refreshDeps: [keyword] },
  );

  const allSchemas = data?.list || [];

  const schemas = useMemo(() => {
    if (typeFilter === 'all') return allSchemas;
    return allSchemas.filter((s: SchemaDefinition) => {
      const types = (s.schemaType || 'INPUT').split(',').map((t) => t.trim());
      return types.includes(typeFilter);
    });
  }, [allSchemas, typeFilter]);

  const typeCounts = useMemo(() => {
    const counts: Record<string, number> = { all: allSchemas.length };
    allSchemas.forEach((s: SchemaDefinition) => {
      const types = (s.schemaType || 'INPUT').split(',').map((t) => t.trim());
      types.forEach((t) => {
        counts[t] = (counts[t] || 0) + 1;
      });
    });
    return counts;
  }, [allSchemas]);

  const handleDelete = useClickDebounce((record: SchemaDefinition) => {
    if (record.frozen && !access.canUnlockSchema) {
      message.error('该 Schema 已冻结，暂不可删除');
      return;
    }
    if (!access.canEditSchema || !record.schemaName) return;
    Modal.confirm({
      title: '确认删除',
      content: `确定删除 Schema「${record.schemaName}」吗？`,
      onOk: async () => {
        if (!record.schemaName) return;
        try {
          await deleteSchema(record.schemaName);
          message.success('删除成功');
          refresh();
        } catch (e: any) {
          message.error(e?.message || '删除失败');
        }
      },
    });
  });

  return (
    <div>
      {/* 顶部操作栏 */}
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space>
            <FileTextOutlined style={{ fontSize: 20, color: '#6366f1' }} />
            <Text strong style={{ fontSize: 16 }}>Schema 管理</Text>
            <Tag style={{ borderRadius: 4, marginLeft: 8 }}>{allSchemas.length} 个</Tag>
          </Space>
          <Space>
            <Input.Search
              placeholder="搜索 Schema"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onSearch={refresh}
              style={{ width: 240 }}
            />
            {access.canEditSchema && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/schema/editor')}>
                新建 Schema
              </Button>
            )}
            <Segmented
              value={viewMode}
              onChange={(v) => setViewMode(v as string)}
              options={[
                { value: 'card', icon: <AppstoreOutlined /> },
                { value: 'table', icon: <UnorderedListOutlined /> },
              ]}
              size="small"
            />
          </Space>
        </div>
      </Card>

      {/* 类型筛选 + 内容区 */}
      <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
        {/* 类型筛选 Tabs */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
          <Text type="secondary" style={{ fontSize: 12, flexShrink: 0 }}>类型筛选：</Text>
          <Select
            value={typeFilter}
            onChange={setTypeFilter}
            style={{ width: 160 }}
            size="small"
            options={[
              { label: `全部 (${typeCounts.all || 0})`, value: 'all' },
              { label: `INPUT (${typeCounts.INPUT || 0})`, value: 'INPUT' },
              { label: `OUTPUT (${typeCounts.OUTPUT || 0})`, value: 'OUTPUT' },
              { label: `EVENT (${typeCounts.EVENT || 0})`, value: 'EVENT' },
            ]}
          />
        </div>

        {/* 卡片视图 */}
        {viewMode === 'card' && (
          <>
            {schemas.length === 0 && !loading ? (
              <Empty description="暂无 Schema" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: '40px 0' }} />
            ) : (
              <Row gutter={[16, 16]}>
                {schemas.map((record: SchemaDefinition) => {
                  const isLocked = record.frozen && !access.canUnlockSchema;
                  const scope = scopeConfig[record.scope || 'PLATFORM'];
                  const types = (record.schemaType || 'INPUT').split(',').map((t) => t.trim());
                  return (
                    <Col key={record.schemaName || record.id} xs={24} sm={12} lg={8} xl={6}>
                      <Card
                        hoverable
                        size="small"
                        className="workflow-list-card"
                        onClick={() => history.push(`/schema/editor/${record.schemaName}?mode=view`)}
                        style={{ borderRadius: 12, border: '1px solid #f0f0f0', height: '100%' }}
                        title={
                          <Tooltip title={record.schemaName} placement="topLeft">
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                              <FileTextOutlined style={{ color: '#6366f1', flexShrink: 0 }} />
                              <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>
                                {record.schemaName}
                              </span>
                              {record.frozen && (
                                <Tooltip title="已冻结">
                                  <LockOutlined style={{ color: '#fa8c16', flexShrink: 0 }} />
                                </Tooltip>
                              )}
                            </div>
                          </Tooltip>
                        }
                        extra={
                          <Tag
                            color={scope.color}
                            style={{ borderRadius: 4, fontSize: 11, margin: 0 }}
                          >
                            {scope.label}
                          </Tag>
                        }
                        actions={[
                          <Button
                            key="view"
                            type="text"
                            size="small"
                            icon={<EyeOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              history.push(`/schema/editor/${record.schemaName}?mode=view`);
                            }}
                          >
                            查看
                          </Button>,
                          <Button
                            key="versions"
                            type="text"
                            size="small"
                            icon={<HistoryOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              history.push(`/schema/versions/${record.schemaName}`);
                            }}
                          >
                            版本
                          </Button>,
                          ...(access.canEditSchema
                            ? [
                                <Tooltip key="delete" title={isLocked ? '该 Schema 已冻结，暂不可删除' : undefined}>
                                  <span>
                                    <Button
                                      type="text"
                                      size="small"
                                      danger
                                      icon={<DeleteOutlined />}
                                      disabled={isLocked}
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handleDelete(record);
                                      }}
                                    >
                                      删除
                                    </Button>
                                  </span>
                                </Tooltip>,
                              ]
                            : []),
                        ]}
                      >
                        {/* 描述 */}
                        <Tooltip title={record.description || '暂无描述'} placement="topLeft">
                          <div style={{
                            color: '#595959',
                            fontSize: 12,
                            marginBottom: 10,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            minHeight: 20,
                          }}>
                            {record.description || '暂无描述'}
                          </div>
                        </Tooltip>
                        {/* 类型标签 + 格式 + 作用域 */}
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                          <Space wrap size={4}>
                            {types.map((t) => (
                              <Tag
                                key={t}
                                color={`${TYPE_TAG_COLOR[t] || '#8c8c8c'}15`}
                                style={{
                                  color: TYPE_TAG_COLOR[t] || '#8c8c8c',
                                  borderColor: `${TYPE_TAG_COLOR[t] || '#8c8c8c'}40`,
                                  borderRadius: 4,
                                  fontSize: 11,
                                }}
                              >
                                {t}
                              </Tag>
                            ))}
                            <Tag color="blue" style={{ borderRadius: 4, fontSize: 11 }}>
                              {FORMAT_LABEL[record.schemaFormat] || record.schemaFormat}
                            </Tag>
                          </Space>
                          {(record.refCount ?? 0) > 0 && (
                            <Tag color={record.refCount! > 5 ? 'blue' : 'default'} style={{ borderRadius: 4, fontSize: 11 }}>
                              引用 {record.refCount}
                            </Tag>
                          )}
                        </div>
                        {/* 应用分组 + 更新时间 */}
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          {record.scope === 'PRIVATE' && record.appGroup ? (
                            <Tag style={{ borderRadius: 4, fontSize: 11 }}>{record.appGroup}</Tag>
                          ) : (
                            <span />
                          )}
                          <Text type="secondary" style={{ fontSize: 11 }}>
                            <ClockCircleOutlined style={{ marginRight: 4 }} />
                            {record.updatedAt ? new Date(record.updatedAt).toLocaleDateString() : '-'}
                          </Text>
                        </div>
                      </Card>
                    </Col>
                  );
                })}
              </Row>
            )}
          </>
        )}

        {/* 表格视图（保留原始表格模式作为备选） */}
        {viewMode === 'table' && (
          <div>
            {schemas.length === 0 && !loading ? (
              <Empty description="暂无 Schema" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: '40px 0' }} />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {schemas.map((record: SchemaDefinition) => {
                  const isLocked = record.frozen && !access.canUnlockSchema;
                  const scope = scopeConfig[record.scope || 'PLATFORM'];
                  const types = (record.schemaType || 'INPUT').split(',').map((t) => t.trim());
                  return (
                    <div
                      key={record.schemaName || record.id}
                      onClick={() => history.push(`/schema/editor/${record.schemaName}?mode=view`)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '10px 16px',
                        borderRadius: 8,
                        border: '1px solid #f0f0f0',
                        cursor: 'pointer',
                        transition: 'all 0.2s',
                      }}
                      onMouseEnter={(e) => {
                        (e.currentTarget as HTMLDivElement).style.borderColor = '#6366f1';
                        (e.currentTarget as HTMLDivElement).style.backgroundColor = '#fafafa';
                      }}
                      onMouseLeave={(e) => {
                        (e.currentTarget as HTMLDivElement).style.borderColor = '#f0f0f0';
                        (e.currentTarget as HTMLDivElement).style.backgroundColor = 'transparent';
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0, flex: 1 }}>
                        <FileTextOutlined style={{ color: '#6366f1', fontSize: 16, flexShrink: 0 }} />
                        <Text strong style={{ fontSize: 13, flexShrink: 0 }}>{record.schemaName}</Text>
                        {record.frozen && (
                          <Tooltip title="已冻结">
                            <LockOutlined style={{ color: '#fa8c16' }} />
                          </Tooltip>
                        )}
                        <Space wrap size={4}>
                          {types.map((t) => (
                            <Tag
                              key={t}
                              color={`${TYPE_TAG_COLOR[t] || '#8c8c8c'}15`}
                              style={{
                                color: TYPE_TAG_COLOR[t] || '#8c8c8c',
                                borderColor: `${TYPE_TAG_COLOR[t] || '#8c8c8c'}40`,
                                borderRadius: 4,
                                fontSize: 11,
                              }}
                            >
                              {t}
                            </Tag>
                          ))}
                          <Tag color="blue" style={{ borderRadius: 4, fontSize: 11 }}>
                            {FORMAT_LABEL[record.schemaFormat] || record.schemaFormat}
                          </Tag>
                        </Space>
                        <Tag color={scope.color} style={{ borderRadius: 4, fontSize: 11 }}>{scope.label}</Tag>
                        {record.scope === 'PRIVATE' && record.appGroup && (
                          <Tag style={{ borderRadius: 4, fontSize: 11 }}>{record.appGroup}</Tag>
                        )}
                        <Text type="secondary" style={{ fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {record.description || ''}
                        </Text>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                        {(record.refCount ?? 0) > 0 && (
                          <Tag color={record.refCount! > 5 ? 'blue' : 'default'} style={{ borderRadius: 4, fontSize: 11 }}>
                            引用 {record.refCount}
                          </Tag>
                        )}
                        <Text type="secondary" style={{ fontSize: 11 }}>
                          {record.updatedAt ? new Date(record.updatedAt).toLocaleDateString() : '-'}
                        </Text>
                        <Space size={4}>
                          <Button
                            size="small"
                            icon={<EyeOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              history.push(`/schema/editor/${record.schemaName}?mode=view`);
                            }}
                          >
                            查看
                          </Button>
                          <Button
                            size="small"
                            icon={<HistoryOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              history.push(`/schema/versions/${record.schemaName}`);
                            }}
                          >
                            版本
                          </Button>
                          {access.canEditSchema && (
                            <Tooltip title={isLocked ? '该 Schema 已冻结，暂不可删除' : undefined}>
                              <span>
                                <Button
                                  size="small"
                                  danger
                                  icon={<DeleteOutlined />}
                                  disabled={isLocked}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleDelete(record);
                                  }}
                                />
                              </span>
                            </Tooltip>
                          )}
                        </Space>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  );
};

export default SchemaList;
