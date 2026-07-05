import { useCallback, useRef } from 'react';

/**
 * 点击防抖 Hook：首次点击立即执行，在指定冷却时间内忽略后续点击。
 * 适用于保存、提交、删除、测试、发布等可能触发网络请求的操作，防止用户快速连点造成重复提交。
 *
 * @param callback 原始点击回调
 * @param delay 冷却时间（毫秒），默认 500ms
 */
export function useClickDebounce<T extends (...args: any[]) => any>(
  callback: T,
  delay = 500,
): T {
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  return useCallback(
    (...args: Parameters<T>) => {
      if (timeoutRef.current) return;
      callbackRef.current(...args);
      timeoutRef.current = setTimeout(() => {
        timeoutRef.current = null;
      }, delay);
    },
    [delay],
  ) as T;
}
