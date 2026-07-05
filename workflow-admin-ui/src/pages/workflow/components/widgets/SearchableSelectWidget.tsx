import { useMemo, useCallback, useState, useEffect } from 'react';
import { Select } from 'antd';
import type { WidgetProps } from '@rjsf/utils';

/**
 * 可搜索的枚举下拉选择 Widget（用于 @rjsf）。
 * 替代 @rjsf/antd 默认的 SelectWidget，增加 showSearch 支持。
 *
 * 搜索结果按相关性排序：完全匹配 > 前缀匹配 > 包含匹配。
 * 搜索/打开下拉时自动滚至顶部，确保高相关性结果可见。
 *
 * Schema 驱动：通过 x-widget: "searchableSelect" 激活，或在 uiSchema 中指定 ui:widget。
 * 适用于枚举选项较多的场景（如 Redis 178 个命令）。
 */
const SearchableSelectWidget: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  placeholder,
  options,
  id,
}) => {
  const enumOptions = (options?.enumOptions || []) as { value: string; label: string }[];
  const [searchText, setSearchText] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);

  // 根据搜索词动态排序：完全匹配 > 前缀匹配 > 包含匹配 > 不匹配(隐藏)
  const sortedOptions = useMemo(() => {
    if (!searchText) {
      return enumOptions.map((opt) => ({ value: opt.value, label: opt.label }));
    }
    const keyword = searchText.toLowerCase();
    const scored: { value: string; label: string; score: number }[] = [];
    for (const opt of enumOptions) {
      const label = opt.label.toLowerCase();
      if (label === keyword) {
        scored.push({ ...opt, score: 0 }); // 完全匹配
      } else if (label.startsWith(keyword)) {
        scored.push({ ...opt, score: 1 }); // 前缀匹配
      } else if (label.includes(keyword)) {
        scored.push({ ...opt, score: 2 }); // 包含匹配
      }
    }
    scored.sort((a, b) => a.score - b.score);
    return scored.map(({ value, label }) => ({ value, label }));
  }, [enumOptions, searchText]);

  /**
   * 重置下拉列表滚动位置到顶部。
   * 解决 antd Select 默认滚动到已选项导致高相关性结果不可见的问题。
   */
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

  // 搜索词变化时重置滚动（确保高相关性结果从顶部展示）
  useEffect(() => {
    if (searchText && dropdownOpen) {
      resetScroll();
    }
  }, [searchText, dropdownOpen, resetScroll]);

  // 下拉打开时重置滚动（防止自动跳到已选值位置）
  useEffect(() => {
    if (dropdownOpen) {
      const timer = setTimeout(resetScroll, 80);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [dropdownOpen, resetScroll]);

  const handleSearch = useCallback((val: string) => {
    setSearchText(val);
  }, []);

  return (
    <Select
      showSearch
      allowClear
      style={{ width: '100%' }}
      id={id}
      value={value}
      onChange={onChange}
      onSearch={handleSearch}
      onDropdownVisibleChange={setDropdownOpen}
      disabled={disabled || readonly}
      placeholder={placeholder || '请选择'}
      options={sortedOptions}
      optionFilterProp="label"
      autoClearSearchValue={false}
      notFoundContent="无匹配选项"
      // filterOption={false} 在 antd 5 下会禁用搜索交互（只能下拉、无法输入过滤），
      // 改为始终返回 true，由 sortedOptions 接管过滤与相关性排序。
      filterOption={() => true}
    />
  );
};

export default SearchableSelectWidget;
