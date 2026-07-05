import { useEffect, useMemo, useRef, useState } from 'react';
import { message } from 'antd';
import Editor from '@monaco-editor/react';
import type { WidgetProps } from '@rjsf/utils';

const MIN_LINES = 3;
const MAX_LINES = 30;
const LINE_HEIGHT = 18;

/**
 * JSON 对象编辑器 Widget（用于 @rjsf）。
 * 基于 Monaco Editor，提供语法高亮与格式化，失焦时校验并写回表单。
 * 支持自适应高度与深色主题。
 */
const JsonCodeEditor: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  options,
}) => {
  const stringValue = useMemo(() => {
    if (value == null) return '{}';
    if (typeof value === 'string') return value;
    return JSON.stringify(value, null, 2);
  }, [value]);

  const [text, setText] = useState(stringValue);
  const [editorTheme, setEditorTheme] = useState<'vs' | 'vs-dark'>('vs');
  const textRef = useRef(text);
  textRef.current = text;

  useEffect(() => {
    setEditorTheme(localStorage.getItem('fluxion-dark-mode') === 'true' ? 'vs-dark' : 'vs');
  }, []);

  useEffect(() => {
    setText(stringValue);
  }, [stringValue]);

  const handleBlur = () => {
    try {
      const parsed = JSON.parse(textRef.current || '{}');
      onChange(parsed);
    } catch (e) {
      message.error('JSON 格式错误，请检查后再失焦');
    }
  };

  const heightOption = (options as any)?.height;
  const minHeightOption = (options as any)?.minHeight ?? 80;
  const maxHeightOption = (options as any)?.maxHeight ?? 540;
  const autoHeight = (options as any)?.autoHeight ?? false;

  const editorHeight = useMemo(() => {
    if (!autoHeight) return heightOption ?? 160;
    const lines = text.split('\n').length;
    const desired = Math.max(MIN_LINES, Math.min(lines, MAX_LINES)) * LINE_HEIGHT + 14;
    const min = typeof minHeightOption === 'number' ? minHeightOption : parseInt(minHeightOption, 10) || 80;
    const max = typeof maxHeightOption === 'number' ? maxHeightOption : parseInt(maxHeightOption, 10) || 540;
    return Math.min(Math.max(desired, min), max);
  }, [autoHeight, heightOption, minHeightOption, maxHeightOption, text]);

  return (
    <Editor
      height={editorHeight}
      theme={editorTheme}
      language="json"
      value={text}
      onChange={(v) => setText(v || '{}')}
      onMount={(editor) => {
        editor.onDidBlurEditorWidget(handleBlur);
      }}
      options={{
        minimap: { enabled: false },
        readOnly: disabled || readonly,
        automaticLayout: true,
        scrollBeyondLastLine: false,
      }}
    />
  );
};

export default JsonCodeEditor;
