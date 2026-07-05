import { useEffect, useState } from 'react';
import { Button, Input, Select, Space, Typography } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import type { WidgetProps } from '@rjsf/utils';
import { getTableColumns } from '@/services/schema';

interface ColumnInfo {
  name: string;
  dataType?: string;
}

/**
 * 基于表结构的 Key-Value 编辑器 Widget（用于 @rjsf）。
 * Key 使用当前表的列名下拉选择，Value 使用输入框，
 * 适用于结构化 DB CRUD 函数的 where / data 等对象字段。
 */
const DbColumnsKeyValue: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  formContext,
}) => {
  const tableName = (formContext as any)?.table as string | undefined;
  const dataSource = (formContext as any)?.dataSource as string | undefined;
  const [columns, setColumns] = useState<ColumnInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [pairs, setPairs] = useState<{ key: string; value: any }[]>([]);

  useEffect(() => {
    const obj = typeof value === 'object' && value !== null ? value : {};
    setPairs(
      Object.entries(obj).map(([key, val]) => ({
        key,
        value: val,
      }))
    );
  }, [value]);

  useEffect(() => {
    if (!tableName) {
      setColumns([]);
      return;
    }
    setLoading(true);
    getTableColumns(tableName, dataSource, { silent: true })
      .then((cols) => setColumns(cols.map((c) => ({ name: c.name, dataType: c.dataType }))))
      .catch((e) => console.error(`加载 ${tableName} 列信息失败:`, e))
      .finally(() => setLoading(false));
  }, [tableName, dataSource]);

  const syncValue = (nextPairs: { key: string; value: any }[]) => {
    const obj: Record<string, any> = {};
    nextPairs.forEach((p) => {
      if (p.key) obj[p.key] = p.value;
    });
    onChange(obj);
  };

  const updatePair = (index: number, patch: Partial<{ key: string; value: any }>) => {
    const next = pairs.map((p, i) => (i === index ? { ...p, ...patch } : p));
    setPairs(next);
    syncValue(next);
  };

  const addPair = () => {
    const next = [...pairs, { key: '', value: '' }];
    setPairs(next);
    syncValue(next);
  };

  const removePair = (index: number) => {
    const next = pairs.filter((_, i) => i !== index);
    setPairs(next);
    syncValue(next);
  };

  const columnOptions = columns.map((c) => ({
    value: c.name,
    label: c.dataType ? `${c.name} (${c.dataType})` : c.name,
  }));

  if (!tableName) {
    return (
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        请先选择表名
      </Typography.Text>
    );
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      {pairs.map((pair, index) => (
        <Space key={index} style={{ display: 'flex' }} align="baseline">
          <Select
            style={{ minWidth: 160 }}
            placeholder="列名"
            options={columnOptions}
            value={pair.key || undefined}
            onChange={(key) => updatePair(index, { key })}
            disabled={disabled || readonly}
            loading={loading}
            showSearch
          />
          <Input
            style={{ minWidth: 160 }}
            placeholder="值"
            value={pair.value}
            onChange={(e) => updatePair(index, { value: e.target.value })}
            disabled={disabled || readonly}
          />
          {!disabled && !readonly && (
            <MinusCircleOutlined
              onClick={() => removePair(index)}
              style={{ color: '#ff4d4f' }}
              onPointerEnterCapture={() => {}}
              onPointerLeaveCapture={() => {}}
            />
          )}
        </Space>
      ))}
      {!disabled && !readonly && (
        <Button
          type="dashed"
          onClick={addPair}
          block
          icon={<PlusOutlined onPointerEnterCapture={() => {}} onPointerLeaveCapture={() => {}} />}
        >
          添加字段
        </Button>
      )}
    </Space>
  );
};

export default DbColumnsKeyValue;
