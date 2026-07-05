import { useRequest, history } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Tag,
  Typography,
  message,
  Tabs,
  Badge,
  Tooltip,
  Table,
  Segmented,
} from 'antd';
import {
  ShopOutlined,
  DownloadOutlined,
  CloudUploadOutlined,
  SearchOutlined,
  AppstoreOutlined,
  UnorderedListOutlined,
  PartitionOutlined,
  FunctionOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useState, useMemo } from 'react';
import {
  getListings,
  searchMarketplace,
  installFromMarketplace,
  uninstallFromMarketplace,
  getInstalls,
  publishWorkflow as publishWfToMarketplace,
  publishFunction as publishFnToMarketplace,
} from '@/services/marketplace';
import { getWorkflows } from '@/services/workflow';
import { getFunctions } from '@/services/function';
import { useClickDebounce } from '@/utils/useClickDebounce';
import type { MarketplaceListing, MarketplaceInstallRecord } from '@/types/marketplace';

const { Text, Title, Paragraph } = Typography;

const typeConfig: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  WORKFLOW: { label: '工作流', color: '#6366f1', icon: <PartitionOutlined /> },
  FUNCTION: { label: '函数', color: '#fa8c16', icon: <FunctionOutlined /> },
};

const MarketplacePage: React.FC = () => {
  const [tab, setTab] = useState<string>('browse');
  const [keyword, setKeyword] = useState('');
  const [viewMode, setViewMode] = useState<string>('card');
  const [installVisible, setInstallVisible] = useState(false);
  const [installTarget, setInstallTarget] = useState<MarketplaceListing | null>(null);
  const [installAppGroup, setInstallAppGroup] = useState('');
  const [installing, setInstalling] = useState(false);
  const [publishVisible, setPublishVisible] = useState(false);
  const [publishType, setPublishType] = useState<'WORKFLOW' | 'FUNCTION'>('WORKFLOW');
  const [publishForm] = Form.useForm();

  // 市场列表
  const { data: allListings = [], loading: listingsLoading, refresh: refreshListings } = useRequest(
    getListings,
    { formatResult: (res) => res || [] },
  );

  // 搜索
  const { data: searchResults, loading: searchLoading, run: runSearch } = useRequest(
    searchMarketplace,
    { manual: true, formatResult: (res) => res || [] },
  );

  // 安装记录
  const { data: installsMap = {}, refresh: refreshInstalls } = useRequest(
    async () => {
      // 获取所有安装记录（按已安装的 listing 聚合）
      const map: Record<string, MarketplaceInstallRecord[]> = {};
      // 由于需要 appGroup 才能查安装记录，这里从 listing 侧获取
      return map;
    },
    { formatResult: (res) => res || {} },
  );

  // 工作流列表（用于发布选择）
  const { data: wfData } = useRequest(
    () => getWorkflows({ keyword: '' }),
    { formatResult: (res) => res?.list || [] },
  );

  // 函数列表（用于发布选择）
  const { data: fnData } = useRequest(
    () => getFunctions({ keyword: '' }),
    { formatResult: (res) => res?.list || [] },
  );

  const listings = useMemo(() => {
    if (keyword && searchResults) return searchResults;
    return allListings;
  }, [keyword, searchResults, allListings]);

  const handleSearch = useClickDebounce(() => {
    if (!keyword.trim()) {
      refreshListings();
      return;
    }
    runSearch(keyword);
  });

  const handleInstall = (listing: MarketplaceListing) => {
    setInstallTarget(listing);
    setInstallAppGroup('');
    setInstallVisible(true);
  };

  const confirmInstall = useClickDebounce(async () => {
    if (!installTarget || !installAppGroup) return;
    setInstalling(true);
    try {
      await installFromMarketplace({ listingId: installTarget.listingId, appGroup: installAppGroup });
      message.success(`已将 "${installTarget.title}" 安装到 ${installAppGroup}`);
      setInstallVisible(false);
      refreshListings();
      refreshInstalls();
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.response?.data?.error || e?.message || '安装失败';
      message.error(msg);
    } finally {
      setInstalling(false);
    }
  });

  const handleUninstall = useClickDebounce((listing: MarketplaceListing) => {
    Modal.confirm({
      title: '确认卸载',
      content: `确定卸载 "${listing.title}" 吗？卸载后将删除对应的私有副本。`,
      onOk: async () => {
        try {
          // 需要知道安装时的 appGroup，这里使用第一个已安装的
          // 实际应该通过安装记录获取
          await uninstallFromMarketplace(listing.listingId, installAppGroup || 'default');
          message.success('卸载成功');
          refreshListings();
          refreshInstalls();
        } catch (e: any) {
          const msg = e?.response?.data?.message || e?.message || '卸载失败';
          message.error(msg);
        }
      },
    });
  });

  const handlePublish = () => {
    publishForm.resetFields();
    setPublishVisible(true);
  };

  const confirmPublish = useClickDebounce(async () => {
    try {
      const values = await publishForm.validateFields();
      const publishData = {
        listingId: values.listingId,
        sourceId: values.sourceId,
        title: values.title,
        description: values.description,
        tags: values.tags,
      };
      if (publishType === 'WORKFLOW') {
        await publishWfToMarketplace(publishData);
      } else {
        await publishFnToMarketplace(publishData);
      }
      message.success('发布到市场成功');
      setPublishVisible(false);
      refreshListings();
    } catch (e: any) {
      if (e?.errorFields) return;
      const msg = e?.response?.data?.message || e?.message || '发布失败';
      message.error(msg);
    }
  });

  const parseTags = (tags?: string): string[] => {
    if (!tags) return [];
    try {
      const parsed = JSON.parse(tags);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return tags.split(',').map((t) => t.trim()).filter(Boolean);
    }
  };

  return (
    <div>
      {/* 顶部 */}
      <Card style={{ borderRadius: 12, border: 'none', marginBottom: 16 }} className="home-stat-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space>
            <ShopOutlined style={{ fontSize: 20, color: '#fa8c16' }} />
            <Text strong style={{ fontSize: 16 }}>工作流市场</Text>
            <Tag style={{ borderRadius: 4, marginLeft: 8 }}>{allListings.length} 个可用</Tag>
          </Space>
          <Space>
            <Input.Search
              placeholder="搜索市场..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onSearch={handleSearch}
              style={{ width: 280 }}
              enterButton={<SearchOutlined />}
            />
            <Button icon={<CloudUploadOutlined />} onClick={handlePublish}>发布到市场</Button>
          </Space>
        </div>
      </Card>

      <Card style={{ borderRadius: 12, border: 'none' }} className="home-stat-card">
        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'browse',
              label: (
                <span>
                  <ShopOutlined /> 浏览市场
                  <Badge
                    count={listings.length}
                    style={{ backgroundColor: '#f0f0f0', color: '#595959', fontSize: 11, boxShadow: 'none', marginLeft: 6 }}
                  />
                </span>
              ),
            },
            {
              key: 'installed',
              label: <span><CheckCircleOutlined /> 已安装</span>,
            },
          ]}
        />

        {tab === 'browse' && (
          <>
            <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'flex-end' }}>
              <Segmented
                value={viewMode}
                onChange={(v) => setViewMode(v as string)}
                options={[
                  { value: 'card', icon: <AppstoreOutlined /> },
                  { value: 'table', icon: <UnorderedListOutlined /> },
                ]}
                size="small"
              />
            </div>

            {listings.length === 0 && !listingsLoading && !searchLoading ? (
              <Empty
                description={keyword ? `未找到与 "${keyword}" 相关的结果` : '市场暂无内容，快来发布第一个吧'}
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: '60px 0' }}
              />
            ) : viewMode === 'card' ? (
              <Row gutter={[16, 16]}>
                {listings.map((listing) => {
                  const typeInfo = typeConfig[listing.sourceType] || typeConfig.WORKFLOW;
                  const tags = parseTags(listing.tags);
                  return (
                    <Col key={listing.listingId} xs={24} sm={12} lg={8} xl={6}>
                      <Card
                        hoverable
                        size="small"
                        style={{ borderRadius: 12, border: '1px solid #f0f0f0', height: '100%' }}
                        title={
                          <Tooltip title={listing.title} placement="topLeft">
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                              <span style={{ color: typeInfo.color, flexShrink: 0 }}>{typeInfo.icon}</span>
                              <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>
                                {listing.title}
                              </span>
                            </div>
                          </Tooltip>
                        }
                        extra={
                          <Tag color={typeInfo.color} style={{ borderRadius: 4, fontSize: 11, margin: 0 }}>
                            {typeInfo.label}
                          </Tag>
                        }
                        actions={[
                          <Button
                            key="install"
                            type="text"
                            size="small"
                            icon={<DownloadOutlined />}
                            onClick={(e) => { e.stopPropagation(); handleInstall(listing); }}
                          >
                            安装
                          </Button>,
                          <span key="count" style={{ fontSize: 12, color: '#8c8c8c' }}>
                            <DownloadOutlined style={{ marginRight: 4 }} />
                            {listing.installCount} 次安装
                          </span>,
                        ]}
                      >
                        <Paragraph
                          type="secondary"
                          style={{ fontSize: 12, marginBottom: 10, minHeight: 36 }}
                          ellipsis={{ rows: 2 }}
                        >
                          {listing.description || '暂无描述'}
                        </Paragraph>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          <Space wrap size={4}>
                            {tags.slice(0, 3).map((tag, i) => (
                              <Tag key={i} style={{ borderRadius: 4, fontSize: 11 }}>{tag}</Tag>
                            ))}
                          </Space>
                          {listing.author && (
                            <Text type="secondary" style={{ fontSize: 11 }}>by {listing.author}</Text>
                          )}
                        </div>
                      </Card>
                    </Col>
                  );
                })}
              </Row>
            ) : (
              <Table
                rowKey="listingId"
                size="small"
                loading={listingsLoading || searchLoading}
                dataSource={listings}
                pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }}
                columns={[
                  {
                    title: '名称',
                    dataIndex: 'title',
                    render: (title: string, record: MarketplaceListing) => {
                      const typeInfo = typeConfig[record.sourceType] || typeConfig.WORKFLOW;
                      return (
                        <Space>
                          <span style={{ color: typeInfo.color }}>{typeInfo.icon}</span>
                          <Text strong style={{ fontSize: 13 }}>{title}</Text>
                        </Space>
                      );
                    },
                  },
                  {
                    title: '类型',
                    dataIndex: 'sourceType',
                    width: 80,
                    render: (t: string) => {
                      const info = typeConfig[t] || typeConfig.WORKFLOW;
                      return <Tag color={info.color} style={{ borderRadius: 4 }}>{info.label}</Tag>;
                    },
                  },
                  {
                    title: '描述',
                    dataIndex: 'description',
                    ellipsis: true,
                    render: (desc: string) => <Text type="secondary" style={{ fontSize: 12 }}>{desc || '-'}</Text>,
                  },
                  {
                    title: '安装次数',
                    dataIndex: 'installCount',
                    width: 90,
                    align: 'center',
                  },
                  {
                    title: '作者',
                    dataIndex: 'author',
                    width: 100,
                  },
                  {
                    title: '操作',
                    width: 100,
                    render: (_: any, record: MarketplaceListing) => (
                      <Button
                        size="small"
                        type="primary"
                        ghost
                        icon={<DownloadOutlined />}
                        onClick={() => handleInstall(record)}
                      >
                        安装
                      </Button>
                    ),
                  },
                ]}
              />
            )}
          </>
        )}

        {tab === 'installed' && (
          <InstalledList
            listings={allListings}
            onUninstall={handleUninstall}
          />
        )}
      </Card>

      {/* 安装弹窗 */}
      <Modal
        title={`安装：${installTarget?.title}`}
        open={installVisible}
        onCancel={() => setInstallVisible(false)}
        onOk={confirmInstall}
        confirmLoading={installing}
        okText="安装"
        destroyOnClose
      >
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary">
            安装后将在目标应用分组下生成一个 scope=PRIVATE 的副本，source_ref 指向市场原始资源。
          </Text>
        </div>
        <Form layout="vertical">
          <Form.Item
            label="目标应用分组（app_group）"
            required
            help="您必须是该应用分组的成员才能安装"
          >
            <Input
              placeholder="请输入目标 app_group"
              value={installAppGroup}
              onChange={(e) => setInstallAppGroup(e.target.value)}
            />
          </Form.Item>
        </Form>
        {installTarget && (
          <div style={{ background: '#f9f9f9', borderRadius: 8, padding: 12 }}>
            <Space direction="vertical" size={4}>
              <Text strong>{installTarget.title}</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                类型：{typeConfig[installTarget.sourceType]?.label} | 安装次数：{installTarget.installCount}
              </Text>
              {installTarget.description && (
                <Text type="secondary" style={{ fontSize: 12 }}>{installTarget.description}</Text>
              )}
            </Space>
          </div>
        )}
      </Modal>

      {/* 发布弹窗 */}
      <Modal
        title="发布到市场"
        open={publishVisible}
        onCancel={() => setPublishVisible(false)}
        onOk={confirmPublish}
        destroyOnClose
        width={560}
      >
        <Form form={publishForm} layout="vertical">
          <Form.Item label="发布类型">
            <Segmented
              value={publishType}
              onChange={(v) => {
                setPublishType(v as 'WORKFLOW' | 'FUNCTION');
                publishForm.setFieldValue('sourceId', undefined);
              }}
              options={[
                { value: 'WORKFLOW', label: '工作流', icon: <PartitionOutlined /> },
                { value: 'FUNCTION', label: '函数', icon: <FunctionOutlined /> },
              ]}
            />
          </Form.Item>
          <Form.Item name="listingId" label="Listing ID" rules={[{ required: true, message: '请输入唯一标识 ID' }]}>
            <Input placeholder="如：awesome-workflow" />
          </Form.Item>
          <Form.Item name="sourceId" label={`选择${publishType === 'WORKFLOW' ? '工作流' : '函数'}`} rules={[{ required: true, message: '请选择要发布的资源' }]}>
            <Select
              showSearch
              placeholder={`选择要发布的${publishType === 'WORKFLOW' ? '工作流' : '函数'}`}
              optionFilterProp="label"
              options={
                publishType === 'WORKFLOW'
                  ? (wfData || []).map((w: any) => ({ label: `${w.name} (${w.id})`, value: w.id }))
                  : (fnData || []).map((f: any) => ({ label: `${f.functionName || f.name}`, value: f.functionName || f.name }))
              }
            />
          </Form.Item>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入市场展示标题' }]}>
            <Input placeholder="市场展示名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="简要描述该工作流/函数的功能和用途" />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="输入后回车添加标签" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

