import { history, useAccess, useRequest } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Empty,
  Input,
  Modal,
  Pagination,
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
  ArrowUpOutlined,
  ArrowDownOutlined,
  DatabaseOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { getSchemas, deleteSchema } from '@/services/schema';
import { listApps } from '@/services/apps';
import { useClickDebounce } from '@/utils/useClickDebounce';
import type { SchemaDefinition } from '@/types/schema';
import { DOMAIN_META, getDomainMeta, sortDomains } from '@/constants/domain';

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

const PAGINATION_SIZE_OPTIONS = [10, 20, 50, 100];
const SchemaList: React.FC = () => {
  const access = useAccess();
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [domainFilter, setDomainFilter] = useState<string>('all');
  const [frozenFilter, setFrozenFilter] = useState<'all' | 'frozen' | 'unfrozen'>('all');
  const [appGroupFilter, setAppGroupFilter] = useState<string>('all');
  const [sortField, setSortField] = useState<'createdAt' | 'updatedAt'>('updatedAt');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');
  const [viewMode, setViewMode] = useState<string>('card');
  const [pageIndex, setPageIndex] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(20);

  const { data: appsData, loading: appsLoading } = useRequest(
    () => listApps(),
    { formatResult: (r: any) => r },
  );

  // When any filter changes, reset to page 1 so the user doesn't land on an empty page.
  useEffect(() => {
    setPageIndex(1);
  }, [keyword, domainFilter, typeFilter, frozenFilter, appGroupFilter, sortField, sortDirection]);

  const frozenQuery = frozenFilter === 'frozen' ? true : frozenFilter === 'unfrozen' ? false : undefined;
  const appGroupQuery = appGroupFilter === 'all' ? undefined : appGroupFilter;

  const { data, loading, refresh } = useRequest(
    () =>
      getSchemas({
        keyword,
        schemaType: typeFilter === 'all' ? undefined : typeFilter,
        domain: domainFilter === 'all' ? undefined : domainFilter,
        frozen: frozenQuery,
        appGroup: appGroupQuery,
        sortField,
        sortDirection,
        page: pageIndex - 1,
        pageSize,
      }),
    {
      formatResult: (res) => res,
      refreshDeps: [keyword, domainFilter, typeFilter, frozenFilter, appGroupFilter, sortField, sortDirection, pageIndex, pageSize],
    },
  );

  const schemas = data?.list || [];
  const total = Number(data?.total ?? 0);
  const frozenCount = schemas.filter((s) => s.frozen).length;

  const handlePageChange = (p: number, s: number) => {
    setPageIndex(p);
    setPageSize(s);
  };

  const TopPagination = (
    <Pagination
      size="small"
      current={pageIndex}
      pageSize={pageSize}
      total={total}
      showQuickJumper={total > 100}
      pageSizeOptions={PAGINATION_SIZE_OPTIONS}
      onChange={handlePageChange}
      style={{ margin: 0 }}
    />
  );

  const BottomPagination = (
    <Pagination
      current={pageIndex}
      pageSize={pageSize}
      total={total}
      showSizeChanger
      showQuickJumper
      showTotal={(t) => `共 ${t} 个`}
      pageSizeOptions={PAGINATION_SIZE_OPTIONS}
      onChange={handlePageChange}
    />
  );

  const frozenOptions = useMemo(
    () => [
      { label: `全部状态 (${total})`, value: 'all' as const },
      { label: `❄️ 已冻结 (${frozenCount})`, value: 'frozen' as const },
      { label: `🟢 未冻结 (${Math.max(0, total - frozenCount)})`, value: 'unfrozen' as const },
    ],
    [total, frozenCount],
  );

  const appOptions = useMemo(() => {
    const apps = (Array.isArray(appsData) ? appsData : (appsData as any)?.list ?? []) as any[];
    return [
      { label: '全部应用', value: 'all' },
      ...apps
        .filter((a: any) => {
          if (a == null) return false;
          if (!a.status) return true;
          const s = String(a.status).toUpperCase();
          if (s === 'DISABLED' || s === 'INACTIVE' || s === '0') return false;
          return true;
        })
        .map((a: any) => ({
          label: `${a.appName ? `${a.appName} — ` : ''}${a.appKey ?? a.id ?? ''}${a.owner ? `（负责人：${a.owner}）` : ''}`,
          value: String(a.appKey ?? a.id ?? ''),
        })),
    ];
  }, [appsData]);

  const domainOptions = useMemo(() => {
    const allDomains = sortDomains(Object.keys(DOMAIN_META).filter((k) => k !== 'all'));
    return [
      { label: `${DOMAIN_META.all.emoji} 全部 (${total})`, value: 'all' },
      ...allDomains.map((key) => {
        const meta = getDomainMeta(key);
        return {
          label: `${meta.emoji} ${meta.label}`,
          value: key,
        };
      }),
    ];
  }, [total]);

  const typeOptions = useMemo(() => {
    return [
      { label: `全部 (${total})`, value: 'all' },
      { label: 'INPUT', value: 'INPUT' },
      { label: 'OUTPUT', value: 'OUTPUT' },
      { label: 'EVENT', value: 'EVENT' },
    ];
  }, [total]);

  const handleDelete = useClickDebounce((record: SchemaDefinition) => {
    if (record.frozen) {
      message.error('该 Schema 已冻结，如需删除请先在编辑器中取消冻结');
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
      <style dangerouslySetInnerHTML={{ __html: `
/* ==========================================================================
   Schema frozen — 冰蓝色冰冻动态特效（霜冻呼吸 + 冰裂纹闪烁 + 雪花飘落）
   ========================================================================== */
.schema-frozen-fx {
  position: absolute;
  inset: 0;
  border-radius: 12px;
  pointer-events: none;
  overflow: hidden;
  z-index: 2;
  isolation: isolate;
}

/* 霜冻叠加层：呼吸式淡入淡出，配合 blur 模拟冰雾 */
.schema-frozen-frost {
  position: absolute;
  inset: -4px;
  border-radius: 14px;
  background:
    radial-gradient(circle at 20% 20%, rgba(173, 216, 230, 0.35), transparent 55%),
    radial-gradient(circle at 80% 30%, rgba(135, 206, 250, 0.25), transparent 60%),
    radial-gradient(circle at 50% 90%, rgba(200, 235, 255, 0.35), transparent 55%),
    linear-gradient(135deg, rgba(173, 216, 230, 0.18), rgba(224, 247, 255, 0));
  backdrop-filter: blur(0.4px) saturate(0.95);
  -webkit-backdrop-filter: blur(0.4px) saturate(0.95);
  mix-blend-mode: screen;
  animation: frozenFrost 4.6s ease-in-out infinite;
}
@keyframes frozenFrost {
  0%, 100% { opacity: 0.78; filter: hue-rotate(0deg); }
  50%      { opacity: 1;    filter: hue-rotate(-6deg) brightness(1.05); }
}

/* 冰裂纹：三条 SVG 样式的分叉裂纹，分别在不同区域错落闪烁 */
.schema-frozen-crack {
  position: absolute;
  background:
    linear-gradient(115deg, transparent 47%, rgba(255,255,255,0.85) 49%, rgba(173,216,230,0.65) 50%, rgba(255,255,255,0.85) 51%, transparent 54%) 0 0/100% 100%,
    radial-gradient(1px 1px at 25% 35%, rgba(255,255,255,0.9), transparent 60%);
  opacity: 0;
  transform-origin: top left;
  mix-blend-mode: overlay;
  animation: frozenCrackBlink 5.2s ease-in-out infinite;
}
.schema-frozen-crack.s1 {
  top: 8%; left: 10%;
  width: 60%; height: 2px;
  transform: rotate(18deg);
  animation-delay: 0s;
}
.schema-frozen-crack.s2 {
  top: 42%; right: 6%;
  width: 45%; height: 2px;
  transform: rotate(-30deg) scaleY(0.9);
  animation-delay: 1.4s;
  opacity: 0;
}
.schema-frozen-crack.s3 {
  bottom: 12%; left: 18%;
  width: 55%; height: 2px;
  transform: rotate(42deg) scaleY(1.1);
  animation-delay: 2.8s;
}
@keyframes frozenCrackBlink {
  0%, 22%, 38%, 100% { opacity: 0; }
  26%, 34%           { opacity: 0.85; }
  28%                 { opacity: 1; }
}

/* 雪花：5 颗，不同大小/速度/位置，竖直飘落 + 左右摇摆 */
.snowflake {
  position: absolute;
  color: #fff;
  text-shadow:
    0 0 4px rgba(173, 216, 230, 0.95),
    0 0 10px rgba(135, 206, 250, 0.6);
  opacity: 0;
  animation-name: snowFall, snowSway;
  animation-timing-function: linear, ease-in-out;
  animation-iteration-count: infinite;
  pointer-events: none;
}
.snowflake.sf1 { left: 10%; top: -6px; font-size: 13px; animation-duration: 6.8s, 3s;   animation-delay: 0s, 0s;     color: #e0f4ff; }
.snowflake.sf2 { left: 30%; top: -6px; font-size: 9px;  animation-duration: 8.2s, 2.6s; animation-delay: 1.2s, 0.5s; color: #b9e3ff; }
.snowflake.sf3 { left: 54%; top: -6px; font-size: 14px; animation-duration: 7.4s, 3.2s; animation-delay: 2.1s, 1.1s; color: #ffffff; }
.snowflake.sf4 { left: 74%; top: -6px; font-size: 10px; animation-duration: 9.0s, 2.8s; animation-delay: 0.6s, 0.8s; color: #cceeff; }
.snowflake.sf5 { left: 88%; top: -6px; font-size: 12px; animation-duration: 6.2s, 3.4s; animation-delay: 3.0s, 1.6s; color: #a8dcff; }

@keyframes snowFall {
  0%   { transform: translateY(-6px)  rotate(0deg);   opacity: 0; }
  10%  { opacity: 1; }
  90%  { opacity: 0.9; }
  100% { transform: translateY(calc(100% + 20px)) rotate(360deg); opacity: 0; }
}
@keyframes snowSway {
  0%, 100% { margin-left: 0px; }
  50%      { margin-left: 6px; }
}

/* 冻结卡片外层：鼠标悬停时冰霜更强 + 轻微放大 */
.schema-card-wrap-frozen:hover .schema-frozen-frost {
  animation-duration: 2.6s;
  filter: brightness(1.08) contrast(1.04);
}
.schema-card-wrap-frozen:hover .schema-card {
  box-shadow:
    0 6px 24px -6px rgba(24, 144, 255, 0.35),
    0 0 0 1px rgba(173, 216, 230, 0.6) inset,
    0 0 20px -2px rgba(173, 216, 230, 0.35);
  transform: translateY(-1px) scale(1.008);
}
.schema-card-wrap-frozen:hover .frozen-title-icon {
  animation: frozenIconPulse 1.6s ease-in-out infinite;
}
@keyframes frozenIconPulse {
  0%, 100% { transform: scale(1) rotate(0deg);   filter: drop-shadow(0 0 0 rgba(135,206,250,0)); }
  50%      { transform: scale(1.15) rotate(12deg); filter: drop-shadow(0 0 6px rgba(135,206,250,0.9)); }
}
` }} />
      {/* 顶部操作栏 */}
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space>
            <FileTextOutlined style={{ fontSize: 20, color: '#6366f1' }} />
            <Text strong style={{ fontSize: 16 }}>Schema 管理</Text>
            <Tag style={{ borderRadius: 4, marginLeft: 8 }}>{total} 个</Tag>
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

      {/* 筛选 + 内容区 */}
      <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
        {/* 筛选区：领域 + 类型 + 冻结 + 应用 + 排序 + 顶部Mini分页（双向同步） */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <Text type="secondary" style={{ fontSize: 12, flexShrink: 0 }}>领域筛选：</Text>
            <Select
              value={domainFilter}
              onChange={setDomainFilter}
              style={{ width: 180 }}
              size="small"
              options={domainOptions}
            />
            <div style={{ width: 1, height: 20, background: '#f0f0f0', margin: '0 4px' }} />
            <Text type="secondary" style={{ fontSize: 12, flexShrink: 0 }}>类型筛选：</Text>
            <Select
              value={typeFilter}
              onChange={setTypeFilter}
              style={{ width: 160 }}
              size="small"
              options={typeOptions}
            />
            <div style={{ width: 1, height: 20, background: '#f0f0f0', margin: '0 4px' }} />
            <LockOutlined style={{ color: '#fa8c16', fontSize: 12 }} />
            <Select
              value={frozenFilter}
              onChange={(v) => setFrozenFilter(v as any)}
              style={{ width: 160 }}
              size="small"
              options={frozenOptions as any}
            />
            <div style={{ width: 1, height: 20, background: '#f0f0f0', margin: '0 4px' }} />
            <AppstoreOutlined style={{ color: '#1890ff', fontSize: 12 }} />
            <Select
              showSearch
              loading={appsLoading}
              value={appGroupFilter}
              onChange={setAppGroupFilter}
              style={{ width: 240 }}
              size="small"
              options={appOptions}
              filterOption={(input, option) =>
                (option?.label ?? '').toString().toLowerCase().includes(input.toLowerCase())
              }
              notFoundContent={
                appsLoading ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="加载中..." /> : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={<span>暂无应用，请先前往 <a href="#/app" target="_blank" rel="noreferrer">应用管理</a> 创建</span>}
                  />
                )
              }
              placeholder="选择应用（按 appGroup 过滤）"
            />
            <div style={{ width: 1, height: 20, background: '#f0f0f0', margin: '0 4px' }} />
            <ClockCircleOutlined style={{ color: '#6366f1', fontSize: 12 }} />
            <Select
              value={sortField}
              onChange={(v) => setSortField(v as any)}
              style={{ width: 150 }}
              size="small"
              options={[
                { label: '按更新时间', value: 'updatedAt' },
                { label: '按创建时间', value: 'createdAt' },
              ]}
            />
            <Tooltip title={sortDirection === 'desc' ? '降序（新→旧）' : '升序（旧→新）'}>
              <Button
                size="small"
                icon={sortDirection === 'desc' ? <ArrowDownOutlined /> : <ArrowUpOutlined />}
                onClick={() => setSortDirection((prev) => (prev === 'desc' ? 'asc' : 'desc'))}
              >
                {sortDirection === 'desc' ? '降序' : '升序'}
              </Button>
            </Tooltip>
          </div>
          {total > 0 && TopPagination}
        </div>

        {/* 卡片视图 */}
        {viewMode === 'card' && (
          <>
            {schemas.length === 0 && !loading ? (
              <Empty description="暂无 Schema" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: '40px 0' }} />
            ) : (
              <Row gutter={[16, 16]}>
                {schemas.map((record: SchemaDefinition) => {
                  const canDelete = access.canEditSchema && !record.frozen;
                  const scope = scopeConfig[record.scope || 'PLATFORM'];
                  const types = (record.schemaType || 'INPUT').split(',').map((t) => t.trim());
                  return (
                    <Col key={record.schemaName || record.id} xs={24} sm={12} lg={8} xl={6}>
                      <div
                        className={`schema-card-wrap ${record.frozen ? 'schema-card-wrap-frozen' : ''}`}
                        style={{ position: 'relative', height: '100%', borderRadius: 12 }}
                      >
                        {record.frozen && (
                          <div className="schema-frozen-fx" aria-hidden>
                            <div className="schema-frozen-frost" />
                            <div className="schema-frozen-crack s1" />
                            <div className="schema-frozen-crack s2" />
                            <div className="schema-frozen-crack s3" />
                            <span className="snowflake sf1">❄</span>
                            <span className="snowflake sf2">❅</span>
                            <span className="snowflake sf3">❆</span>
                            <span className="snowflake sf4">❄</span>
                            <span className="snowflake sf5">❅</span>
                          </div>
                        )}
                        <Card
                          hoverable
                          size="small"
                          className={`schema-card ${record.frozen ? 'schema-card-frozen' : ''}`}
                          onClick={() => history.push(`/schema/editor/${record.schemaName}?mode=view`)}
                          style={{
                            borderRadius: 12,
                            border: `1px solid ${record.frozen ? 'rgba(24,144,255,0.3)' : 'var(--fluxion-border, #e2e8f0)'}`,
                            display: 'flex',
                            flexDirection: 'column',
                            height: '100%',
                            background: record.frozen
                              ? 'linear-gradient(160deg, rgba(135,206,250,0.08) 0%, rgba(220,245,255,0.18) 40%, #ffffff 75%)'
                              : 'var(--fluxion-surface, #ffffff)',
                            transition: 'box-shadow 0.2s ease, transform 0.2s ease, border-color 0.2s ease',
                            position: 'relative',
                            zIndex: 1,
                            overflow: 'hidden',
                          }}
                          title={
                            <Tooltip title={record.schemaName} placement="topLeft">
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                                {record.frozen ? (
                                  <span className="frozen-title-icon" style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 18, height: 18, background: 'linear-gradient(135deg,#9ad9ff,#3fa9f5)', color: '#fff', borderRadius: 4, fontSize: 11 }}>
                                    ❄
                                  </span>
                                ) : (
                                  <FileTextOutlined style={{ color: '#6366f1', flexShrink: 0 }} />
                                )}
                                <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13, minWidth: 0, color: record.frozen ? '#0d5ea8' : undefined }}>
                                  {record.schemaName}
                                </span>
                              </div>
                            </Tooltip>
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
                          ...(canDelete
                            ? [
                                <Button
                                  key="delete"
                                  type="text"
                                  size="small"
                                  danger
                                  icon={<DeleteOutlined />}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleDelete(record);
                                  }}
                                >
                                  删除
                                </Button>,
                              ]
                            : []),
                        ]}
                        styles={{ body: { padding: '12px 14px 0', flex: 1, display: 'flex', flexDirection: 'column' } }}
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
                        {/* 标签组（作用域 / 冻结 / 领域 / 类型 / 格式 —— flex-wrap 永不重叠） */}
                        <div style={{ marginBottom: 10 }}>
                          <Space wrap size={[6, 6]}>
                            <Tag
                              color={scope.color}
                              style={{ borderRadius: 4, fontSize: 11, marginInlineEnd: 0 }}
                              icon={null}
                            >
                              {scope.label}
                            </Tag>
                            {record.frozen && (
                              <Tag
                                color="warning"
                                icon={<LockOutlined style={{ fontSize: 10 }} />}
                                style={{ borderRadius: 4, fontSize: 11, marginInlineEnd: 0 }}
                              >
                                已冻结
                              </Tag>
                            )}
                            {(() => {
                              const dm = getDomainMeta(record.domain || 'common');
                              return (
                                <Tag
                                  color={`${dm.color}15`}
                                  style={{
                                    color: dm.color,
                                    borderColor: `${dm.color}40`,
                                    borderRadius: 4,
                                    fontSize: 11,
                                    marginInlineEnd: 0,
                                  }}
                                >
                                  {dm.emoji} {dm.label}
                                </Tag>
                              );
                            })()}
                            {types.map((t) => (
                              <Tag
                                key={t}
                                color={`${TYPE_TAG_COLOR[t] || '#8c8c8c'}15`}
                                style={{
                                  color: TYPE_TAG_COLOR[t] || '#8c8c8c',
                                  borderColor: `${TYPE_TAG_COLOR[t] || '#8c8c8c'}40`,
                                  borderRadius: 4,
                                  fontSize: 11,
                                  marginInlineEnd: 0,
                                }}
                              >
                                {t}
                              </Tag>
                            ))}
                            <Tag color="blue" style={{ borderRadius: 4, fontSize: 11, marginInlineEnd: 0 }}>
                              {FORMAT_LABEL[record.schemaFormat] || record.schemaFormat}
                            </Tag>
                            {(record.refCount ?? 0) > 0 && (
                              <Tag color={record.refCount! > 5 ? 'blue' : 'default'} style={{ borderRadius: 4, fontSize: 11, marginInlineEnd: 0 }}>
                                引用 {record.refCount}
                              </Tag>
                            )}
                            {record.scope === 'PRIVATE' && record.appGroup && (
                              <Tag style={{ borderRadius: 4, fontSize: 11, marginInlineEnd: 0 }}>{record.appGroup}</Tag>
                            )}
                          </Space>
                        </div>
                        {/* 底部：更新时间 —— 贴底显示 */}
                        <div style={{ marginTop: 'auto', paddingTop: 6, borderTop: '1px dashed #f0f0f0' }}>
                          <Text type="secondary" style={{ fontSize: 11 }}>
                            <ClockCircleOutlined style={{ marginRight: 4 }} />
                            {record.updatedAt ? new Date(record.updatedAt).toLocaleDateString() : '-'}
                          </Text>
                        </div>
                      </Card>
                      </div>
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
                  const canDelete = access.canEditSchema && !record.frozen;
                  const scope = scopeConfig[record.scope || 'PLATFORM'];
                  const types = (record.schemaType || 'INPUT').split(',').map((t) => t.trim());
                  return (
                    <div
                      key={record.schemaName || record.id}
                      onClick={() => history.push(`/schema/editor/${record.schemaName}?mode=view`)}
                      className={record.frozen ? 'schema-table-row-frozen' : ''}
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
                          {(() => {
                            const dm = getDomainMeta(record.domain || 'common');
                            return (
                              <Tag
                                color={`${dm.color}15`}
                                style={{
                                  color: dm.color,
                                  borderColor: `${dm.color}40`,
                                  borderRadius: 4,
                                  fontSize: 11,
                                }}
                              >
                                {dm.emoji} {dm.label}
                              </Tag>
                            );
                          })()}
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
                          {canDelete && (
                            <Button
                              size="small"
                              danger
                              icon={<DeleteOutlined />}
                              onClick={(e) => {
                                e.stopPropagation();
                                handleDelete(record);
                              }}
                            />
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

        {/* 分页（大版，带 showSizeChanger / showTotal） */}
        {total > 0 && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
            {BottomPagination}
          </div>
        )}
      </Card>
    </div>
  );
};

export default SchemaList;
