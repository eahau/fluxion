import { useEffect, useState } from 'react';
import { Select, Alert, List, Tag, Spin, Empty } from 'antd';
import { getSchemaVersions } from '@/services/schema';
import type { SchemaDefinition } from '@/types/schema';
import { diffJson, type DiffItem } from '@/utils/schemaDiff';

interface SchemaDiffViewerProps {
  currentSchema: any;
  schemaName?: string;
}

function formatValue(value: any): string {
  if (value === undefined) return 'undefined';
  return JSON.stringify(value);
}

function renderDiffValue(diff: DiffItem): React.ReactNode {
  if (diff.type === 'added') {
    return <span style={{ color: '#52c41a' }}>{formatValue(diff.newValue)}</span>;
  }
  if (diff.type === 'removed') {
    return <span style={{ color: '#ff4d4f' }}>{formatValue(diff.oldValue)}</span>;
  }
  return (
    <span style={{ color: '#888' }}>
      <span style={{ color: '#ff4d4f', textDecoration: 'line-through' }}>
        {formatValue(diff.oldValue)}
      </span>
      {' → '}
      <span style={{ color: '#52c41a' }}>{formatValue(diff.newValue)}</span>
    </span>
  );
}

const SchemaDiffViewer: React.FC<SchemaDiffViewerProps> = ({ currentSchema, schemaName }) => {
  const [versions, setVersions] = useState<SchemaDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<string | undefined>();

  useEffect(() => {
    if (!schemaName) {
      setVersions([]);
      setSelectedVersion(undefined);
      return;
    }
    setLoading(true);
    getSchemaVersions(schemaName, { silent: true })
      .then((data) => {
        const list = data || [];
        setVersions(list);
        if (list.length > 0) {
          setSelectedVersion(String(list[0].id || list[0].version || 0));
        }
      })
      .catch((e) => console.error('加载 Schema 版本失败:', e))
      .finally(() => setLoading(false));
  }, [schemaName]);

  const selected = versions.find(
    (v) => String(v.id || v.version || 0) === selectedVersion,
  );
  const oldSchema = (() => {
    try {
      return selected ? JSON.parse((selected as any).schemaJson || '{}') : {};
    } catch (e) {
      return {};
    }
  })();
  const diffs = diffJson(oldSchema, currentSchema);

  const tagColor = {
    added: 'green',
    removed: 'red',
    modified: 'orange',
  };

  const tagText = {
    added: '新增',
    removed: '删除',
    modified: '修改',
  };

  return (
    <Spin spinning={loading}>
      {!schemaName ? (
        <Empty description="新建 Schema 暂无可对比的历史版本" />
      ) : (
        <>
          {versions.length === 0 ? (
            <Empty description="暂无历史版本" />
          ) : (
            <>
              <div style={{ marginBottom: 8, fontWeight: 500 }}>选择对比版本：</div>
              <Select
                style={{ width: '100%' }}
                placeholder="请选择历史版本"
                value={selectedVersion}
                onChange={setSelectedVersion}
                options={versions.map((v) => ({
                  value: String(v.id || v.version || 0),
                  label: `版本 ${v.version || '-'} · ${v.updatedAt || v.createdAt || ''}`,
                }))}
              />
            </>
          )}

          {selectedVersion && (
            <div style={{ marginTop: 16 }}>
              {diffs.length === 0 ? (
                <Alert type="info" message="当前内容与选中版本一致" showIcon />
              ) : (
                <>
                  <div style={{ marginBottom: 8, color: '#888' }}>
                    共 {diffs.length} 处差异
                  </div>
                  <List
                    size="small"
                    bordered
                    dataSource={diffs}
                    renderItem={(diff) => (
                      <List.Item>
                        <div style={{ wordBreak: 'break-all' }}>
                          <Tag color={tagColor[diff.type]}>{tagText[diff.type]}</Tag>
                          <code style={{ marginLeft: 8, marginRight: 8 }}>{diff.path}</code>
                          {renderDiffValue(diff)}
                        </div>
                      </List.Item>
                    )}
                  />
                </>
              )}
            </div>
          )}
        </>
      )}
    </Spin>
  );
};

export default SchemaDiffViewer;