/** 已安装列表子组件 */
const InstalledList: React.FC<{
  listings: MarketplaceListing[];
  onUninstall: (listing: MarketplaceListing) => void;
}> = ({ listings, onUninstall }) => {
  const [appGroup, setAppGroup] = useState('');
  const { data: installs = [], loading, refresh } = useRequest(
    () => (appGroup ? getInstalls(appGroup) : Promise.resolve([])),
    { refreshDeps: [appGroup], formatResult: (res) => res || [] },
  );

  const handleRefresh = useClickDebounce(() => refresh());

  const installedListingIds = useMemo(
    () => new Set(installs.map((i: MarketplaceInstallRecord) => i.listingId)),
    [installs],
  );

  const installedListings = useMemo(
    () => listings.filter((l) => installedListingIds.has(l.listingId)),
    [listings, installedListingIds],
  );

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Text>应用分组：</Text>
        <Input
          placeholder="输入 app_group 查看安装记录"
          value={appGroup}
          onChange={(e) => setAppGroup(e.target.value)}
          style={{ width: 240 }}
        />
        {appGroup && (
          <Button size="small" onClick={handleRefresh}>刷新</Button>
        )}
      </div>

      {!appGroup ? (
        <Empty description="请输入应用分组查看已安装内容" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: '40px 0' }} />
      ) : loading ? (
        <Empty description="加载中..." image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : installs.length === 0 ? (
        <Empty description={`应用分组 "${appGroup}" 暂无安装记录`} image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: '40px 0' }} />
      ) : (
        <Table
          rowKey="listingId"
          size="small"
          dataSource={installs}
          pagination={false}
          columns={[
            {
              title: 'Listing ID',
              dataIndex: 'listingId',
              render: (id: string) => {
                const listing = listings.find((l) => l.listingId === id);
                return listing ? (
                  <Space>
                    <span style={{ color: typeConfig[listing.sourceType]?.color }}>
                      {typeConfig[listing.sourceType]?.icon}
                    </span>
                    <Text strong>{listing.title}</Text>
                  </Space>
                ) : (
                  <Text code>{id}</Text>
                );
              },
            },
            {
              title: '安装者',
              dataIndex: 'installedBy',
              width: 120,
            },
            {
              title: '安装时间',
              dataIndex: 'createdAt',
              width: 180,
            },
            {
              title: '操作',
              width: 100,
              render: (_: any, record: MarketplaceInstallRecord) => {
                const listing = listings.find((l) => l.listingId === record.listingId);
                return listing ? (
                  <Button size="small" danger icon={<DeleteOutlined />} onClick={() => onUninstall(listing)}>
                    卸载
                  </Button>
                ) : null;
              },
            },
          ]}
        />
      )}
    </div>
  );
};

export default MarketplacePage;
