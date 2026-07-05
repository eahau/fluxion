import { create } from 'zustand';
import type { Node, Edge, XYPosition, OnNodesChange, OnEdgesChange, OnConnect } from 'reactflow';
import { applyNodeChanges, applyEdgeChanges, addEdge } from 'reactflow';
import type { WorkflowDefinition, NodeType } from '@/types/workflow';
import { definitionToFlow, flowToDefinition, mapNodeTypeToFlowType } from '@/utils/flowConverter';
import { NODE_TYPE_MAP } from '@/constants/nodeTypes';

interface WorkflowState {
  nodes: Node[];
  edges: Edge[];
  selectedNodeId: string | null;
  selectedEdgeId: string | null;
  workflowMeta: Partial<WorkflowDefinition>;

  // History (undo/redo)
  past: Array<{ nodes: Node[]; edges: Edge[] }>;
  future: Array<{ nodes: Node[]; edges: Edge[] }>;

  // Clipboard
  clipboard: { nodes: Node[]; edges: Edge[] } | null;
  /** 连续粘贴计数，用于累积偏移避免节点堆叠 */
  pasteCount: number;

  addNode: (type: NodeType, position: XYPosition, initData?: Partial<any>) => string;
  removeNode: (id: string) => void;
  updateNodeData: (id: string, data: Partial<any>) => void;
  setSelectedNode: (id: string | null) => void;
  setSelectedEdge: (id: string | null) => void;
  updateEdgeData: (id: string, data: Partial<Edge>) => void;
  onNodesChange: OnNodesChange;
  onEdgesChange: OnEdgesChange;
  onConnect: OnConnect;
  loadDefinition: (def: WorkflowDefinition) => void;
  toDefinition: () => WorkflowDefinition;
  setWorkflowMeta: (meta: Partial<WorkflowDefinition>) => void;

  // History operations
  undo: () => void;
  redo: () => void;
  canUndo: () => boolean;
  canRedo: () => boolean;
  pushHistory: () => void;

  // Clipboard operations
  copy: () => void;
  paste: (offset?: XYPosition) => void;
  duplicateSelected: () => void;

  // Select all
  selectAll: () => void;

  /** 确保工作流头部存在 paramValidate 节点，其 params.schema 与 inputSchema 同步 */
  ensureParamValidateHeadNode: (inputSchema: Record<string, any> | undefined) => void;
}

let nodeIdCounter = 1;

function createDefaultNode(type: NodeType, position: XYPosition, initData?: Partial<any>): Node {
  const id = `n${nodeIdCounter++}`;
  const meta = NODE_TYPE_MAP[type];
  return {
    id,
    type: mapNodeTypeToFlowType(type),
    position,
    data: {
      label: initData?.label || `${meta?.label || '节点'} ${id}`,
      functionRef: initData?.functionRef || 'builtin:paramValidate',
      errorStrategy: 'FAIL',
      timeoutMs: 3000,
      params: {},
      decorators: ['logging:default'],
      decoratorParams: {},
      type,
      ...initData,
    },
  };
}

// 生成唯一节点 ID 的辅助函数（使用 timestamp + counter 避免与任意格式 ID 冲突）
function generateUniqueId(): string {
  return `n${Date.now().toString(36)}_${nodeIdCounter++}`;
}

