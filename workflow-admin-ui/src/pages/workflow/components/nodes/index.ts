import ValidateNode from './ValidateNode';
import QueryNode from './QueryNode';
import TransformNode from './TransformNode';
import AssembleNode from './AssembleNode';
import ScriptNode from './ScriptNode';
import CustomNode from './CustomNode';
import ConditionNode from './ConditionNode';
import ParallelNode from './ParallelNode';
import SubWorkflowNode from './SubWorkflowNode';
import EipRouterNode from './EipRouterNode';
import StickyNote from './StickyNote';
import TriggerNode from './TriggerNode';

export const nodeTypes = {
  validateNode: ValidateNode,
  queryNode: QueryNode,
  transformNode: TransformNode,
  assembleNode: AssembleNode,
  scriptNode: ScriptNode,
  customNode: CustomNode,
  conditionNode: ConditionNode,
  parallelNode: ParallelNode,
  subWorkflowNode: SubWorkflowNode,
  eipRouterNode: EipRouterNode,
  stickyNote: StickyNote,
  triggerNode: TriggerNode,
};
