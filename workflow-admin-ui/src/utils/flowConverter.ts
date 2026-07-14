import type { Node, Edge } from 'reactflow';
import { MarkerType } from 'reactflow';
import type { WorkflowDefinition, WorkflowNode, NodeType } from '@/types/workflow';

/**
 * 条件边携带的完整条件数据（兼容表达式模式与结构化规则模式）
 */
export interface ConditionalEdgeData {
  condition?: string;
  logic?: 'and' | 'or';
  negate?: boolean;
  rules?: { field: string; operator: string; value?: any; negate?: boolean }[];
}

export function formatConditionalLabel(data?: ConditionalEdgeData): string | undefined {
  if (!data) return undefined;
  const prefix = data.negate ? 'NOT ' : '';
  if (data.condition) return `${prefix}${data.condition}`;
  if (data.rules && data.rules.length > 0) {
    const logic = data.logic === 'or' ? 'OR' : 'AND';
    const ruleText = data.rules
      .map((r) => `${r.negate ? 'NOT ' : ''}${r.field} ${r.operator} ${r.value ?? ''}`)
      .join(` ${logic} `);
    return `${prefix}${ruleText}`;
  }
  return undefined;
}

/**
 * NodeType（后端核心枚举：BUILTIN/CUSTOM/SCRIPT/EXTERNAL）→ ReactFlow 视觉节点类型
 * 注：后端只接受这 4 种值；FunctionNodeType（DATA_QUERY 等）仅用于前端视觉分类
 */
const NODE_TYPE_TO_FLOW_TYPE: Record<string, string> = {
  BUILTIN: 'customNode',
  CUSTOM: 'customNode',
  SCRIPT: 'scriptNode',
  EXTERNAL: 'customNode',
};

/**
 * FunctionNodeType（前端视觉分类）→ ReactFlow 视觉节点类型
 * 用于从 node.data.type（可能是旧版 FunctionNodeType）映射到 ReactFlow type
 */
const FUNCTION_NODE_TYPE_TO_FLOW_TYPE: Record<string, string> = {
  PARAM_VALIDATE: 'validateNode',
  DYNAMIC_VALIDATE: 'validateNode',
  DATA_QUERY: 'queryNode',
  DATA_TRANSFORM: 'transformNode',
  ASSEMBLE_RESPONSE: 'assembleNode',
  SCRIPT: 'scriptNode',
  CUSTOM: 'customNode',
  CONDITION_BRANCH: 'conditionNode',
  FILTER: 'conditionNode',
  PARALLEL: 'parallelNode',
  SUB_WORKFLOW: 'subWorkflowNode',
  EIP_ROUTER: 'eipRouterNode',
};

const FLOW_TYPE_TO_FUNCTION_NODE_TYPE: Record<string, string> = {
  validateNode: 'PARAM_VALIDATE',
  queryNode: 'DATA_QUERY',
  transformNode: 'DATA_TRANSFORM',
  assembleNode: 'ASSEMBLE_RESPONSE',
  scriptNode: 'SCRIPT',
  customNode: 'CUSTOM',
  conditionNode: 'CONDITION_BRANCH',
  filterNode: 'FILTER',
  parallelNode: 'PARALLEL',
  subWorkflowNode: 'SUB_WORKFLOW',
  eipRouterNode: 'EIP_ROUTER',
};

/**
 * 从 functionRef 前缀推导发送到后端的 NodeType。
 * 后端 DTO 枚举只接受 FunctionNodeType 视觉分类值（DATA_QUERY / CUSTOM / SCRIPT 等），
 * 不能发送 BUILTIN / EXTERNAL 等核心类型，否则 Jackson 反序列化会失败。
 */
function functionRefToNodeType(functionRef?: string): NodeType {
  if (!functionRef) return 'CUSTOM' as NodeType;
  // 复用视觉分类推导，确保返回值在后端 NodeType 枚举范围内
  return functionRefToFunctionNodeType(functionRef) as NodeType;
}

/**
 * 从 functionRef 前缀推导前端视觉 FunctionNodeType（用于 NODE_TYPE_MAP 配色等）
 */
function functionRefToFunctionNodeType(functionRef?: string): string {
  if (!functionRef) return 'CUSTOM';
  if (functionRef.startsWith('builtin:')) {
    // 内置函数根据名称细分视觉类型
    const name = functionRef;
    if (name === 'builtin:paramValidate') return 'PARAM_VALIDATE';
    if (name === 'builtin:conditionBranch') return 'CONDITION_BRANCH';
    if (name === 'builtin:filter') return 'FILTER';
    if (['builtin:dbExecute', 'builtin:httpCall', 'builtin:paginate', 'builtin:redisCommand'].includes(name)) return 'DATA_QUERY';
    if (name === 'builtin:loopAggregator') return 'PARALLEL';
    if (name === 'builtin:responseWrapper') return 'ASSEMBLE_RESPONSE';
    if (name === 'builtin:groovyScript') return 'SCRIPT';
    return 'CUSTOM';
  }
  if (functionRef.startsWith('script:')) return 'SCRIPT';
  if (functionRef.startsWith('external:')) return 'CUSTOM';
  return 'CUSTOM';
}

