import { type NodeProps } from 'reactflow';
import NodeCard, { type BaseNodeData } from './NodeCard';

const BaseNode: React.FC<NodeProps<BaseNodeData>> = ({ data, selected }) => {
  return <NodeCard data={data} selected={selected} />;
};

export default BaseNode;
export type { BaseNodeData };
