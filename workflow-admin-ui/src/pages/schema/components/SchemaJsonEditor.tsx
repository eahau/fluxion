import Editor from '@monaco-editor/react';

interface SchemaJsonEditorProps {
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
}

const SchemaJsonEditor: React.FC<SchemaJsonEditorProps> = ({ value, onChange, readOnly = false }) => {
  return (
    <div style={{ border: '1px solid #d9d9d9', borderRadius: 8, overflow: 'hidden' }}>
      <Editor
        height={500}
        language="json"
        value={value}
        onChange={(v) => onChange(v || '')}
        options={{
          minimap: { enabled: false },
          formatOnPaste: true,
          readOnly,
        }}
      />
    </div>
  );
};

export default SchemaJsonEditor;
