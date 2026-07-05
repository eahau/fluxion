import { useEffect, useCallback, useRef } from 'react';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { useReactFlow } from 'reactflow';

/**
 * 全局键盘快捷键 Hook
 * 参考 n8n、Figma 等产品的快捷键设计
 */
export function useKeyboardShortcuts() {
  const { undo, redo, copy, paste, duplicateSelected, selectAll, removeNode, nodes, selectedNodeId } =
    useWorkflowStore();
  const { fitView } = useReactFlow();

  // 防抖：防止 keydown 事件重复触发导致多次粘贴
  const lastActionRef = useRef<{ key: string; time: number }>({ key: '', time: 0 });

  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      // 忽略输入框中的按键
      const target = event.target as HTMLElement;
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable
      ) {
        return;
      }

      const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
      const ctrlKey = isMac ? event.metaKey : event.ctrlKey;

      // 防抖检查：同一快捷键 200ms 内不重复触发
      const now = Date.now();
      const actionKey = `${ctrlKey ? 'ctrl+' : ''}${event.shiftKey ? 'shift+' : ''}${event.key}`;
      if (
        actionKey === lastActionRef.current.key &&
        now - lastActionRef.current.time < 200
      ) {
        return;
      }

      const recordAction = () => {
        lastActionRef.current = { key: actionKey, time: now };
      };

      // Cmd/Ctrl + Z: 撤销
      if (ctrlKey && !event.shiftKey && event.key === 'z') {
        event.preventDefault();
        undo();
        recordAction();
        return;
      }

      // Cmd/Ctrl + Shift + Z 或 Cmd/Ctrl + Y: 重做
      if ((ctrlKey && event.shiftKey && event.key === 'z') || (ctrlKey && event.key === 'y')) {
        event.preventDefault();
        redo();
        recordAction();
        return;
      }

      // Cmd/Ctrl + C: 复制
      if (ctrlKey && event.key === 'c') {
        event.preventDefault();
        copy();
        recordAction();
        return;
      }

      // Cmd/Ctrl + V: 粘贴
      if (ctrlKey && event.key === 'v') {
        event.preventDefault();
        paste();
        recordAction();
        return;
      }

      // Cmd/Ctrl + D: 复制节点
      if (ctrlKey && event.key === 'd') {
        event.preventDefault();
        duplicateSelected();
        recordAction();
        return;
      }

      // Cmd/Ctrl + A: 全选
      if (ctrlKey && event.key === 'a') {
        event.preventDefault();
        selectAll();
        recordAction();
        return;
      }

      // Delete 或 Backspace: 删除选中节点
      if (event.key === 'Delete' || event.key === 'Backspace') {
        const selectedNodes = nodes.filter((n) => n.selected);
        if (selectedNodes.length > 0) {
          event.preventDefault();
          selectedNodes.forEach((node) => removeNode(node.id));
          recordAction();
        } else if (selectedNodeId) {
          event.preventDefault();
          removeNode(selectedNodeId);
          recordAction();
        }
        return;
      }

      // Cmd/Ctrl + 0: 适应视图
      if (ctrlKey && event.key === '0') {
        event.preventDefault();
        fitView({ padding: 0.2 });
        recordAction();
        return;
      }
    },
    [undo, redo, copy, paste, duplicateSelected, selectAll, removeNode, nodes, selectedNodeId, fitView],
  );

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);
}
