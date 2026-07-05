import { history, useAccess, useRequest } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Dropdown,
  Empty,
  Input,
  Modal,
  Pagination,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Tabs,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  CloudUploadOutlined,
  StopOutlined,
  AppstoreOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { getFunctions, deleteFunction, publishFunction, deprecateFunction } from '@/services/function';
import { useClickDebounce } from '@/utils/useClickDebounce';
import type { FunctionDefinition } from '@/types/function';
import {
  FUNCTION_GROUPS,
  FUNCTION_GROUP_ORDER,
  FUNCTION_GROUP_MAP,
  getFunctionDisplayName,
  getFunctionGroupKey,
  getFunctionDescription,
  getFunctionFullDescription,
} from '@/constants/functionDisplay';
import { getDomainMeta } from '@/constants/domain';
import { NODE_TYPE_MAP } from '@/constants/nodeTypes';

const { Text, Paragraph } = Typography;

const CATEGORY_OPTIONS = [
  { value: 'BUILTIN', label: '内置' },
  { value: 'CUSTOM', label: '自定义' },
  { value: 'SCRIPT', label: '脚本' },
  { value: 'EXTERNAL', label: '外部' },
];

const SCOPE_META: Record<string, { label: string; color: string }> = {
  PLATFORM: { label: '平台内置', color: '#722ed1' },
  PRIVATE: { label: '应用私有', color: '#1890ff' },
  MARKETPLACE: { label: '市场', color: '#fa8c16' },
};

