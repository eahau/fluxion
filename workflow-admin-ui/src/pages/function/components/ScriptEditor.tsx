import Editor, { type Monaco } from '@monaco-editor/react';

interface ScriptEditorProps {
  language: string;
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
  height?: number;
}

const SUGGESTIONS = [
  { label: 'ctx', detail: '上下文对象', insertText: 'ctx' },
  { label: 'input', detail: '当前节点输入', insertText: 'input' },
  { label: 'output', detail: '当前节点输出', insertText: 'output' },
  { label: 'prev.output', detail: '上一个节点输出', insertText: 'prev.output' },
  { label: 'T()', detail: 'SpEL 类型引用', insertText: 'T(java.lang.String)' },
  { label: '#root', detail: 'SpEL 根对象', insertText: '#root' },
  { label: '#this', detail: 'SpEL 当前对象', insertText: '#this' },
];

function configureMonaco(monaco: Monaco) {
  if (!monaco.languages.getLanguages().some((l) => l.id === 'spel')) {
    monaco.languages.register({ id: 'spel' });
    monaco.languages.setMonarchTokensProvider('spel', {
      tokenizer: {
        root: [
          [/#\w+/, 'variable'],
          [/T\([^)]+\)/, 'type.identifier'],
          [/\$\{[^}]*\}/, 'expression'],
          [/[a-zA-Z_]\w*/, 'identifier'],
          [/[0-9]+/, 'number'],
          [/[{}()\[\].,;:]/, 'delimiter'],
          [/[+\-*/%<>!=&|^~?]+/, 'operator'],
          [/".*?"|'.*?'/, 'string'],
          [/#.*$/, 'comment'],
        ],
      },
    });
  }

  monaco.languages.registerCompletionItemProvider('spel', {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };
      return {
        suggestions: SUGGESTIONS.map((s) => ({
          label: s.label,
          kind: monaco.languages.CompletionItemKind.Variable,
          detail: s.detail,
          insertText: s.insertText,
          range,
        })),
      };
    },
  });

  monaco.languages.registerCompletionItemProvider('groovy', {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };
      return {
        suggestions: SUGGESTIONS.filter((s) => !s.label.startsWith('#') && !s.label.startsWith('T(')).map(
          (s) => ({
            label: s.label,
            kind: monaco.languages.CompletionItemKind.Variable,
            detail: s.detail,
            insertText: s.insertText,
            range,
          }),
        ),
      };
    },
  });
}

const ScriptEditor: React.FC<ScriptEditorProps> = ({ language, value, onChange, readOnly, height = 300 }) => {
  const displayLang = language === 'spel' ? 'spel' : language;

  return (
    <div style={{ border: '1px solid #d9d9d9', borderRadius: 8, overflow: 'hidden' }}>
      <Editor
        height={height}
        language={displayLang}
        value={value}
        onChange={(v) => onChange(v || '')}
        options={{ minimap: { enabled: false }, readOnly }}
        beforeMount={configureMonaco}
      />
    </div>
  );
};

export default ScriptEditor;
