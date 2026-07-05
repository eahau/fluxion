import { type NodeProps } from 'reactflow';
import NodeCard from './NodeCard';
import type { BaseNodeData } from './BaseNode';

const ParallelNode: React.FC<NodeProps<BaseNodeData>> = ({ data, selected }) => {
  return (
    <NodeCard
      data={data}
      selected={selected}
      hideDefaultSource
      extraSourceHandles={[
        { id: 'branch-1', label: '分支1', left: '25%' },
        { id: 'branch-2', label: '分支2', left: '50%' },
        { id: 'branch-3', label: '分支3', left: '75%' },
      ]}
    />
  );
};

export default ParallelNode;
