import { useEffect, useState } from 'react';
import { Collapse, Empty, Input, Space, Spin, Tag, Tooltip, Typography, message } from 'antd';
import { SearchOutlined, DatabaseOutlined, LinkOutlined, CopyOutlined } from '@ant-design/icons';
import { getTablesWithColumns, getTableForeignKeys, type TableMetadata, type ForeignKeysResult } from '@/services/schema';

const { Text } = Typography;

interface SchemaSidebarProps {
  dataSource?: string;
}

const SchemaSidebar: React.FC<SchemaSidebarProps> = ({ dataSource }) => {
  const [tables, setTables] = useState<TableMetadata[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [foreignKeys, setForeignKeys] = useState<Record<string, ForeignKeysResult>>({});
  const [loadingFk, setLoadingFk] = useState<Record<string, boolean>>({});

  useEffect(() => {
    setLoading(true);
    getTablesWithColumns(dataSource, { silent: true })
      .then(setTables)
      .catch((e) => console.error('加载表结构失败:', e))
      .finally(() => setLoading(false));
  }, [dataSource]);

  const loadForeignKeys = (table: string) => {
    if (foreignKeys[table] || loadingFk[table]) return;
    setLoadingFk((prev) => ({ ...prev, [table]: true }));
    getTableForeignKeys(table, dataSource, { silent: true })
      .then((result) => setForeignKeys((prev) => ({ ...prev, [table]: result })))
      .catch((e) => console.error(`加载 ${table} 外键失败:`, e))
      .finally(() => setLoadingFk((prev) => ({ ...prev, [table]: false })));
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).then(() => message.success('已复制'));
  };

  const filtered = tables.filter((t) => t.name.toLowerCase().includes(keyword.toLowerCase()));

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ padding: 12, borderBottom: '1px solid #f0f0f0' }}>
        <Input
          size="small"
          placeholder="搜索表名"
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          allowClear
        />
      </div>
      <Spin spinning={loading} style={{ padding: 12, flex: 1, overflow: 'auto' }}>
        {filtered.length === 0 ? (
          <Empty description="暂无表" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ marginTop: 24 }} />
        ) : (
          <Collapse ghost expandIconPosition="end" style={{ padding: '0 8px' }}>
            {filtered.map((table) => (
              <Collapse.Panel
                key={table.name}
                header={
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <DatabaseOutlined style={{ color: '#1890ff' }} />
                    <Text strong style={{ fontSize: 13 }}>{table.name}</Text>
                    <Tag size="small" style={{ marginLeft: 'auto', marginRight: 8 }}>{table.columns.length} 列</Tag>
                  </div>
                }
                onClick={() => loadForeignKeys(table.name)}
              >
                <Space direction="vertical" style={{ width: '100%' }} size={4}>
                  {table.columns.map((col) => (
                    <div
                      key={col.name}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '4px 8px',
                        borderRadius: 4,
                        background: '#f6ffed',
                        fontSize: 12,
                      }}
                    >
                      <Space size={4}>
                        <Text style={{ fontSize: 12 }}>{col.name}</Text>
                        <Text type="secondary" style={{ fontSize: 11 }}>{col.dataType}</Text>
                        {!col.nullable && <Tag color="red" style={{ fontSize: 10, lineHeight: '14px', padding: '0 4px' }}>NOT NULL</Tag>}
                        {col.autoIncrement && <Tag color="#6366f1" style={{ fontSize: 10, lineHeight: '14px', padding: '0 4px' }}>AUTO</Tag>}
                      </Space>
                      <Tooltip title="复制列名">
                        <CopyOutlined
                          style={{ fontSize: 11, color: '#8c8c8c', cursor: 'pointer' }}
                          onClick={(e) => {
                            e.stopPropagation();
                            copyToClipboard(col.name);
                          }}
                        />
                      </Tooltip>
                    </div>
                  ))}
                </Space>

                {loadingFk[table.name] && (
                  <Spin size="small" style={{ display: 'block', marginTop: 8 }} />
                )}
                {foreignKeys[table.name] && (
                  <div style={{ marginTop: 12 }}>
                    {foreignKeys[table.name].incoming.length > 0 && (
                      <>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          <LinkOutlined style={{ marginRight: 4 }} />
                          被其他表引用
                        </Text>
                        <Space direction="vertical" style={{ width: '100%', marginTop: 4 }} size={4}>
                          {foreignKeys[table.name].incoming.map((fk, idx) => (
                            <Tag key={idx} color="#8b5cf6" style={{ fontSize: 11 }}>
                              {fk.sourceTable}.{fk.sourceColumn} → {fk.targetTable}.{fk.targetColumn}
                            </Tag>
                          ))}
                        </Space>
                      </>
                    )}
                    {foreignKeys[table.name].outgoing.length > 0 && (
                      <div style={{ marginTop: 8 }}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          <LinkOutlined style={{ marginRight: 4 }} />
                          引用其他表
                        </Text>
                        <Space direction="vertical" style={{ width: '100%', marginTop: 4 }} size={4}>
                          {foreignKeys[table.name].outgoing.map((fk, idx) => (
                            <Tag key={idx} color="#06b6d4" style={{ fontSize: 11 }}>
                              {fk.sourceTable}.{fk.sourceColumn} → {fk.targetTable}.{fk.targetColumn}
                            </Tag>
                          ))}
                        </Space>
                      </div>
                    )}
                  </div>
                )}
              </Collapse.Panel>
            ))}
          </Collapse>
        )}
      </Spin>
    </div>
  );
};

export default SchemaSidebar;
