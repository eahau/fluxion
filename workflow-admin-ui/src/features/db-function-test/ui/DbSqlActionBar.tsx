// features/db-function-test/ui/DbSqlActionBar.tsx
// DB Feature 专属 UI：「执行测试」按钮 + SQL 历史下拉 + 可选的表结构预览

import React from 'react';
import { Button } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import type { SqlHistoryEntry } from '@/shared/types/function-test-common';
import { SqlHistoryDropdown } from '@/shared';

export interface DbSqlActionBarProps {
  onRunTest: () => void;
  sqlHistory: SqlHistoryEntry[];
  onSelectSql: (sql: string) => void;
  runButtonSize?: 'small' | 'middle' | 'large';
  buttonLabel?: string;
  tableBrowser?: React.ReactNode;
}

export const DbSqlActionBar: React.FC<DbSqlActionBarProps> = ({
  onRunTest, sqlHistory, onSelectSql,
  runButtonSize = 'middle', buttonLabel = '执行测试', tableBrowser,
}) => (
  <>
    {tableBrowser}
    <div
      style={{
        width: '100%', marginTop: 2, display: 'flex',
        justifyContent: 'space-between', alignItems: 'center',
      }}
    >
      <Button type="primary" icon={<PlayCircleOutlined />} size={runButtonSize} onClick={onRunTest}>
        {buttonLabel}
      </Button>
      <SqlHistoryDropdown sqlHistory={sqlHistory} onSelectSql={onSelectSql} size={runButtonSize} />
    </div>
  </>
);

export default DbSqlActionBar;
