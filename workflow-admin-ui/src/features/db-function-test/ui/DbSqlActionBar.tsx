// features/db-function-test/ui/DbSqlActionBar.tsx
// DB Feature 专属 UI：SQL 编辑器左下方操作行（执行测试 + 历史记录）
// 注：「查看表结构」按钮由父组件(DbTestPanel)单独放到 SQL 编辑器右上角 Drawer，不再内嵌在此行

import React from 'react';
import { Button, Space } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import type { SqlHistoryEntry } from '@/shared/types/function-test-common';
import { SqlHistoryDropdown } from '@/shared';

export interface DbSqlActionBarProps {
  onRunTest: () => void;
  sqlHistory: SqlHistoryEntry[];
  onSelectSql: (sql: string) => void;
  runButtonSize?: 'small' | 'middle' | 'large';
  buttonLabel?: string;
  runButtonDisabled?: boolean;
  /** 额外插槽（仅兜底兼容，新代码建议把「查看表结构」独立为 SQL 框右上角触发器） */
  extra?: React.ReactNode;
}

export const DbSqlActionBar: React.FC<DbSqlActionBarProps> = ({
  onRunTest, sqlHistory, onSelectSql,
  runButtonSize = 'middle', buttonLabel = '执行测试', runButtonDisabled = false, extra,
}) => (
  <div
    style={{
      width: '100%',
      marginTop: 8,
      marginBottom: 4,
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      flexWrap: 'wrap',
      rowGap: 6,
    }}
  >
    {/* 左下：执行测试 + 历史记录（用户需求：固定在此位置） */}
    <Space size={8} wrap>
      <Button type="primary" icon={<PlayCircleOutlined />} size={runButtonSize} onClick={onRunTest} disabled={runButtonDisabled}>
        {buttonLabel}
      </Button>
      <SqlHistoryDropdown sqlHistory={sqlHistory} onSelectSql={onSelectSql} size={runButtonSize} disabled={runButtonDisabled} />
    </Space>
    {extra && <div>{extra}</div>}
  </div>
);

export default DbSqlActionBar;
