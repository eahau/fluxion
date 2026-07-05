import { useMemo, useState, useCallback, useEffect } from 'react';
import { Select, Tooltip } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import type { WidgetProps } from '@rjsf/utils';
import {
  OPERATOR_LABELS,
  getOperatorsForFieldType,
  type FieldType,
} from '@/utils/conditionSchema';

/**
 * 条件操作符下拉 Widget（用于 @rjsf）。
 *
 * - 在操作符代码旁显示中文说明，例如 eq → 等于 (eq)
 * - 依据"当前规则项的 field 字段值对应的字段类型"智能过滤可用操作符列表：
 *   例如 items 是 array → 仅显示 isNull / isEmpty / size* / contains / in
 * - 若用户已选了一个对当前类型"非法"的操作符（历史遗留 / 类型切换），仍保留该选项以便修改。
 */
const OperatorSelectWidget: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  placeholder,
  options,
  id,
  formContext,
}) => {
  const enumOptions = (options?.enumOptions || []) as { value: string; label: string }[];
  const [searchText, setSearchText] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);

  // ─── 类型智能过滤：从 formContext 拿到 fieldTypeMap，并解析出当前规则的 field 文本 ──
  const { fieldType, fieldPath } = useMemo(() => {
    const ctx = formContext as any;
    const fieldTypeMap: Record<string, FieldType> | null = ctx?.upstreamFieldTypes || null;
    const rootFormData = ctx?.params || null;

    // 从 id 推出同级 field 路径
    // 例1：root_rules_0_operator         →  root_rules_0_field
    // 例2：root_conditions_0_rules_1_operator  →  root_conditions_0_rules_1_field
    const pathParts = id ? id.split('_') : [];
    if (pathParts[0] === 'root') pathParts.shift();
    if (pathParts.length === 0 || !rootFormData || !fieldTypeMap) {
      return { fieldType: null, fieldPath: '' };
    }
    pathParts[pathParts.length - 1] = 'field';

    // 遍历访问 formData
    let cursor: any = rootFormData;
    for (let i = 0; i < pathParts.length; i += 1) {
      if (cursor == null) break;
      const part = pathParts[i];
      if (Array.isArray(cursor)) {
        const idx = Number(part);
        cursor = Number.isFinite(idx) ? cursor[idx] : undefined;
      } else if (typeof cursor === 'object') {
        cursor = cursor[part];
      } else {
        cursor = undefined;
        break;
      }
    }
    const f = typeof cursor === 'string' ? cursor : '';
    return { fieldType: f ? (fieldTypeMap[f] ?? null) : null, fieldPath: f };
  }, [id, formContext]);

  const filteredEnumOptions = useMemo(() => {
    if (!fieldType) return enumOptions;
    const allowed = new Set(getOperatorsForFieldType(fieldType));
    const out = enumOptions.filter(
      (opt) => allowed.has(opt.value) || opt.value === value,
    );
    return out;
  }, [enumOptions, fieldType, value]);

  const selectOptions = useMemo(
    () =>
      filteredEnumOptions.map((opt) => {
        const code = opt.value;
        const desc = OPERATOR_LABELS[code];
        return {
          value: code,
          label: desc ? `${desc} (${code})` : code,
        };
      }),
    [filteredEnumOptions],
  );

  const sortedOptions = useMemo(() => {
    if (!searchText) return selectOptions;
    const keyword = searchText.toLowerCase();
    const scored: { value: string; label: string; score: number }[] = [];
    for (const opt of selectOptions) {
      const label = opt.label.toLowerCase();
      if (label === keyword) {
        scored.push({ ...opt, score: 0 });
      } else if (label.startsWith(keyword)) {
        scored.push({ ...opt, score: 1 });
      } else if (label.includes(keyword)) {
        scored.push({ ...opt, score: 2 });
      }
    }
    scored.sort((a, b) => a.score - b.score);
    return scored.map(({ value: v, label }) => ({ value: v, label }));
  }, [selectOptions, searchText]);

  const resetScroll = useCallback(() => {
    requestAnimationFrame(() => {
      const containers = document.querySelectorAll('.rc-virtual-list-holder');
      containers.forEach((el) => {
        if (el instanceof HTMLElement) {
          el.scrollTop = 0;
        }
      });
    });
  }, []);

  useEffect(() => {
    if (searchText && dropdownOpen) resetScroll();
  }, [searchText, dropdownOpen, resetScroll]);

  useEffect(() => {
    if (dropdownOpen) {
      const timer = setTimeout(resetScroll, 80);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [dropdownOpen, resetScroll]);

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
      <Select
        showSearch
        allowClear
        style={{ flex: 1, minWidth: 0 }}
        id={id}
        value={value}
        onChange={onChange}
        onSearch={setSearchText}
        onDropdownVisibleChange={setDropdownOpen}
        disabled={disabled || readonly}
        placeholder={placeholder || '请选择操作符'}
        options={sortedOptions}
        optionFilterProp="label"
        autoClearSearchValue={false}
        notFoundContent="无匹配选项（可能当前字段类型不支持该操作符）"
        filterOption={() => true}
      />
      {fieldType && (
        <Tooltip
          title={
            fieldPath
              ? `字段【${fieldPath}】推断类型：${fieldType}，操作符列表已按类型智能过滤`
              : `字段类型 ${fieldType}，操作符列表已按类型智能过滤`
          }
        >
          <InfoCircleOutlined
            style={{ color: '#1890ff', fontSize: 12, flex: 'none' }}
          />
        </Tooltip>
      )}
    </div>
  );
};

export default OperatorSelectWidget;
