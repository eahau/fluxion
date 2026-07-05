import { useState } from 'react';
import { AppstoreOutlined } from '@ant-design/icons';
import NodePalette from './NodePalette';

/** 收起时宽度（仅图标） */
const COLLAPSED_WIDTH = 48;
/** 展开时宽度 */
const EXPANDED_WIDTH = 260;

const DesignerLeftPanel: React.FC<{ darkMode?: boolean }> = ({ darkMode }) => {
  const [hovered, setHovered] = useState(false);
  const width = hovered ? EXPANDED_WIDTH : COLLAPSED_WIDTH;

  return (
    <div
      className="fluxion-designer-left-panel"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        width,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        borderRight: '1px solid var(--fluxion-border)',
        background: 'var(--fluxion-bg-elevated)',
        flexShrink: 0,
        overflow: 'hidden',
        transition: 'width 0.2s ease',
        position: 'relative',
        zIndex: 10,
      }}
    >
      {/* 头部 */}
      <div style={{
        padding: '12px 0',
        borderBottom: '1px solid var(--fluxion-border)',
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flexShrink: 0,
        justifyContent: hovered ? 'flex-start' : 'center',
        paddingLeft: hovered ? 16 : 0,
        paddingRight: hovered ? 16 : 0,
        transition: 'padding 0.2s ease',
        whiteSpace: 'nowrap',
      }}>
        <AppstoreOutlined style={{ color: 'var(--fluxion-primary)', fontSize: 22, flexShrink: 0 }} />
        {hovered && (
          <span style={{ fontWeight: 600, fontSize: 14, color: 'var(--fluxion-text)', opacity: 1, transition: 'opacity 0.15s ease' }}>
            函数库
          </span>
        )}
      </div>

      {/* 内容区 */}
      <div style={{
        flex: 1,
        overflow: 'hidden',
        opacity: hovered ? 1 : 0,
        transition: 'opacity 0.15s ease',
        pointerEvents: hovered ? 'auto' : 'none',
      }}>
        <NodePalette />
      </div>
    </div>
  );
};

export default DesignerLeftPanel;
