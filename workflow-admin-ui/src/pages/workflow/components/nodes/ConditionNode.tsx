import { type NodeProps } from 'reactflow';
import NodeCard from './NodeCard';
import type { BaseNodeData } from './BaseNode';

const ConditionNode: React.FC<NodeProps<BaseNodeData>> = ({ data, selected }) => {
  return (
    <NodeCard
      data={data}
      selected={selected}
      hideDefaultSource
      extraSourceHandles={[
        { id: 'true', label: '是', left: '30%' },
        { id: 'false', label: '否', left: '70%' },
      ]}
    />
  );
};

export default ConditionNode;
