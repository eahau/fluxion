// shared/ui/function-test/SqlHistoryDropdown.tsx
// 跨 features 通用：SQL 历史记录下拉（DB feature 用）

import React from 'react';
import { Button, Dropdown, Tag, Tooltip, Typography } from 'antd';
import { ClockCircleOutlined } from '@ant-design/icons';
import type { SqlHistoryEntry } from '../../types/function-test-common';
import { formatHistoryTime, highlightSqlKeywords, summarizeHistoryResult } from '../../lib/sql-format';

export interface SqlHistoryDropdownProps {
  sqlHistory: SqlHistoryEntry[];
  onSelectSql: (sql: string) => void;
  size?: 'small' | 'middle' | 'large';
}

export const SqlHistoryDropdown: React.FC<SqlHistoryDropdownProps> = ({
  sqlHistory, onSelectSql, size = 'middle',
}) => (
  <Dropdown
    trigger={['click']}
    disabled={sqlHistory.length === 0}
    placement="bottomRight"
    menu={{
      style: { maxWidth: 520, minWidth: 420 },
      items: sqlHistory.map((entry, index) => {
        const sql = entry?.sql || '';
        const preview = sql.length > 80 ? `${sql.slice(0, 80)}…` : sql;
        const summary = summarizeHistoryResult(entry as any);
        const isError = !!entry?.error;
        return {
          key: String(index),
          label: (
            <div style={{ padding: '6px 4px', lineHeight: 1.5 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <Tag color={isError ? 'error' : 'success'} style={{ margin: 0 }}>
                  {isError ? '失败' : '成功'}
                </Tag>
                <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                  {formatHistoryTime((entry as any)?.executedAt || 0)}
                </Typography.Text>
                {(entry as any)?.truncated && (
                  <Tag color="gold" style={{ marginInlineStart: 'auto' }}>已裁剪</Tag>
                )}
              </div>
              <div
                style={{
                  fontFamily: 'monospace', fontSize: 12, whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all', marginBottom: summary ? 4 : 0,
                }}
              >
                <span style={{ color: '#8c8c8c', marginRight: 6 }}>#{index + 1}</span>
                <Tooltip title={sql} placement="left">{highlightSqlKeywords(preview)}</Tooltip>
              </div>
              {summary && (
                <Typography.Text
                  type={isError ? 'danger' : 'secondary'}
                  style={{ fontSize: 11, display: 'block' }}
                >
                  {summary}
                </Typography.Text>
              )}
            </div>
          ),
          onClick: () => onSelectSql(sql),
        };
      }),
    }}
  >
    <Button icon={<ClockCircleOutlined />} size={size}>
      历史记录{sqlHistory.length > 0 ? `（${sqlHistory.length}）` : ''}
    </Button>
  </Dropdown>
);

export default SqlHistoryDropdown;