const FunctionList: React.FC = () => {
  const access = useAccess();
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<string>();
  const [activeGroup, setActiveGroup] = useState<string>('all');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  // 搜索条件变化时重置到第一页，并回到“全部”分组
  useEffect(() => {
    setPage(1);
    setActiveGroup('all');
  }, [keyword, category]);

  // 切换分组时重置分页
  useEffect(() => {
    setPage(1);
  }, [activeGroup]);

  const { data, loading, refresh } = useRequest(
    () => getFunctions({ keyword, category, page: 0, pageSize: 1000 }),
    { formatResult: (res) => res, refreshDeps: [keyword, category] },
  );

  const allFunctions = useMemo(() => data?.list || [], [data]);

  const currentList = useMemo(() => {
    if (activeGroup === 'all') return allFunctions;
    return allFunctions.filter((fn) => getFunctionGroupKey(fn) === activeGroup);
  }, [allFunctions, activeGroup]);

  const currentPageList = useMemo(
    () => currentList.slice((page - 1) * pageSize, page * pageSize),
    [currentList, page, pageSize],
  );

  // 按能力分组统计数量，用于分组标签展示
  const groupCounts = useMemo(() => {
    const counts: Record<string, number> = { all: allFunctions.length };
    allFunctions.forEach((fn) => {
      const key = getFunctionGroupKey(fn);
      counts[key] = (counts[key] || 0) + 1;
    });
    return counts;
  }, [allFunctions]);

  // 将当前分页数据按分组 key 排序展示
  const grouped = useMemo(() => {
    const map = new Map<string, FunctionDefinition[]>();
    currentPageList.forEach((fn) => {
      const key = getFunctionGroupKey(fn);
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(fn);
    });

    const sorted: [string, FunctionDefinition[]][] = [];
    FUNCTION_GROUP_ORDER.forEach((key) => {
      if (map.has(key)) sorted.push([key, map.get(key)!]);
    });
    map.forEach((items, key) => {
      if (!FUNCTION_GROUP_ORDER.includes(key)) sorted.push([key, items]);
    });
    return sorted;
  }, [currentPageList]);

  const handlePublish = useClickDebounce(async (record: FunctionDefinition) => {
    if (!record.name) return;
    await publishFunction(record.name);
    message.success('函数已发布到 Worker');
    refresh();
  });

  const handleDeprecate = useClickDebounce(async (record: FunctionDefinition) => {
    if (!record.name) return;
    await deprecateFunction(record.name);
    message.success('函数已下线');
    refresh();
  });

  const handleDelete = useClickDebounce((record: FunctionDefinition) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定删除函数「${record.name}」吗？`,
      onOk: async () => {
        if (!record.name) return;
        await deleteFunction(record.name);
        message.success('删除成功');
        refresh();
      },
    });
  });

  const renderFunctionCard = (fn: FunctionDefinition) => {
    const isBuiltin = fn.category === 'BUILTIN';
    const displayName = getFunctionDisplayName(fn);
    const domain = (fn.config?.domain as string) || 'other';
    const domainMeta = getDomainMeta(domain);
    const nodeMeta = NODE_TYPE_MAP[fn.nodeType];
    const icon = nodeMeta?.icon || domainMeta.emoji;
    const color = nodeMeta?.color || domainMeta.color;
    const scope = fn.scope || 'PRIVATE';
    const scopeCfg = SCOPE_META[scope] || SCOPE_META.PRIVATE;

    const menuItems = [
      {
        key: 'view',
        label: '查看',
        icon: <EyeOutlined />,
        onClick: () => history.push(`/function/editor/${fn.id}?readonly=1`),
      },
      ...(!isBuiltin && access.canEditFunction
        ? [
            {
              key: 'edit',
              label: '编辑',
              icon: <EditOutlined />,
              onClick: () => history.push(`/function/editor/${fn.id}`),
            },
            fn.status === 'ACTIVE'
              ? {
                  key: 'deprecate',
                  label: '下线',
                  icon: <StopOutlined />,
                  danger: true,
                  onClick: () => handleDeprecate(fn),
                }
              : {
                  key: 'publish',
                  label: '发布',
                  icon: <CloudUploadOutlined />,
                  onClick: () => handlePublish(fn),
                },
            {
              key: 'delete',
              label: '删除',
              icon: <DeleteOutlined />,
              danger: true,
              onClick: () => handleDelete(fn),
            },
          ]
        : []),
    ];

    const handleCardClick = () => {
      if (isBuiltin || !access.canEditFunction) {
        history.push(`/function/editor/${fn.id}?readonly=1`);
      } else {
        history.push(`/function/editor/${fn.id}`);
      }
    };

    return (
      <Card
        key={fn.id}
        size="small"
        hoverable
        onClick={handleCardClick}
        styles={{
          body: {
            padding: 14,
            display: 'flex',
            flexDirection: 'column',
            height: '100%',
          },
        }}
        style={{ borderRadius: 10, borderColor: 'var(--fluxion-border)', height: '100%', cursor: 'pointer' }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 10 }}>
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: `${color}12`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 18,
              flexShrink: 0,
            }}
          >
            {icon}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2 }}>
              <Text strong style={{ fontSize: 15, color: 'var(--fluxion-text)' }} ellipsis={{ tooltip: displayName }}>
                {displayName}
              </Text>
              {fn.status === 'ACTIVE' ? (
                <Tag color="success" style={{ margin: 0, fontSize: 11, padding: '0 6px' }}>
                  可用
                </Tag>
              ) : (
                <Tag style={{ margin: 0, fontSize: 11, padding: '0 6px' }}>停用</Tag>
              )}
            </div>
            <Text type="secondary" style={{ fontSize: 12 }} ellipsis={{ tooltip: fn.name }}>
              {fn.name}
            </Text>
          </div>
          <Dropdown menu={{ items: menuItems as any }} placement="bottomRight" arrow>
            <span onClick={(e) => e.stopPropagation()}>
              <Button type="text" size="small" icon={<MoreOutlined />} title="操作" />
            </span>
          </Dropdown>
        </div>

        <Tooltip title={getFunctionFullDescription(fn)}>
          <Paragraph
            type="secondary"
            ellipsis={{ rows: 2, expandable: false }}
            style={{ fontSize: 13, lineHeight: 1.5, marginBottom: 12, flex: 1 }}
          >
            {getFunctionDescription(fn, 80)}
          </Paragraph>
        </Tooltip>

        <Space size={4} wrap>
          <Tag size="small" style={{ fontSize: 11, margin: 0 }}>
            {fn.category}
          </Tag>
          <Tag size="small" color={scopeCfg.color} style={{ fontSize: 11, margin: 0 }}>
            {scopeCfg.label}
          </Tag>
          <Tag
            size="small"
            style={{
              fontSize: 11,
              margin: 0,
              color: domainMeta.color,
              background: `${domainMeta.color}10`,
              borderColor: `${domainMeta.color}30`,
            }}
          >
            {domainMeta.label}
          </Tag>
          {nodeMeta && (
            <Tag size="small" style={{ fontSize: 11, margin: 0 }}>
              {nodeMeta.label}
            </Tag>
          )}
        </Space>
      </Card>
    );
  };

  return (
    <Card
      title={
        <Space>
          <AppstoreOutlined style={{ fontSize: 18, color: 'var(--fluxion-primary)' }} />
          <span>函数管理</span>
        </Space>
      }
      styles={{ body: { padding: 16 } }}
      extra={
        <Space>
          <Select
            placeholder="类别"
            allowClear
            value={category}
            onChange={setCategory}
            options={CATEGORY_OPTIONS}
            style={{ width: 120 }}
          />
          <Input.Search
            placeholder="搜索函数"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={refresh}
            style={{ width: 240 }}
          />
          {access.canEditFunction && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/function/editor')}>
              注册函数
            </Button>
          )}
        </Space>
      }
    >
      <Tabs
        activeKey={activeGroup}
        onChange={setActiveGroup}
        type="card"
        items={[
          {
            key: 'all',
            label: (
              <Space size={6}>
                <span>📚</span>
                <span>全部</span>
                <Tag
                  style={{
                    margin: 0,
                    fontSize: 11,
                    lineHeight: '16px',
                    borderRadius: 10,
                    background: activeGroup === 'all' ? 'var(--fluxion-primary)' : 'var(--fluxion-border-light)',
                    color: activeGroup === 'all' ? 'var(--fluxion-text-inverse)' : 'var(--fluxion-text-secondary)',
                    border: 'none',
                  }}
                >
                  {groupCounts.all || 0}
                </Tag>
              </Space>
            ),
          },
          ...FUNCTION_GROUPS.filter((g) => (groupCounts[g.key] || 0) > 0).map((g) => ({
            key: g.key,
            label: (
              <Space size={6}>
                <span>{g.icon}</span>
                <span>{g.label}</span>
                <Tag
                  style={{
                    margin: 0,
                    fontSize: 11,
                    lineHeight: '16px',
                    borderRadius: 10,
                    background: activeGroup === g.key ? g.color : 'var(--fluxion-border-light)',
                    color: activeGroup === g.key ? 'var(--fluxion-text-inverse)' : 'var(--fluxion-text-secondary)',
                    border: 'none',
                  }}
                >
                  {groupCounts[g.key] || 0}
                </Tag>
              </Space>
            ),
          })),
        ]}
        style={{ marginTop: -8, marginBottom: 16 }}
      />

      {currentPageList.length > 0 && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
          <Pagination
            current={page}
            pageSize={pageSize}
            total={currentList.length}
            showTotal={(total) => `共 ${total} 条`}
            showSizeChanger
            pageSizeOptions={['12', '20', '40', '80']}
            onChange={(p, ps) => {
              setPage(p);
              setPageSize(ps);
            }}
          />
        </div>
      )}

      <Spin spinning={loading} tip="加载中...">
        {!loading && currentPageList.length === 0 ? (
          <Empty
            description={keyword || category ? '未找到匹配的函数' : '暂无函数，请先在函数管理中创建'}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <>
            {grouped.map(([groupKey, items]) => {
              const meta = FUNCTION_GROUP_MAP[groupKey];
              return (
                <div key={groupKey} style={{ marginBottom: 24 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                    <span style={{ fontSize: 16 }}>{meta?.icon || '📦'}</span>
                    <Text strong style={{ fontSize: 14, color: 'var(--fluxion-text)' }}>
                      {meta?.label || groupKey}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12, color: 'var(--fluxion-text-secondary)' }}>
                      {meta?.description}
                    </Text>
                    <Tag
                      style={{
                        marginLeft: 'auto',
                        fontSize: 11,
                        lineHeight: '16px',
                        borderRadius: 10,
                        background: `${meta?.color || 'var(--fluxion-text-muted)'}15`,
                        color: meta?.color || 'var(--fluxion-text-muted)',
                        border: 'none',
                      }}
                    >
                      {items.length}
                    </Tag>
                  </div>
                  <Row gutter={[16, 16]}>
                    {items.map((fn) => (
                      <Col key={fn.id} xs={24} sm={12} lg={8} xl={6}>
                        {renderFunctionCard(fn)}
                      </Col>
                    ))}
                  </Row>
                </div>
              );
            })}
            {currentPageList.length > 0 && (
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
                <Pagination
                  current={page}
                  pageSize={pageSize}
                  total={currentList.length}
                  showTotal={(total) => `共 ${total} 条`}
                  showSizeChanger
                  pageSizeOptions={['12', '20', '40', '80']}
                  onChange={(p, ps) => {
                    setPage(p);
                    setPageSize(ps);
                  }}
                />
              </div>
            )}
          </>
        )}
      </Spin>
    </Card>
  );
};

export default FunctionList;
