import { type NodeProps } from 'reactflow';
import NodeCard from './NodeCard';
import type { BaseNodeData } from './BaseNode';

const TransformNode: React.FC<NodeProps<BaseNodeData>> = ({ data, selected }) => {
  return <NodeCard data={data} selected={selected} />;
};

export default TransformNode;
