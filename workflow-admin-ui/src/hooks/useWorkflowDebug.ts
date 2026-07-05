import { useCallback } from 'react';
import {
  debugWorkflow,
  debugWorkflowStep,
  debugWorkflowNode,
  rerunWorkflowNode,
  getDebugHistory,
  getDebugHistoryDetail,
} from '@/services/workflow';
import { useDebugStore } from '@/stores/useDebugStore';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import type { DebugResult, NodeTrace, DebugExecutionRecord } from '@/types/workflow';

export interface UseWorkflowDebugReturn {
  execute: (inputs: Record<string, any>, breakpoints?: string[]) => Promise<DebugResult>;
  step: () => Promise<DebugResult | undefined>;
  testNode: (nodeId: string, inputData?: any) => Promise<any>;
  rerunNode: (nodeId: string, overrideInput?: any) => Promise<DebugResult | undefined>;
  loadHistory: (page?: number, size?: number) => Promise<{ list: DebugExecutionRecord[]; total: number }>;
  loadHistoryDetail: (executionId: string) => Promise<DebugExecutionRecord>;
  applyResultToCanvas: (traces: NodeTrace[]) => void;
}

export function useWorkflowDebug(workflowId: string): UseWorkflowDebugReturn {
  const {
    snapshot,
    setSnapshot,
    setCurrentResult,
    setExecutionHistory,
  } = useDebugStore();
  const { nodes, updateNodeData } = useWorkflowStore();

  const applyResultToCanvas = useCallback(
    (traces: NodeTrace[]) => {
      const traceMap = new Map(traces.map((t) => [t.nodeId, t]));
      nodes.forEach((node) => {
        const trace = traceMap.get(node.id);
        if (trace) {
          updateNodeData(node.id, {
            status: trace.status,
            durationMs: trace.durationMs,
            isValid: trace.status === 'SUCCESS',
          });
        }
      });
    },
    [nodes, updateNodeData],
  );

  const execute = useCallback(
    async (inputs: Record<string, any>, breakpoints?: string[]) => {
      const res = await debugWorkflow(workflowId, inputs, breakpoints);
      setSnapshot(res.snapshot);
      setCurrentResult(res);
      applyResultToCanvas(res.traces || []);
      return res;
    },
    [workflowId, setSnapshot, setCurrentResult, applyResultToCanvas],
  );

  const step = useCallback(async () => {
    if (!snapshot) return undefined;
    const res = await debugWorkflowStep(workflowId, snapshot);
    setSnapshot(res.snapshot);
    setCurrentResult(res);
    applyResultToCanvas(res.traces || []);
    return res;
  }, [snapshot, workflowId, setSnapshot, setCurrentResult, applyResultToCanvas]);

  const testNode = useCallback(
    async (nodeId: string, inputData?: any) => {
      return debugWorkflowNode(workflowId, nodeId, inputData);
    },
    [workflowId],
  );

  const rerunNode = useCallback(
    async (nodeId: string, overrideInput?: any) => {
      if (!snapshot) return undefined;
      const res = await rerunWorkflowNode(workflowId, snapshot, nodeId, overrideInput);
      setSnapshot(res.snapshot);
      setCurrentResult(res);
      applyResultToCanvas(res.traces || []);
      return res;
    },
    [snapshot, workflowId, setSnapshot, setCurrentResult, applyResultToCanvas],
  );

  const loadHistory = useCallback(
    async (page = 1, size = 20) => {
      const res = await getDebugHistory(workflowId, page, size);
      setExecutionHistory(res.list || []);
      return { list: res.list || [], total: res.total || 0 };
    },
    [workflowId, setExecutionHistory],
  );

  const loadHistoryDetail = useCallback(
    async (executionId: string) => {
      return getDebugHistoryDetail(workflowId, executionId);
    },
    [workflowId],
  );

  return {
    execute,
    step,
    testNode,
    rerunNode,
    loadHistory,
    loadHistoryDetail,
    applyResultToCanvas,
  };
}
