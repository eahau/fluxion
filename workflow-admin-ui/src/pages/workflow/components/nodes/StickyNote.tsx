import { useState } from 'react';
import { Handle, Position } from 'reactflow';

export interface StickyNoteData {
  label: string;
  text?: string;
  color?: string;
  type: string;
}

export interface StickyNoteProps {
  data: StickyNoteData;
  selected?: boolean;
}

const COLORS = [
  { bg: '#fef9c3', border: '#fde047', name: '黄色' },
  { bg: '#dbeafe', border: '#93c5fd', name: '蓝色' },
  { bg: '#dcfce7', border: '#86efac', name: '绿色' },
  { bg: '#fee2e2', border: '#fca5a5', name: '红色' },
  { bg: '#f3e8ff', border: '#d8b4fe', name: '紫色' },
  { bg: '#ffedd5', border: '#fdba74', name: '橙色' },
];

const StickyNote: React.FC<StickyNoteProps> = ({ data, selected }) => {
  const [isEditing, setIsEditing] = useState(false);
  const [text, setText] = useState(data.text || '');
  const [showColors, setShowColors] = useState(false);

  const colorScheme = COLORS.find(c => c.bg === data.color) || COLORS[0];
  const bgColor = data.color || colorScheme.bg;

  const handleBlur = () => {
    setIsEditing(false);
    if (data.text !== text) {
      data.text = text;
    }
  };

  return (
    <div
      style={{
        padding: '14px 16px',
        borderRadius: 4,
        background: bgColor,
        border: selected ? `2px solid #3b82f6` : `1px solid ${colorScheme.border}40`,
        minWidth: 160,
        minHeight: 80,
        maxWidth: 280,
        boxShadow: selected
          ? '0 8px 24px rgba(0,0,0,0.12), 0 2px 6px rgba(0,0,0,0.08)'
          : '3px 3px 8px rgba(0,0,0,0.1), 1px 1px 3px rgba(0,0,0,0.06)',
        position: 'relative',
        transform: 'rotate(-1deg)',
        transition: 'all 0.2s ease',
        cursor: isEditing ? 'text' : 'grab',
      }}
      onDoubleClick={() => setIsEditing(true)}
    >
      {/* 纸张纹理效果 */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          borderRadius: 4,
          background: 'linear-gradient(135deg, transparent 40%, rgba(255,255,255,0.3) 100%)',
          pointerEvents: 'none',
        }}
      />

      <Handle type="target" position={Position.Top} style={{ opacity: 0 }} />
      <Handle type="source" position={Position.Bottom} style={{ opacity: 0 }} />

      {/* 颜色选择按钮 */}
      <div
        style={{
          position: 'absolute',
          top: 6,
          right: 6,
          width: 18,
          height: 18,
          borderRadius: '50%',
          background: 'rgba(0,0,0,0.1)',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 10,
          opacity: showColors ? 1 : 0.6,
          transition: 'opacity 0.2s',
        }}
        onClick={(e) => {
          e.stopPropagation();
          setShowColors(!showColors);
        }}
        onMouseEnter={(e) => (e.currentTarget.style.opacity = '1')}
        onMouseLeave={(e) => (e.currentTarget.style.opacity = showColors ? '1' : '0.6')}
      >
        🎨
      </div>

      {/* 颜色选择器 */}
      {showColors && (
        <div
          style={{
            position: 'absolute',
            top: 28,
            right: 6,
            display: 'flex',
            gap: 6,
            background: '#fff',
            padding: '8px 10px',
            borderRadius: 10,
            boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
            zIndex: 10,
          }}
          onClick={(e) => e.stopPropagation()}
        >
          {COLORS.map((c) => (
            <div
              key={c.bg}
              title={c.name}
              style={{
                width: 22,
                height: 22,
                borderRadius: 6,
                background: c.bg,
                cursor: 'pointer',
                border: data.color === c.bg ? `2px solid ${c.border}` : '1px solid rgba(0,0,0,0.1)',
                transition: 'transform 0.15s ease',
              }}
              onClick={() => {
                data.color = c.bg;
                setShowColors(false);
              }}
              onMouseEnter={(e) => (e.currentTarget.style.transform = 'scale(1.15)')}
              onMouseLeave={(e) => (e.currentTarget.style.transform = 'scale(1)')}
            />
          ))}
        </div>
      )}

      {/* 内容区域 */}
      {isEditing ? (
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onBlur={handleBlur}
          autoFocus
          placeholder="输入备注内容..."
          style={{
            width: '100%',
            minHeight: 60,
            border: 'none',
            background: 'transparent',
            resize: 'none',
            outline: 'none',
            fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, sans-serif',
            fontSize: 13,
            lineHeight: 1.5,
            color: '#1e293b',
          }}
        />
      ) : (
        <div
          style={{
            fontSize: 13,
            lineHeight: 1.5,
            color: '#1e293b',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            minHeight: 40,
          }}
        >
          {data.text || (
            <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>双击编辑备注...</span>
          )}
        </div>
      )}

      {/* 底部折叠效果 */}
      <div
        style={{
          position: 'absolute',
          bottom: 0,
          right: 0,
          width: 16,
          height: 16,
          background: `linear-gradient(135deg, ${bgColor} 50%, rgba(0,0,0,0.06) 50%)`,
          borderRadius: '0 0 4px 0',
        }}
      />
    </div>
  );
};

export default StickyNote;
