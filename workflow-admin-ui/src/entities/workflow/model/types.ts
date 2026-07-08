// Workflow 实体核心类型
export type WorkflowStatus = 'draft' | 'published' | 'archived' | 'disabled';
export type InstanceStatus = 'pending' | 'running' | 'success' | 'failed' | 'timeout' | 'canceled';
export type NodeType =
  | 'start' | 'end'
  | 'query' | 'transform' | 'assemble' | 'validate'
  | 'function' | 'custom' | 'condition' | 'parallel'
  | 'subWorkflow' | 'script' | 'eipRouter' | 'stickyNote';

export interface GraphNode {
  id: string;
  type: NodeType;
  label?: string;
  x: number;
  y: number;
  config?: Record<string, any>;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  config?: Record<string, any>;
}

export interface FlowGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface WorkflowBrief {
  id: string;
  name: string;
  description?: string;
  status?: WorkflowStatus;
  updatedAt?: number;
  version?: number;
}
