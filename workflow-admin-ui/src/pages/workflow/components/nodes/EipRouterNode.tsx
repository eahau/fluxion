import { type NodeProps } from 'reactflow';
import NodeCard from './NodeCard';
import type { BaseNodeData } from './BaseNode';

const EipRouterNode: React.FC<NodeProps<BaseNodeData>> = ({ data, selected }) => {
  return (
    <NodeCard
      data={data}
      selected={selected}
      hideDefaultSource
      extraSourceHandles={[
        { id: 'route-1', label: '路由1', left: '25%' },
        { id: 'route-2', label: '路由2', left: '50%' },
        { id: 'route-3', label: '路由3', left: '75%' },
      ]}
    />
  );
};

export default EipRouterNode;
