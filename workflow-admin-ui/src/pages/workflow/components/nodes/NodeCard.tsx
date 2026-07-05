import { Handle, Position } from 'reactflow';
import { PushpinOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { NODE_TYPE_MAP, STATUS_COLORS } from '@/constants/nodeTypes';

export interface BaseNodeData {
  label: string;
  functionRef: string;
  type: string;
  isValid?: boolean;
  durationMs?: number;
  status?: string;
  breakpoint?: boolean;
  inlineOutput?: any;
  isPinned?: boolean;
}

export interface NodeCardProps {
  data: BaseNodeData;
  selected?: boolean;
  extraSourceHandles?: Array<{
    id: string;
    label?: string;
    left?: string | number;
  }>;
  hideDefaultSource?: boolean;
}

function formatInlineOutput(output: any): string {
  if (output === null || output === undefined) return '';
  if (typeof output === 'string') return output;
  if (typeof output === 'number' || typeof output === 'boolean') return String(output);
  try {
    const str = JSON.stringify(output);
    return str.length > 100 ? str.slice(0, 100) + '...' : str;
  } catch {
    return '[output]';
  }
}

const NodeCard: React.FC<NodeCardProps> = ({
  data,
  selected,
  extraSourceHandles,
  hideDefaultSource,
}) => {
  const meta = NODE_TYPE_MAP[data.type];
  const color = meta?.color || '#94a3b8';
  const statusColor = data.status ? STATUS_COLORS[data.status as keyof typeof STATUS_COLORS] : null;

  // 动态样式计算
  const cardStyle: React.CSSProperties = {
    minWidth: 180,
    maxWidth: 240,
    borderRadius: 12,
    background: selected ? `${color}12` : 'var(--fluxion-node-bg)',
    border: selected ? `2px solid ${color}` : '1px solid var(--fluxion-node-border)',
    boxShadow: selected
      ? '0 4px 16px -2px rgba(0,0,0,0.10), 0 2px 4px rgba(0,0,0,0.06)'
      : '0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.04)',
    transform: selected ? 'scale(1.02)' : undefined,
    position: 'relative',
    transition: 'border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease, background 0.2s ease',
    overflow: 'hidden',
    cursor: 'pointer',
    zIndex: selected ? 5 : undefined,
  };

  // 状态覆盖样式
  if (statusColor) {
    cardStyle.borderColor = statusColor.main;
    cardStyle.background = statusColor.light;
  }

  // Running 动画
  if (data.status === 'RUNNING') {
    cardStyle.animation = 'fluxion-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite';
  }

  return (
    <div style={cardStyle} className="fluxion-node-card">
      {/* 顶部彩色条 — 与边框同色，高度与边框宽度一致 */}
      <div
        style={{
          height: selected ? 2 : 0,
          background: color,
          transition: 'height 0.2s ease',
        }}
      />

      {/* Handle - 顶部 */}
      <Handle
        type="target"
        position={Position.Top}
        style={{
          background: color,
          width: 10,
          height: 10,
          border: '2px solid var(--fluxion-node-handle-border)',
          top: -5,
        }}
      />

      {/* 主体内容 */}
      <div style={{ padding: '12px 14px' }}>
        {/* 断点标记 */}
        {data.breakpoint && (
          <div
            style={{
              position: 'absolute',
              top: 10,
              left: 10,
              width: 8,
              height: 8,
              borderRadius: '50%',
              background: '#ef4444',
              boxShadow: '0 0 0 3px #ef444420',
              zIndex: 2,
            }}
          />
        )}

        {/* Pin 指示器 */}
        {data.isPinned && (
          <div
            style={{
              position: 'absolute',
              top: 10,
              right: 10,
              width: 20,
              height: 20,
              borderRadius: 6,
              background: 'linear-gradient(135deg, #8b5cf6, #6366f1)',
              color: '#fff',
              fontSize: 10,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 2,
              boxShadow: '0 2px 4px rgba(139, 92, 246, 0.3)',
            }}
          >
            <PushpinOutlined />
          </div>
        )}

        {/* 标题区 */}
        <div
          style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6, userSelect: 'text' }}
          onMouseDown={(e) => e.stopPropagation()}
          onPointerDown={(e) => e.stopPropagation()}
        >
          <div
            style={{
              width: 32,
              height: 32,
              borderRadius: 8,
              background: `${color}15`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 16,
              flexShrink: 0,
            }}
          >
            {meta?.icon || '⚙️'}
          </div>
          <div style={{ flex: 1, minWidth: 0, userSelect: 'text' }}>
            <div
              style={{
                fontWeight: 600,
                fontSize: 13,
                color: 'var(--fluxion-node-title)',
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
                color: 'var(--fluxion-node-subtitle)',
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

        {/* 状态标签 */}
        {data.status && statusColor && (
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              padding: '2px 8px',
              borderRadius: 6,
              background: `${statusColor.main}15`,
              fontSize: 11,
              fontWeight: 500,
              color: statusColor.dark,
              marginTop: 4,
            }}
          >
            {data.status === 'RUNNING' && (
              <div
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  background: statusColor.main,
                  animation: 'fluxion-pulse 1.5s ease-in-out infinite',
                }}
              />
            )}
            {data.status === 'SUCCESS' ? '完成' : data.status === 'FAILED' ? '失败' : data.status === 'RUNNING' ? '运行中' : data.status === 'FALLBACK' ? '降级' : '跳过'}
            {data.durationMs !== undefined && (
              <span style={{ marginLeft: 4, color: 'var(--fluxion-node-subtitle)', display: 'flex', alignItems: 'center', gap: 2 }}>
                <ClockCircleOutlined style={{ fontSize: 9 }} />
                {data.durationMs}ms
              </span>
            )}
          </div>
        )}

        {/* Inline 输出显示 */}
        {data.inlineOutput !== undefined && (
          <div
            style={{
              marginTop: 8,
              padding: '8px 10px',
              background: 'var(--fluxion-node-output-bg)',
              borderRadius: 8,
              fontSize: 11,
              color: 'var(--fluxion-node-output-text)',
              fontFamily: '"SF Mono", Monaco, "Cascadia Code", monospace',
              maxHeight: 60,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              border: '1px solid var(--fluxion-node-output-border)',
              lineHeight: 1.5,
            }}
          >
            {formatInlineOutput(data.inlineOutput)}
          </div>
        )}
      </div>

      {/* Handle - 底部 */}
      {!hideDefaultSource && (
        <Handle
          type="source"
          position={Position.Bottom}
          id="default"
          style={{
            background: color,
            width: 10,
            height: 10,
            border: '2px solid var(--fluxion-node-handle-border)',
            bottom: -5,
          }}
        />
      )}

      {/* 额外 Handle */}
      {extraSourceHandles?.map((h) => (
        <div key={h.id} style={{ position: 'absolute', bottom: -6, left: h.left ?? '50%' }}>
          <Handle
            type="source"
            position={Position.Bottom}
            id={h.id}
            style={{
              background: color,
              width: 8,
              height: 8,
              border: '2px solid var(--fluxion-node-handle-border)',
              position: 'relative',
              left: 0,
            }}
          />
          {h.label && (
            <div
              style={{
                position: 'absolute',
                top: 10,
                left: '50%',
                transform: 'translateX(-50%)',
                fontSize: 10,
                color: 'var(--fluxion-node-extra-label-text)',
                whiteSpace: 'nowrap',
                background: 'var(--fluxion-node-extra-label-bg)',
                padding: '1px 6px',
                borderRadius: 4,
              }}
            >
              {h.label}
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

export default NodeCard;
