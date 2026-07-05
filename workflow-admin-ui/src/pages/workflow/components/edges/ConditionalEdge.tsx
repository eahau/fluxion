import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, type EdgeProps } from 'reactflow';

const ConditionalEdge: React.FC<EdgeProps> = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  label,
  style = {},
  markerEnd,
  selected,
}) => {
  // 使用 smoothStep 路径，更清晰
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 12,
  });

  const edgeColor = selected ? '#06b6d4' : '#06b6d4';

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          ...style,
          stroke: edgeColor,
          strokeWidth: selected ? 2 : 1.5,
          strokeDasharray: '6 4',
        }}
      />
      {label && (
        <EdgeLabelRenderer>
          <div
            className="nodrag nopan"
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
              background: '#fff',
              padding: '3px 10px',
              borderRadius: 12,
              fontSize: 11,
              fontWeight: 500,
              border: `1px solid ${edgeColor}40`,
              color: edgeColor,
              pointerEvents: 'all',
              cursor: 'default',
              boxShadow: '0 2px 4px rgba(0,0,0,0.04)',
            }}
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default ConditionalEdge;
