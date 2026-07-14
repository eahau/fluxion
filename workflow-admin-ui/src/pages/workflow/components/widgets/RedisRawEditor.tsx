import { useEffect, useMemo, useCallback, useState } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { StreamLanguage } from '@codemirror/language';
import type { StreamParser } from '@codemirror/legacy-modes/mode/stream-parser';
import { autocompletion, CompletionContext, CompletionResult } from '@codemirror/autocomplete';
import { EditorView } from '@codemirror/view';
import type { WidgetProps } from '@rjsf/utils';
import { NODE_PARAM_SCHEMAS } from '@/constants/nodeParamSchemas';

/**
 * 从本地 nodeParamSchemas.ts 提取 Redis 命令枚举，作为 raw 模式自动补全的数据源。
 */
export const REDIS_COMMANDS: string[] =
  (NODE_PARAM_SCHEMAS['builtin:redisCommand']?.properties?.command?.enum as string[]) || [];

interface RedisRawModeState {
  /** 当前行是否尚未识别到命令关键字 */
  first: boolean;
}

/**
 * 简易 Redis CLI 语法高亮：
 * - 每行第一个 token → keyword（命令）
 * - ${var} → variableName（变量插值）
 * - 其余 token → string（参数）
 */
const redisRawMode: StreamParser<RedisRawModeState> = {
  startState() {
    return { first: true };
  },
  token(stream, state) {
    if (stream.sol()) {
      state.first = true;
    }
    if (stream.eatSpace()) {
      return null;
    }

    // 变量插值：${...}
    if (stream.match(/^\$\{[^}]*\}/)) {
      return 'variableName';
    }

    // 第一个非空 token 作为命令关键字
    if (state.first) {
      state.first = false;
      stream.match(/^\S+/);
      return 'keyword';
    }

    // 其余作为参数
    stream.match(/^\S+/);
    return 'string';
  },
};

/**
 * Redis 命令自动补全：仅在每行第一个单词处提示命令列表。
 */
function redisCommandCompletions(context: CompletionContext): CompletionResult | null {
  const word = context.matchBefore(/^\S+/);
  if (!word || word.from === word.to) return null;

  const line = context.state.doc.lineAt(word.from);
  const before = context.state.sliceDoc(line.from, word.from);
  if (before.trim().length > 0) {
    return null;
  }

  const text = word.text.toUpperCase();
  const options = REDIS_COMMANDS.filter((cmd) => cmd.startsWith(text)).map((cmd) => ({
    label: cmd,
    type: 'keyword' as const,
  }));

  if (options.length === 0) return null;
  return {
    from: word.from,
    options,
    validFor: /^\S*$/,
  };
}

/**
 * Redis raw 命令行编辑器。
 *
 * 用于 @rjsf，在 FunctionTestPanel 的 raw 模式下替换默认 textarea，
 * 提供命令自动补全、关键字高亮、变量插值高亮。
 */
const MIN_LINES = 2;
const MAX_LINES = 20;
const LINE_HEIGHT = 18;

const RedisRawEditor: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  placeholder,
  schema,
  options,
}) => {
  const textValue = typeof value === 'string' ? value : '';
  const [darkMode, setDarkMode] = useState(false);

  useEffect(() => {
    setDarkMode(localStorage.getItem('fluxion-dark-mode') === 'true');
  }, []);

  const heightOption = (options as any)?.height;
  const minHeightOption = (options as any)?.minHeight ?? 60;
  const maxHeightOption = (options as any)?.maxHeight ?? 360;
  const autoHeight = (options as any)?.autoHeight ?? false;

  const editorHeight = useMemo(() => {
    if (!autoHeight) return heightOption ?? '120px';
    const lines = textValue.split('\n').length;
    const desired = Math.max(MIN_LINES, Math.min(lines, MAX_LINES)) * LINE_HEIGHT + 16;
    const min = typeof minHeightOption === 'number' ? minHeightOption : parseInt(minHeightOption, 10) || 60;
    const max = typeof maxHeightOption === 'number' ? maxHeightOption : parseInt(maxHeightOption, 10) || 360;
    return `${Math.min(Math.max(desired, min), max)}px`;
  }, [autoHeight, heightOption, minHeightOption, maxHeightOption, textValue]);

  const extensions = useMemo(
    () => [
      StreamLanguage.define(redisRawMode),
      autocompletion({ override: [redisCommandCompletions] }),
      EditorView.editable.of(!disabled && !readonly),
      EditorView.theme({
        '&': {
          fontSize: '13px',
          minHeight: '60px',
        },
        '.cm-content': {
          padding: '8px 0',
        },
        '.cm-gutters': {
          display: 'none',
        },
        '.cm-activeLine': {
          backgroundColor: 'transparent',
        },
      }),
      darkMode ? EditorView.theme({ '&': { backgroundColor: '#1e293b' } }) : [],
    ],
    [disabled, readonly, darkMode],
  );

  const handleChange = useCallback(
    (newValue: string) => {
      if (onChange) {
        onChange(newValue || undefined);
      }
    },
    [onChange],
  );

  return (
    <CodeMirror
      value={textValue}
      height={editorHeight}
      placeholder={placeholder || schema?.description || '输入 Redis CLI 命令，如 SET user:1 name John'}
      onChange={handleChange}
      extensions={extensions}
      basicSetup={{
        lineNumbers: false,
        foldGutter: false,
        highlightActiveLine: false,
        highlightActiveLineGutter: false,
      }}
    />
  );
};

export default RedisRawEditor;
