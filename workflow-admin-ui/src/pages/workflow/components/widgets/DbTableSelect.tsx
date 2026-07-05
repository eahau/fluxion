import { useEffect, useState } from 'react';
import { Select, Spin } from 'antd';
import type { WidgetProps } from '@rjsf/utils';
import { getTables } from '@/services/schema';

/**
 * 数据库表名下拉选择 Widget（用于 @rjsf）。
 * 自动从 /api/admin/schemas/tables 加载当前数据库用户表列表。
 * 支持 formContext 传入 dataSource，用于分库场景。
 */
const DbTableSelect: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  formContext,
}) => {
  const dataSource = (formContext as any)?.dataSource as string | undefined;
  const [options, setOptions] = useState<{ value: string; label: string }[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getTables(dataSource, { silent: true })
      .then((tables) => setOptions(tables.map((t) => ({ value: t, label: t }))))
      .catch(() => setOptions([]))
      .finally(() => setLoading(false));
  }, [dataSource]);

  return (
    <Select
      showSearch
      allowClear
      placeholder="请选择数据表"
      value={value || undefined}
      onChange={(v) => onChange(v)}
      disabled={disabled || readonly}
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

export default DbTableSelect;
