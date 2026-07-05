import { create } from 'zustand';
import type { DebugResult, DebugExecutionRecord, NodeTrace } from '@/types/workflow';
import type { MockRule } from '@/types/api';

export interface PinData {
  id: string;
  nodeId: string;
  nodeName: string;
  type: 'input' | 'output';
  data: any;
  createdAt: number;
}

interface DebugState {
  snapshot: any | null;
  executionHistory: DebugExecutionRecord[];
  currentResult: DebugResult | null;
  pinData: PinData[];
  mockConfigs: MockRule[];
  setSnapshot: (snapshot: any) => void;
  setCurrentResult: (result: DebugResult | null) => void;
  setExecutionHistory: (history: DebugExecutionRecord[]) => void;
  loadFromHistory: (record: DebugExecutionRecord) => void;
  updateNodeTraces: (nodeId: string, traces: NodeTrace[]) => void;
  pinTrace: (nodeId: string, nodeName: string, type: 'input' | 'output', data: any) => void;
  unpin: (id: string) => void;
  clearPins: () => void;
  setMockConfigs: (configs: MockRule[]) => void;
}

export const useDebugStore = create<DebugState>((set) => ({
  snapshot: null,
  executionHistory: [],
  currentResult: null,
  pinData: [],
  mockConfigs: [],

  setSnapshot: (snapshot) => set({ snapshot }),
  setCurrentResult: (currentResult) => set({ currentResult }),
  setExecutionHistory: (executionHistory) => set({ executionHistory }),

  loadFromHistory: (record) =>
    set({
      currentResult: {
        status: record.status === 'SUCCESS' ? 'COMPLETED' : 'FAILED',
        traces: record.nodeTraces,
        finalOutput: record.finalOutput,
        totalDurationMs: record.totalDurationMs,
      },
    }),

  updateNodeTraces: (nodeId, traces) =>
    set((state) => {
      if (!state.currentResult) return state;
      const existing = state.currentResult.traces?.filter((t) => t.nodeId !== nodeId) || [];
      return {
        currentResult: {
          ...state.currentResult,
          traces: [...existing, ...traces],
        },
      };
    }),

  pinTrace: (nodeId, nodeName, type, data) =>
    set((state) => {
      const existingIndex = state.pinData.findIndex(
        (p) => p.nodeId === nodeId && p.type === type,
      );
      const newPin: PinData = {
        id: `${nodeId}-${type}-${Date.now()}`,
        nodeId,
        nodeName,
        type,
        data,
        createdAt: Date.now(),
      };
      if (existingIndex >= 0) {
        const next = [...state.pinData];
        next[existingIndex] = newPin;
        return { pinData: next };
      }
      return { pinData: [...state.pinData, newPin] };
    }),

  unpin: (id) =>
    set((state) => ({
      pinData: state.pinData.filter((p) => p.id !== id),
    })),

  clearPins: () => set({ pinData: [] }),
  setMockConfigs: (mockConfigs) => set({ mockConfigs }),
}));
