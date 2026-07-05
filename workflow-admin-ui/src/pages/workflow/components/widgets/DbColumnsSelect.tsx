import { useEffect, useState } from 'react';
import { Select, Spin } from 'antd';
import type { WidgetProps } from '@rjsf/utils';
import { getTableColumns } from '@/services/schema';

/**
 * 数据库列名多选 Widget（用于 @rjsf）。
 * 根据 formContext 中当前选中的 table 自动加载列列表。
 * 支持 formContext 传入 dataSource，用于分库场景。
 */
const DbColumnsSelect: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  formContext,
}) => {
  const tableName = (formContext as any)?.table;
  const dataSource = (formContext as any)?.dataSource as string | undefined;
  const [options, setOptions] = useState<{ value: string; label: string }[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!tableName) {
      setOptions([]);
      return;
    }
    setLoading(true);
    getTableColumns(tableName, dataSource, { silent: true })
      .then((cols) =>
        setOptions(
          cols.map((c) => ({
            value: c.name,
            label: `${c.name}${c.dataType ? ` (${c.dataType})` : ''}${c.comment ? ` - ${c.comment}` : ''}`,
          }))
        )
      )
      .catch(() => setOptions([]))
      .finally(() => setLoading(false));
  }, [tableName, dataSource]);

  return (
    <Select
      mode="multiple"
      showSearch
      allowClear
      placeholder={tableName ? '请选择列（为空表示查询全部）' : '请先选择数据表'}
      value={Array.isArray(value) ? value : []}
      onChange={(v) => onChange(v)}
      disabled={disabled || readonly || !tableName}
      loading={loading}
      style={{ width: '100%' }}
      options={options}
      filterOption={(input, option) =>
        (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
      }
      notFoundContent={loading ? <Spin size="small" /> : '暂无数据'}
    />
  );
};

export default DbColumnsSelect;
