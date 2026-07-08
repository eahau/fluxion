import { useCallback, useRef } from 'react';

/**
 * Shared Hook：防止按钮短时间内重复点击触发多次请求
 * （从原来 utils/useClickDebounce.ts 迁入 shared/hooks）
 */
export function useClickDebounce<T extends (...args: any[]) => Promise<void> | void>(
  fn: T,
  delayMs: number = 300,
): (...args: Parameters<T>) => void {
  const lastClick = useRef<number>(0);
  return useCallback(
    (...args: Parameters<T>) => {
      const now = Date.now();
      if (now - lastClick.current < delayMs) return;
      lastClick.current = now;
      fn(...args);
    },
    [fn, delayMs],
  );
}
