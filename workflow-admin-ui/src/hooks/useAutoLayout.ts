import { useCallback } from 'react';
import dagre from 'dagre';
import type { Node, Edge } from 'reactflow';
import { useWorkflowStore } from '@/stores/useWorkflowStore';

// 节点宽高（用于布局计算）
const NODE_WIDTH = 180;
const NODE_HEIGHT = 80;

export interface AutoLayoutOptions {
  direction?: 'TB' | 'LR'; // TB = top-bottom, LR = left-right
  nodeWidth?: number;
  nodeHeight?: number;
  rankSep?: number; // 层间距
  nodeSep?: number; // 同层节点间距
}

/**
 * 使用 dagre 算法对节点进行拓扑排序布局
 * 参考 n8n、Node-RED 的自动布局实现
 */
export function useAutoLayout() {
  const { nodes, edges, onNodesChange } = useWorkflowStore();

  const autoLayout = useCallback(
    (options?: AutoLayoutOptions) => {
      if (nodes.length === 0) return;

      const {
        direction = 'TB',
        nodeWidth = NODE_WIDTH,
        nodeHeight = NODE_HEIGHT,
        rankSep = 80,
        nodeSep = 60,
      } = options || {};

      // 创建 dagre 图
      const g = new dagre.graphlib.Graph();
      g.setDefaultEdgeLabel(() => ({}));
      g.setGraph({
        rankdir: direction,
        ranksep: rankSep,
        nodesep: nodeSep,
        marginx: 50,
        marginy: 50,
      });

      // 添加节点
      nodes.forEach((node) => {
        g.setNode(node.id, { width: nodeWidth, height: nodeHeight });
      });

      // 添加边
      edges.forEach((edge) => {
        g.setEdge(edge.source, edge.target);
      });

      // 执行布局计算
      dagre.layout(g);

      // 应用新位置
      const changes = nodes.map((node) => {
        const nodeWithPosition = g.node(node.id);
        return {
          id: node.id,
          type: 'position',
          payload: {
            position: {
              x: nodeWithPosition.x - nodeWidth / 2,
              y: nodeWithPosition.y - nodeHeight / 2,
            },
          },
        };
      });

      // 批量更新位置
      onNodesChange(changes as any);
    },
    [nodes, edges, onNodesChange],
  );

  return { autoLayout };
}