export const useWorkflowStore = create<WorkflowState>((set, get) => ({
  nodes: [],
  edges: [],
  selectedNodeId: null,
  selectedEdgeId: null,
  workflowMeta: {},
  past: [],
  future: [],
  clipboard: null,
  pasteCount: 0,

  addNode: (type, position, initData) => {
    get().pushHistory();
    const node = createDefaultNode(type, position, initData);
    set((state) => ({
      nodes: [...state.nodes, node],
      future: [], // 新操作清空 future
    }));
    return node.id;
  },

  removeNode: (id) => {
    get().pushHistory();
    set((state) => ({
      nodes: state.nodes.filter((n) => n.id !== id),
      edges: state.edges.filter((e) => e.source !== id && e.target !== id),
      selectedNodeId: state.selectedNodeId === id ? null : state.selectedNodeId,
      future: [],
    }));
  },

  updateNodeData: (id, data) =>
    set((state) => ({
      nodes: state.nodes.map((n) =>
        n.id === id ? { ...n, data: { ...n.data, ...data } } : n,
      ),
    })),

  setSelectedNode: (id) => set({ selectedNodeId: id, selectedEdgeId: null }),

  setSelectedEdge: (id) => set({ selectedEdgeId: id, selectedNodeId: null }),

  updateEdgeData: (id, data) =>
    set((state) => ({
      edges: state.edges.map((e) => (e.id === id ? { ...e, ...data } : e)),
    })),

  onNodesChange: (changes) =>
    set((state) => {
      const updatedNodes = applyNodeChanges(changes, state.nodes);
      // 同步 select 变更到 selectedNodeId
      const selectChanges = changes.filter((c) => c.type === 'select');
      if (selectChanges.length > 0) {
        const selected = updatedNodes.find((n) => n.selected);
        return {
          nodes: updatedNodes,
          selectedNodeId: selected?.id ?? null,
        };
      }
      return { nodes: updatedNodes };
    }),

  onEdgesChange: (changes) =>
    set((state) => ({
      edges: applyEdgeChanges(changes, state.edges),
    })),

  onConnect: (connection) => {
    get().pushHistory();
    set((state) => {
      const isConditional = !!connection.sourceHandle && connection.sourceHandle !== 'default';
      const edge = {
        ...connection,
        type: isConditional ? 'conditional' : 'smoothstep',
        label: isConditional ? connection.sourceHandle : undefined,
        data: isConditional
          ? ({ condition: connection.sourceHandle } as import('@/utils/flowConverter').ConditionalEdgeData)
          : undefined,
      };
      return {
        edges: addEdge(edge, state.edges),
        future: [],
      };
    });
  },

  loadDefinition: (def) => {
    const { nodes, edges } = definitionToFlow(def);
    nodeIdCounter = Math.max(1, ...nodes.map((n) => parseInt(n.id.replace('n', ''), 10) || 0)) + 1;
    // 确保 method 有默认值且大小写正确（HTTP/HTTPS 默认 GET，选项均为大写）
    const protocol = def.protocol || 'HTTP';
    const workflowMeta = {
      ...def,
      method: def.method?.toUpperCase() || (protocol === 'HTTP' || protocol === 'HTTPS' ? 'GET' : undefined),
    };
    set({ nodes, edges, workflowMeta, past: [], future: [], pasteCount: 0, clipboard: null });
  },

  toDefinition: () => {
    const { nodes, edges, workflowMeta } = get();
    return flowToDefinition(nodes, edges, workflowMeta);
  },

  setWorkflowMeta: (meta) =>
    set((state) => ({
      workflowMeta: { ...state.workflowMeta, ...meta },
    })),

  // History 操作
  pushHistory: () =>
    set((state) => ({
      past: [...state.past.slice(-49), { nodes: state.nodes, edges: state.edges }], // 最多保留 50 条历史
    })),

  undo: () => {
    const state = get();
    if (state.past.length === 0) return;

    const previous = state.past[state.past.length - 1];
    const newPast = state.past.slice(0, -1);

    set({
      past: newPast,
      future: [{ nodes: state.nodes, edges: state.edges }, ...state.future],
      nodes: previous.nodes,
      edges: previous.edges,
    });
  },

  redo: () => {
    const state = get();
    if (state.future.length === 0) return;

    const next = state.future[0];
    const newFuture = state.future.slice(1);

    set({
      past: [...state.past, { nodes: state.nodes, edges: state.edges }],
      future: newFuture,
      nodes: next.nodes,
      edges: next.edges,
    });
  },

  canUndo: () => get().past.length > 0,
  canRedo: () => get().future.length > 0,

  // Clipboard 操作
  copy: () => {
    const state = get();
    const selectedNodes = state.nodes.filter((n) => n.selected);
    if (selectedNodes.length === 0) return;

    const selectedIds = new Set(selectedNodes.map((n) => n.id));
    const selectedEdges = state.edges.filter(
      (e) => selectedIds.has(e.source) && selectedIds.has(e.target),
    );

    set({
      clipboard: { nodes: selectedNodes, edges: selectedEdges },
      pasteCount: 0, // 重新复制时重置粘贴计数
    });
  },

  paste: (offset = { x: 50, y: 50 }) => {
    const state = get();
    if (!state.clipboard || state.clipboard.nodes.length === 0) return;

    get().pushHistory();

    // 累积偏移：连续粘贴时每次偏移量递增，避免节点堆叠
    const count = state.pasteCount + 1;
    const cumulativeOffset = {
      x: offset.x * count,
      y: offset.y * count,
    };

    // 创建 ID 映射，为每个剪贴板节点生成新 ID
    const idMap = new Map<string, string>();
    const newNodes: Node[] = [];
    for (const node of state.clipboard.nodes) {
      const newId = generateUniqueId();
      idMap.set(node.id, newId);
      newNodes.push({
        ...node,
        id: newId,
        // 深拷贝 data，避免与原节点共享引用
        data: JSON.parse(JSON.stringify(node.data)),
        position: {
          x: node.position.x + cumulativeOffset.x,
          y: node.position.y + cumulativeOffset.y,
        },
        selected: true,
      });
    }

    // 复制边，更新 source/target ID
    const newEdges = state.clipboard.edges
      .filter((edge) => idMap.has(edge.source) && idMap.has(edge.target))
      .map((edge) => ({
        ...edge,
        id: `e-${idMap.get(edge.source)}-${idMap.get(edge.target)}-${Date.now()}`,
        source: idMap.get(edge.source)!,
        target: idMap.get(edge.target)!,
      }));

    // 取消原有选中状态
    const unselectedNodes = state.nodes.map((n) => ({ ...n, selected: false }));

    set({
      nodes: [...unselectedNodes, ...newNodes],
      edges: [...state.edges, ...newEdges],
      pasteCount: count,
      future: [],
    });
  },

  duplicateSelected: () => {
    get().copy();
    get().paste({ x: 50, y: 50 });
  },

  selectAll: () => {
    set((state) => ({
      nodes: state.nodes.map((n) => ({ ...n, selected: true })),
    }));
  },

  ensureParamValidateHeadNode: (inputSchema) => {
    const state = get();
    const existingValidateNode = state.nodes.find(
      (n) => n.data.functionRef === 'builtin:paramValidate',
    );

    // inputSchema 为空或已清除 → 若存在自动插入的 paramValidate 头节点，则移除它
    if (!inputSchema || Object.keys(inputSchema).length === 0) {
      if (existingValidateNode) {
        get().pushHistory();
        const validateId = existingValidateNode.id;
        // 获取 paramValidate 的下游节点，将上游边的 source 直接指向下游
        const outgoingEdges = state.edges.filter((e) => e.source === validateId);
        const incomingEdges = state.edges.filter((e) => e.target === validateId);
        const upstreamIds = incomingEdges.map((e) => e.source);
        const downstreamIds = outgoingEdges.map((e) => e.target);

        // 删除 paramValidate 节点及其关联边
        const filteredNodes = state.nodes.filter((n) => n.id !== validateId);
        const filteredEdges = state.edges.filter(
          (e) => e.source !== validateId && e.target !== validateId,
        );

        // 重建上游 → 下游的边（保持依赖链不断）
        const reconnectedEdges: Edge[] = [];
        for (const upId of upstreamIds) {
          for (const downId of downstreamIds) {
            reconnectedEdges.push({
              id: `e-${upId}-${downId}`,
              source: upId,
              target: downId,
              type: 'smoothstep',
              style: { stroke: '#595959', strokeWidth: 2 },
              markerEnd: { type: 'arrowclosed' as any, color: '#595959', width: 16, height: 16 },
            });
          }
        }

        set({
          nodes: filteredNodes,
          edges: [...filteredEdges, ...reconnectedEdges],
          future: [],
        });
      }
      return;
    }

    // 已存在 paramValidate 节点 → 更新其 params.schema
    if (existingValidateNode) {
      get().pushHistory();
      const updatedNodes = state.nodes.map((n) =>
        n.id === existingValidateNode.id
          ? { ...n, data: { ...n.data, params: { schema: inputSchema } } }
          : n,
      );
      set({ nodes: updatedNodes, future: [] });
      return;
    }

    // 不存在 → 创建头节点
    get().pushHistory();
    const headId = generateUniqueId();
    const headNode: Node = {
      id: headId,
      type: 'validateNode',
      position: { x: 300, y: 50 },
      data: {
        label: '入参校验',
        functionRef: 'builtin:paramValidate',
        errorStrategy: 'FAIL',
        timeoutMs: 3000,
        params: { schema: inputSchema },
        decorators: ['logging:default'],
        decoratorParams: {},
        type: 'PARAM_VALIDATE',
      },
    };

    // 找到当前的头节点（没有入边的节点），让它们依赖于新的 paramValidate
    const targetIds = new Set(state.edges.map((e) => e.target));
    const currentHeadIds = state.nodes
      .filter((n) => !targetIds.has(n.id))
      .map((n) => n.id);

    // 将现有节点下移，为头节点留出空间
    const shiftedNodes = state.nodes.map((n) => ({
      ...n,
      position: { ...n.position, y: n.position.y + 180 },
    }));

    // 创建从头节点到当前头节点的边
    const newEdges: Edge[] = currentHeadIds.map((targetNodeId) => ({
      id: `e-${headId}-${targetNodeId}`,
      source: headId,
      target: targetNodeId,
      type: 'smoothstep',
      style: { stroke: '#595959', strokeWidth: 2 },
      markerEnd: { type: 'arrowclosed' as any, color: '#595959', width: 16, height: 16 },
    }));

    set({
      nodes: [headNode, ...shiftedNodes],
      edges: [...state.edges, ...newEdges],
      future: [],
    });
  },
}));