export function mapNodeTypeToFlowType(type: string, functionRef?: string): string {
  // 优先使用 FunctionNodeType 映射（前端视觉分类）
  if (FUNCTION_NODE_TYPE_TO_FLOW_TYPE[type]) return FUNCTION_NODE_TYPE_TO_FLOW_TYPE[type];
  // 兼容后端核心 NodeType（BUILTIN/CUSTOM/SCRIPT/EXTERNAL）
  if (NODE_TYPE_TO_FLOW_TYPE[type]) {
    // 对于 BUILTIN，尝试从 functionRef 推导更精确的视觉类型
    if (type === 'BUILTIN' && functionRef) {
      const fnType = functionRefToFunctionNodeType(functionRef);
      if (FUNCTION_NODE_TYPE_TO_FLOW_TYPE[fnType]) return FUNCTION_NODE_TYPE_TO_FLOW_TYPE[fnType];
    }
    return NODE_TYPE_TO_FLOW_TYPE[type];
  }
  return 'customNode';
}

export function mapFlowTypeToNodeType(type: string): string {
  return FLOW_TYPE_TO_FUNCTION_NODE_TYPE[type] || 'CUSTOM';
}

const DEFAULT_EDGE_STYLE = {
  stroke: '#595959',
  strokeWidth: 2,
};

const DEFAULT_MARKER_END = {
  type: MarkerType.ArrowClosed,
  color: '#595959',
  width: 16,
  height: 16,
};

export function buildEdgesFromDependencies(nodes: WorkflowNode[]): Edge[] {
  const edges: Edge[] = [];
  nodes.forEach((node) => {
    node.dependsOn?.forEach((depId) => {
      edges.push({
        id: `e-${depId}-${node.id}`,
        source: depId,
        target: node.id,
        type: 'smoothstep',
        style: DEFAULT_EDGE_STYLE,
        markerEnd: DEFAULT_MARKER_END,
      });
    });
    if (node.conditionalNexts) {
      node.conditionalNexts.forEach((next, idx) => {
        if (!next.target) return;
        const data: ConditionalEdgeData = {
          condition: next.condition,
          logic: next.logic,
          negate: next.negate,
          rules: next.rules,
        };
        edges.push({
          id: `e-${node.id}-${next.target}-c${idx}`,
          source: node.id,
          target: next.target,
          type: 'conditional',
          label: formatConditionalLabel(data),
          data,
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color: '#13c2c2',
            width: 16,
            height: 16,
          },
        });
      });
    }
  });
  return edges;
}

export function getIncomingNodes(nodeId: string, edges: Edge[]): string[] {
  return edges.filter((e) => e.target === nodeId).map((e) => e.source);
}

export function getConditionalNexts(
  nodeId: string,
  edges: Edge[],
): { condition?: string; logic: 'and' | 'or'; negate?: boolean; rules?: { field: string; operator: string; value?: any; negate?: boolean }[]; target: string }[] {
  return edges
    .filter((e) => e.source === nodeId && e.type === 'conditional')
    .map((e) => {
      const data = (e.data as ConditionalEdgeData | undefined) || {};
      return {
        condition: data.condition ?? (typeof e.label === 'string' ? e.label : undefined),
        logic: data.logic || 'and',
        negate: data.negate,
        rules: data.rules,
        target: e.target,
      };
    });
}

export function definitionToFlow(def: WorkflowDefinition): { nodes: Node[]; edges: Edge[] } {
  let nodes = def.nodes.map((node, index) => ({
    id: node.id,
    type: mapNodeTypeToFlowType(node.type, node.functionRef),
    position: {
      x: node.position?.x ?? 300,
      y: node.position?.y ?? index * 150,
    },
    data: {
      label: node.name,
      functionRef: node.functionRef,
      errorStrategy: node.errorStrategy,
      timeoutMs: node.timeoutMs,
      params: node.params,
      decorators: node.decorators,
      decoratorParams: node.decoratorParams,
      // 保留视觉分类用的 FunctionNodeType（不发送到后端）
      type: node.type && ['PARAM_VALIDATE','DYNAMIC_VALIDATE','DATA_QUERY','DATA_TRANSFORM','ASSEMBLE_RESPONSE','CONDITION_BRANCH','FILTER','PARALLEL','SUB_WORKFLOW','EIP_ROUTER'].includes(node.type)
        ? node.type
        : functionRefToFunctionNodeType(node.functionRef),
    },
  }));

  let edges = buildEdgesFromDependencies(def.nodes);

  const protocol = def.protocol?.toUpperCase();
  if (protocol && protocol !== 'NONE') {
    const triggerFunctionRef = protocolToTriggerFunctionRef(protocol);
    const triggerLabel = protocolToTriggerLabel(protocol);

    const triggerNode: Node = {
      id: `trigger-${def.workflowId || 'root'}`,
      type: 'triggerNode',
      position: { x: 300, y: 20 },
      data: {
        label: triggerLabel,
        functionRef: triggerFunctionRef,
        errorStrategy: 'FAIL' as any,
        timeoutMs: 3000,
        params: {
          method: def.method,
          path: def.path || def.bindKey,
          topic: def.path,
        },
        decorators: ['logging:default'],
        decoratorParams: {},
        type: 'TRIGGER',
        _isHeadTrigger: true,
      },
    };

    const targetIds = new Set(edges.map((e) => e.target));
    const currentHeadIds = nodes
      .filter((n) => !targetIds.has(n.id))
      .map((n) => n.id);

    nodes = nodes.map((n) => ({
      ...n,
      position: { ...n.position, y: n.position.y + 120 },
    }));

    const newEdges: Edge[] = currentHeadIds.map((targetNodeId) => ({
      id: `e-${triggerNode.id}-${targetNodeId}`,
      source: triggerNode.id,
      target: targetNodeId,
      type: 'smoothstep',
      style: DEFAULT_EDGE_STYLE,
      markerEnd: DEFAULT_MARKER_END,
    }));

    nodes = [triggerNode, ...nodes];
    edges = [...edges, ...newEdges];
  }

  return { nodes, edges };
}

