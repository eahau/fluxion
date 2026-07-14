import { Handle, Position, type NodeProps } from 'reactflow';
import { AntDesignOutlined } from '@ant-design/icons';

const TRIGGER_COLOR = '#6366f1';

interface TriggerNodeData {
  label: string;
  functionRef: string;
  params?: {
    method?: string;
    path?: string;
    topic?: string;
  };
  type: string;
}

const TriggerNode: React.FC<NodeProps<TriggerNodeData>> = ({ data, selected }) => {
  const cardStyle: React.CSSProperties = {
    minWidth: 200,
    maxWidth: 260,
    borderRadius: 12,
    background: selected ? `${TRIGGER_COLOR}15` : '#f0f5ff',
    border: selected ? `2px solid ${TRIGGER_COLOR}` : `2px solid ${TRIGGER_COLOR}40`,
    boxShadow: selected
      ? '0 4px 16px -2px rgba(99, 102, 241, 0.25), 0 2px 4px rgba(0,0,0,0.06)'
      : '0 2px 8px rgba(99, 102, 241, 0.15)',
    transform: selected ? 'scale(1.02)' : undefined,
    position: 'relative',
    transition: 'border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease, background 0.2s ease',
    overflow: 'hidden',
    cursor: 'pointer',
    zIndex: selected ? 5 : undefined,
  };

  return (
    <div style={cardStyle}>
      <div
        style={{
          height: 3,
          background: `linear-gradient(90deg, ${TRIGGER_COLOR}, #a5b4fc)`,
        }}
      />

      <div style={{ padding: '14px 16px' }}>
        <div
          style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}
          onMouseDown={(e) => e.stopPropagation()}
          onPointerDown={(e) => e.stopPropagation()}
        >
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              background: `${TRIGGER_COLOR}20`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 18,
              flexShrink: 0,
            }}
          >
            <AntDesignOutlined style={{ color: TRIGGER_COLOR }} />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                fontWeight: 700,
                fontSize: 14,
                color: '#1e293b',
                lineHeight: 1.3,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
              title={data.label}
            >
              {data.label}
            </div>
            <div
              style={{
                fontSize: 11,
                color: '#64748b',
                lineHeight: 1.3,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
              title={data.functionRef}
            >
              {data.functionRef}
            </div>
          </div>
        </div>

        {(data.params?.method || data.params?.path || data.params?.topic) && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {data.params?.method && (
              <span
                style={{
                  padding: '2px 8px',
                  borderRadius: 4,
                  background: `${TRIGGER_COLOR}15`,
                  fontSize: 11,
                  fontWeight: 600,
                  color: TRIGGER_COLOR,
                  textTransform: 'uppercase',
                }}
              >
                {data.params.method}
              </span>
            )}
            {data.params?.path && (
              <span
                style={{
                  padding: '2px 8px',
                  borderRadius: 4,
                  background: '#0891b215',
                  fontSize: 11,
                  color: '#0891b2',
                  fontFamily: '"SF Mono", Monaco, monospace',
                  maxWidth: 160,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={data.params.path}
              >
                {data.params.path}
              </span>
            )}
            {data.params?.topic && !data.params?.path && (
              <span
                style={{
                  padding: '2px 8px',
                  borderRadius: 4,
                  background: '#f59e0b15',
                  fontSize: 11,
                  color: '#f59e0b',
                  fontFamily: '"SF Mono", Monaco, monospace',
                  maxWidth: 160,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={data.params.topic}
              >
                topic: {data.params.topic}
              </span>
            )}
          </div>
        )}
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        id="default"
        style={{
          background: TRIGGER_COLOR,
          width: 12,
          height: 12,
          border: '2px solid #fff',
          bottom: -6,
        }}
      />
    </div>
  );
};

export default TriggerNode;