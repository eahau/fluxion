import { useMemo, useState, useCallback } from 'react';
import { Input, AutoComplete, Segmented, Space } from 'antd';
import { LinkOutlined, FontSizeOutlined } from '@ant-design/icons';
import type { WidgetProps } from '@rjsf/utils';

/**
 * 通用绑定输入 Widget（用于 @rjsf）。
 * 支持两种模式：
 * - 字面量模式：普通字符串输入
 * - 引用模式：$ref 绑定，从 formContext.inputSchemaFields 读取工作流入参字段提供补全建议
 *
 * Schema 驱动：通过 x-widget: "binding" 激活，前端零硬编码。
 */
const BindingInput: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  placeholder,
  schema,
  formContext,
}) => {
  const inputSchemaFields: string[] = (formContext as any)?.inputSchemaFields || [];
  const isBinding = value !== null && typeof value === 'object' && '$ref' in value;
  const [mode, setMode] = useState<'literal' | 'binding'>(isBinding ? 'binding' : 'literal');

  const refOptions = useMemo(
    () => inputSchemaFields.map((f) => ({ value: f, label: f })),
    [inputSchemaFields],
  );

  const handleModeChange = useCallback((newMode: string | number) => {
    setMode(newMode as 'literal' | 'binding');
    if (newMode === 'literal' && isBinding) {
      onChange(undefined);
    } else if (newMode === 'binding' && !isBinding) {
      onChange({ $ref: '' });
    }
  }, [isBinding, onChange]);

  const handleRefChange = useCallback((ref: string) => {
    onChange({ ...value, $ref: ref || '' });
  }, [value, onChange]);

  const handlePrefixChange = useCallback((prefix: string) => {
    const next = { ...value, $ref: value?.$ref || '' };
    if (prefix) {
      next.prefix = prefix;
    } else {
      delete next.prefix;
    }
    onChange(next);
  }, [value, onChange]);

  const handleLiteralChange = useCallback((v: string) => {
    onChange(v || undefined);
  }, [onChange]);

  if (mode === 'binding') {
    return (
      <Space direction="vertical" style={{ width: '100%' }} size={4}>
        <Segmented
          size="small"
          block
          value="binding"
          options={[
            { label: '值', value: 'literal', icon: <FontSizeOutlined /> },
            { label: '引用', value: 'binding', icon: <LinkOutlined /> },
          ]}
          onChange={handleModeChange}
        />
        <AutoComplete
          value={value?.$ref || ''}
          options={refOptions}
          onChange={handleRefChange}
          disabled={disabled || readonly}
          placeholder="选择或输入引用路径，如 userId"
          style={{ width: '100%' }}
          allowClear
          filterOption={(input, option) =>
            (option?.value as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
          }
        />
        <Input
          value={value?.prefix || ''}
          onChange={(e) => handlePrefixChange(e.target.value)}
          disabled={disabled || readonly}
          placeholder="前缀（可选），如 user:"
          addonBefore="prefix"
          size="small"
        />
      </Space>
    );
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={4}>
      <Segmented
        size="small"
        block
        value="literal"
        options={[
          { label: '值', value: 'literal', icon: <FontSizeOutlined /> },
          { label: '引用', value: 'binding', icon: <LinkOutlined /> },
        ]}
        onChange={handleModeChange}
      />
      <Input
        value={typeof value === 'string' ? value : ''}
        onChange={(e) => handleLiteralChange(e.target.value)}
        disabled={disabled || readonly}
        placeholder={placeholder || schema?.description || ''}
      />
    </Space>
  );
};

export default BindingInput;
