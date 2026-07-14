import { useMemo, useCallback } from 'react';
import { Modal } from 'antd';
import { debugWorkflowNode } from '@/services/workflow';
import { useDebugStore } from '@/stores/useDebugStore';
import { FunctionTestPanel } from '@/features/function-test';
import type { FunctionDefinition } from '@/types/function';
import type { Node } from 'reactflow';

interface NodeTestModalProps {
  workflowId: string;
  node: Node | null;
  /** 从 API 获取的函数入参 Schema（优先使用） */
  paramSchema?: Record<string, any>;
  visible: boolean;
  onClose: () => void;
}

const NodeTestModal: React.FC<NodeTestModalProps> = ({ workflowId, node, paramSchema, visible, onClose }) => {
  const { mockConfigs } = useDebugStore();

  const functionId = useMemo(() => node?.data?.functionRef || node?.data?.type || 'node-test', [node]);
  const functionDefinition = useMemo<FunctionDefinition | null>(() => {
    if (!node) return null;
    return {
      id: functionId,
      name: functionId,
      category: 'CUSTOM',
      nodeType: (node.data?.type as any) || 'CUSTOM',
      status: 'ACTIVE',
      scope: 'PRIVATE',
      config: {
        paramSchema,
      },
    } as FunctionDefinition;
  }, [functionId, node, paramSchema]);

  const handleExecuteTest = useCallback(async ({ inputs }: { inputs: any }) => {
    if (!node) throw new Error('missing node');
    return debugWorkflowNode(workflowId, node.id, inputs, {
      enabled: true,
      rules: mockConfigs,
    });
  }, [mockConfigs, node, workflowId]);

  return (
    <Modal
      title={`单节点测试: ${node?.data?.label || ''}`}
      open={visible}
      onCancel={onClose}
      width={920}
      footer={null}
    >
      {functionDefinition && (
        <FunctionTestPanel
          functionId={functionId}
          functionDefinition={functionDefinition}
          onExecuteTest={handleExecuteTest}
        />
      )}
    </Modal>
  );
};

export default NodeTestModal;
