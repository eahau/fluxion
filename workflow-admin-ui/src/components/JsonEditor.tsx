import { useEffect, useMemo, useRef, useState } from 'react';
import { message } from 'antd';
import Editor from '@monaco-editor/react';

interface JsonEditorProps {
  value?: any;
  onChange?: (value: any) => void;
  readOnly?: boolean;
  height?: number | string;
  /** 最小高度，配合 autoHeight 使用 */
  minHeight?: number | string;
  /** 是否根据内容行数自动调整高度 */
  autoHeight?: boolean;
  /** 最大高度限制（autoHeight=true 时生效） */
  maxHeight?: number | string;
}

const MIN_LINES = 3;
const MAX_LINES = 30;
const LINE_HEIGHT = 18;

const JsonEditor: React.FC<JsonEditorProps> = ({
  value,
  onChange,
  readOnly = false,
  height = 200,
  minHeight = 80,
  autoHeight = false,
  maxHeight = 540,
}) => {
  const stringValue = useMemo(() => {
    if (value == null) return '{}';
    if (typeof value === 'string') return value;
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return '{}';
    }
  }, [value]);

  const [text, setText] = useState(stringValue);
  const [editorTheme, setEditorTheme] = useState<'vs' | 'vs-dark'>('vs');

  useEffect(() => {
    setEditorTheme(localStorage.getItem('fluxion-dark-mode') === 'true' ? 'vs-dark' : 'vs');
  }, []);
  // 用 ref 追踪最新 text，避免 onMount 闭包捕获旧值
  const textRef = useRef(text);
  textRef.current = text;

  useEffect(() => {
    setText(stringValue);
  }, [stringValue]);

  const editorHeight = useMemo(() => {
    if (!autoHeight) return height;
    const lines = text.split('\n').length;
    const desired = Math.max(MIN_LINES, Math.min(lines, MAX_LINES)) * LINE_HEIGHT + 14;
    const min = typeof minHeight === 'number' ? minHeight : parseInt(minHeight as string, 10) || 80;
    const max = typeof maxHeight === 'number' ? maxHeight : parseInt(maxHeight as string, 10) || 540;
    return Math.min(Math.max(desired, min), max);
  }, [autoHeight, height, minHeight, maxHeight, text]);

  return (
    <Editor
      height={editorHeight}
      theme={editorTheme}
      language="json"
      value={text}
      onChange={(v) => setText(v || '{}')}
      onMount={(editor) => {
        if (!readOnly && onChange) {
          editor.onDidBlurEditorWidget(() => {
            try {
              const parsed = JSON.parse(textRef.current || '{}');
              onChange(parsed);
            } catch {
              message.error('JSON 格式错误，请检查后再失焦');
            }
          });
        }
      }}
      options={{
        minimap: { enabled: false },
        readOnly,
        automaticLayout: true,
        scrollBeyondLastLine: false,
      }}
    />
  );
};

export default JsonEditor;