function protocolToTriggerFunctionRef(protocol: string): string {
  return {
    'HTTP': 'trigger:httpInbound',
    'HTTPS': 'trigger:httpInbound',
    'KAFKA': 'trigger:kafkaConsumer',
    'GRPC': 'trigger:grpcInbound',
    'DUBBO': 'trigger:dubboProvider',
  }[protocol] || 'trigger:httpInbound';
}

function protocolToTriggerLabel(protocol: string): string {
  return {
    'HTTP': 'HTTP Inbound',
    'HTTPS': 'HTTP Inbound',
    'KAFKA': 'Kafka Consumer',
    'GRPC': 'gRPC Inbound',
    'DUBBO': 'Dubbo Provider',
  }[protocol] || 'HTTP Inbound';
}

export function flowToDefinition(
  nodes: Node[],
  edges: Edge[],
  meta: Partial<WorkflowDefinition>,
): WorkflowDefinition {
  const { nodes: _metaNodes, ...rest } = meta;

  const triggerNode = nodes.find((n) => n.data._isHeadTrigger);

  const effectiveProtocol = triggerNode ? functionRefToProtocol(triggerNode.data.functionRef) : rest.protocol;

  let filteredEdges = edges;
  let filteredNodes = nodes;
  const triggerTargetIds = new Set<string>();

  if (triggerNode) {
    filteredNodes = nodes.filter((n) => n.id !== triggerNode.id);
    triggerTargetIds.addAll(edges.filter((e) => e.source === triggerNode.id).map((e) => e.target));
    filteredEdges = edges.filter((e) => e.source !== triggerNode.id && e.target !== triggerNode.id);
  }

  const dependsOnMap = new Map<string, string[]>();
  filteredEdges.forEach((e) => {
    const list = dependsOnMap.get(e.target) || [];
    list.push(e.source);
    dependsOnMap.set(e.target, list);
  });

  filteredNodes.forEach((node) => {
    if (triggerTargetIds.has(node.id)) {
      const existing = dependsOnMap.get(node.id) || [];
      const cleaned = existing.filter((id) => id !== triggerNode?.id);
      dependsOnMap.set(node.id, cleaned);
    }
  });

  return {
    name: rest.name || '',
    category: rest.category || 'BUSINESS',
    protocol: effectiveProtocol || 'HTTP',
    method: triggerNode?.data.params?.method || rest.method,
    path: triggerNode?.data.params?.path || rest.path,
    inputSchemaFormat: rest.inputSchemaFormat || 'json-schema',
    outputSchemaFormat: rest.outputSchemaFormat || 'json-schema',
    ...rest,
    nodes: filteredNodes.map((node) => {
      const conditionalNexts = getConditionalNexts(node.id, edges);
      return {
        id: node.id,
        name: node.data.label,
        type: functionRefToNodeType(node.data.functionRef),
        functionRef: node.data.functionRef,
        errorStrategy: node.data.errorStrategy,
        timeoutMs: node.data.timeoutMs,
        params: node.data.params,
        decorators: node.data.decorators,
        decoratorParams: node.data.decoratorParams,
        dependsOn: dependsOnMap.get(node.id) || [],
        position: node.position,
        asyncExecution: node.data.asyncExecution ?? false,
        ...(conditionalNexts.length ? { conditionalNexts } : {}),
      } as WorkflowNode;
    }),
  };
}

function functionRefToProtocol(functionRef?: string): string | undefined {
  if (!functionRef) return undefined;
  return {
    'trigger:httpInbound': 'HTTP',
    'trigger:kafkaConsumer': 'KAFKA',
    'trigger:grpcInbound': 'GRPC',
    'trigger:dubboProvider': 'DUBBO',
  }[functionRef];
}
