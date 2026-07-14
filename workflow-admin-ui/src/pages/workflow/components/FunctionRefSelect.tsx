import { useEffect, useMemo, useState } from 'react';
import { Button, Input, Select, Space, Tag } from 'antd';
import { CheckOutlined, EditOutlined } from '@ant-design/icons';
import { getFunctions } from '@/services/function';
import type { FunctionDefinition } from '@/types/function';

interface FunctionRefSelectProps {
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

const CATEGORY_META: Record<string, { label: string; color: string }> = {
  BUILTIN: { label: '内置', color: 'blue' },
  CUSTOM: { label: '自定义', color: 'green' },
  SCRIPT: { label: '脚本', color: 'purple' },
  EXTERNAL: { label: '外部', color: 'orange' },
  SET_REF: { label: '集合', color: 'cyan' },
};

const FunctionRefSelect: React.FC<FunctionRefSelectProps> = ({
  value,
  onChange,
  placeholder,
  disabled,
}) => {
  const [manual, setManual] = useState(false);
  const [functions, setFunctions] = useState<FunctionDefinition[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getFunctions({ page: 0, pageSize: 1000 }, { silent: true })
      .then((res) => setFunctions(res.list || []))
      .catch(() => setFunctions([]))
      .finally(() => setLoading(false));
  }, []);

  const options = useMemo(() => {
    const list = functions.map((fn) => ({
      value: fn.name,
      label: (
        <Space size={8}>
          <Tag color={CATEGORY_META[fn.category]?.color || 'default'}>
            {CATEGORY_META[fn.category]?.label || fn.category}
          </Tag>
          <span>{fn.name}</span>
          <span style={{ color: '#888', fontSize: 12 }}>{fn.nodeType}</span>
          {fn.status !== 'ACTIVE' && <Tag>停用</Tag>}
        </Space>
      ),
    }));
    // 当前值若不在函数列表中，仍保留显示，避免切换回选择模式时空白
    if (value && !list.some((item) => item.value === value)) {
      list.unshift({ value, label: <span>{value}</span> });
    }
    return list;
  }, [functions, value]);

  return (
    <Space.Compact style={{ width: '100%' }}>
      {manual ? (
        <Input
          value={value}
          onChange={(e) => onChange?.(e.target.value)}
          placeholder={placeholder || '如 builtin:paramValidate 或 rpc:userService.exists'}
          disabled={disabled}
        />
      ) : (
        <Select
          showSearch
          allowClear
          loading={loading}
          value={value}
          onChange={(v) => onChange?.(v)}
          placeholder={placeholder || '搜索或选择函数'}
          options={options}
          filterOption={(input, option) =>
            (option?.value ?? '').toLowerCase().includes(input.toLowerCase())
          }
          style={{ width: '100%' }}
          disabled={disabled}
        />
      )}
      <Button
        icon={manual ? <CheckOutlined /> : <EditOutlined />}
        title={manual ? '完成手动输入' : '手动输入函数引用'}
        onClick={() => setManual(!manual)}
        disabled={disabled}
      />
    </Space.Compact>
  );
};

export default FunctionRefSelect;
