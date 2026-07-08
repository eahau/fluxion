import { useIntl, formatMessage, getIntl } from '@umijs/max';
import type { MessageDescriptor } from 'react-intl';

/**
 * i18n 辅助层 —— 统一封装 react-intl（Umi plugin-locale）的动态文案调用。
 *
 * 设计目标：为后续完整国际化方案提前做好架构准备。
 * - 组件内使用 [useT]（基于 useIntl，可响应语言切换）。
 * - 纯函数 / 非组件上下文（如 functionDisplay.ts 的工具函数）使用模块级 [t]
 *   （基于 getIntl 当前全局 intl，缺失 key 时回退显示 id，不影响运行）。
 *
 * 文案资源集中在 src/locales/{zh-CN,en-US}.ts，key 需保持一一对应。
 */

export interface TOptions {
  /** 插值变量 */
  values?: Record<string, string | number | boolean>;
  /** 默认回退文案（key 缺失时优先使用） */
  defaultMessage?: string;
}

/**
 * 组件内 Hook：返回绑定当前 locale 的翻译函数。
 */
export function useT() {
  const intl = useIntl();
  return (id: string, options?: TOptions): string =>
    intl.formatMessage({ id, defaultMessage: options?.defaultMessage } as MessageDescriptor, options?.values);
}

/**
 * 模块级翻译函数：供非组件上下文（纯函数、常量、工具方法）使用。
 * 内部走全局 intl 单例（getIntl），语言切换后自动跟随。
 */
export function t(id: string, options?: TOptions): string {
  return formatMessage(
    { id, defaultMessage: options?.defaultMessage } as MessageDescriptor,
    options?.values,
  );
}

export { getIntl, formatMessage };
