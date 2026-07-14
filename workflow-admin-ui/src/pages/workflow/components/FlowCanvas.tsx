import { useCallback, useEffect, useRef, useState } from 'react';
import ReactFlow, {
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  Panel,
  ReactFlowProvider,
  useReactFlow,
  MarkerType,
  type ReactFlowInstance,
} from 'reactflow';
import { Tooltip, Typography, App } from 'antd';
import { FullscreenOutlined, FullscreenExitOutlined, PlusCircleOutlined } from '@ant-design/icons';
import 'reactflow/dist/style.css';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { useKeyboardShortcuts } from '@/hooks/useKeyboardShortcuts';
import { nodeTypes } from './nodes';
import { edgeTypes } from './edges';
import EdgeConfigPanel from './EdgeConfigPanel';
import type { NodeType } from '@/types/workflow';
import { BUILTIN_TRIGGER_METAS_FALLBACK } from '@/services/triggerFunctions';

const { Text } = Typography;

const FlowCanvasInner: React.FC<{ darkMode?: boolean }> = ({ darkMode }) => {
  const { message } = App.useApp();
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { project } = useReactFlow();
  const [isDragOver, setIsDragOver] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);

  // 监听全屏状态变化
  useEffect(() => {
    const onFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

  const toggleFullscreen = useCallback(() => {
    const el = document.getElementById('workflow-designer-root');
    if (!el) return;
    if (!document.fullscreenElement) {
      el.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  }, []);
  const {
    nodes,
    edges,
    onNodesChange,
    onEdgesChange,
    onConnect,
    addNode,
    setSelectedNode,
    setSelectedEdge,
    selectedEdgeId,
    hasAnyTrigger,
    registerUniqueTrigger,
  } = useWorkflowStore();

  useKeyboardShortcuts();

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
    if (!isDragOver) setIsDragOver(true);
  }, [isDragOver]);

  const onDragLeave = useCallback((event: React.DragEvent) => {
    // 只在离开画布区域时触发
    const rect = reactFlowWrapper.current?.getBoundingClientRect();
    if (!rect) return;
    const { clientX, clientY } = event;
    if (clientX < rect.left || clientX > rect.right || clientY < rect.top || clientY > rect.bottom) {
      setIsDragOver(false);
    }
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      setIsDragOver(false);
      const raw = event.dataTransfer.getData('application/reactflow');
      if (!raw || !reactFlowWrapper.current) return;

      let payload: { nodeType: NodeType; functionRef: string; label: string } | null = null;
      try {
        payload = JSON.parse(raw);
      } catch {
        payload = { nodeType: raw as NodeType, functionRef: '', label: '' };
      }
      if (!payload || !payload.nodeType) return;

      // ── B3：触发器节点（拖进来自动注册为唯一头触发器 + 最多1个） ──────────────
      const functionRef = (payload.functionRef || '').toString().trim();
      const isTriggerDrop =
        functionRef.startsWith('trigger:') ||
        (payload.nodeType as string).toUpperCase().startsWith('TRIGGER');

      if (isTriggerDrop) {
        if (hasAnyTrigger()) {
          message.warning(
            '一个流程最多 1 个触发器，且必须是头节点。请先在「触发器配置」中删除现有触发器后再添加。',
            5,
          );
          return; // 拒绝添加节点，避免画布有两个触发器
        }
        // 查兜底 meta 拿默认值（后端 meta 已经在 Toolbar 拉过，这里用兜底最稳妥）
        const meta = BUILTIN_TRIGGER_METAS_FALLBACK.find((m) => m.functionRef === functionRef);
        const defaultCfg = meta
          ? Object.fromEntries(
              (meta.paramSchema ?? [])
                .filter((p: any) => p.defaultValue !== undefined && p.defaultValue !== null)
                .map((p: any) => [p.name, p.defaultValue]),
            )
          : {};
        const trigType: 'API' | 'EVENT' | 'SCHEDULE' | 'MANUAL' =
          (meta?.category as any) || 'API';
        const result = registerUniqueTrigger({
          functionRef,
          triggerType: trigType,
          config: defaultCfg,
        });
        if (!result.ok) {
          message.warning(result.msg || '添加触发器失败', 5);
          return;
        }
        // 触发器节点位置：强制画布最上中央（头节点位置），不跟随鼠标，避免用户拖到中间
        const reactFlowBounds = reactFlowWrapper.current.getBoundingClientRect();
        const centerX = Math.max(0, (reactFlowBounds.width || 800) / 2 - 100);
        const triggerPosition = project({ x: centerX, y: 60 });
        addNode(payload.nodeType, triggerPosition, {
          functionRef,
          label: payload.label || meta?.label || functionRef,
          params: defaultCfg,
          _isHeadTrigger: true,
        });
        message.success(
          `触发器已绑定：${meta?.label || functionRef}（一个流程仅此 1 个，已自动设为头节点）`,
          3,
        );
        return;
      }

      const reactFlowBounds = reactFlowWrapper.current.getBoundingClientRect();
      const position = project({
        x: event.clientX - reactFlowBounds.left,
        y: event.clientY - reactFlowBounds.top,
      });

      // 处理 StickyNote 类型
      if (payload.nodeType === 'STICKY_NOTE' as any) {
        const id = `note${Date.now()}`;
        useWorkflowStore.setState((state) => ({
          nodes: [
            ...state.nodes,
            {
              id,
              type: 'stickyNote',
              position,
              data: { label: '备注', type: 'STICKY_NOTE', text: '' },
            },
          ],
        }));
        return;
      }

      addNode(payload.nodeType, position, {
        functionRef: payload.functionRef,
        label: payload.label || payload.functionRef,
      });
    },
    [project, addNode, hasAnyTrigger, registerUniqueTrigger, message],
  );

  const isEmpty = nodes.length === 0;

  return (
    <div ref={reactFlowWrapper} className="fluxion-designer-canvas" style={{ width: '100%', height: '100%', background: darkMode ? '#0f172a' : '#f8fafc', position: 'relative' }}>
      {/* 拖拽放置引导层 */}
      {isDragOver && (
        <div style={{
          position: 'absolute',
          inset: 0,
          border: '2px dashed #3b82f6',
          borderRadius: 8,
          background: 'rgba(59, 130, 246, 0.04)',
          zIndex: 20,
          pointerEvents: 'none',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}>
          <div style={{
            background: 'rgba(59, 130, 246, 0.9)',
            color: '#fff',
            padding: '8px 20px',
            borderRadius: 20,
            fontSize: 13,
            fontWeight: 500,
            boxShadow: '0 4px 12px rgba(59, 130, 246, 0.3)',
          }}>
            + 释放以放置节点
          </div>
        </div>
      )}
      {/* 空态引导 */}
      {isEmpty && (
        <div style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          pointerEvents: 'none',
          zIndex: 10,
        }}>
          <div style={{
            width: 80,
            height: 80,
            borderRadius: 20,
            background: 'linear-gradient(135deg, #eff6ff, #dbeafe)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 20,
          }}>
            <PlusCircleOutlined style={{ fontSize: 36, color: '#3b82f6' }} />
          </div>
          <Text style={{ fontSize: 16, fontWeight: 600, color: '#334155' }}>
            拖拽左侧函数到此处构建流程
          </Text>
          <Text type="secondary" style={{ fontSize: 13, marginTop: 8, color: '#94a3b8' }}>
            支持拖放节点或使用快捷键 N 快速添加
          </Text>
        </div>
      )}
      <style>{`
        /* MiniMap 样式美化 */
        .react-flow__minimap {
          border-radius: 12px !important;
          border: 1px solid #e2e8f0 !important;
          box-shadow: 0 4px 12px rgba(0,0,0,0.08) !important;
          background: #fff !important;
          overflow: hidden !important;
        }
        
        /* Controls 样式美化 */
        .react-flow__controls {
          border-radius: 10px !important;
          border: 1px solid #e2e8f0 !important;
          box-shadow: 0 4px 12px rgba(0,0,0,0.08) !important;
          overflow: hidden !important;
        }
        .react-flow__controls-button {
          border: none !important;
          border-bottom: 1px solid #f1f5f9 !important;
          background: #fff !important;
          width: 28px !important;
          height: 28px !important;
        }
        .react-flow__controls-button:hover {
          background: #f8fafc !important;
        }
        .react-flow__controls-button svg {
          fill: #64748b !important;
        }
        
        /* Attribution 隐藏 */
        .react-flow__attribution {
          display: none !important;
        }
        
        /* 选中框样式 */
        .react-flow__selection {
          background: rgba(59, 130, 246, 0.08) !important;
          border: 1px dashed #3b82f6 !important;
          border-radius: 4px !important;
        }
      `}</style>
      
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onNodeClick={(_, node) => setSelectedNode(node.id)}
        onEdgeClick={(_, edge) => setSelectedEdge(edge.id)}
        onPaneClick={() => {
          setSelectedNode(null);
          setSelectedEdge(null);
        }}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        defaultEdgeOptions={{
          type: 'smoothstep',
          style: { 
            stroke: '#94a3b8', 
            strokeWidth: 1.5,
          },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color: '#94a3b8',
            width: 12,
            height: 12,
          },
          animated: false,
        }}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        multiSelectionKeyCode="Shift"
        selectionKeyCode="Shift"
        selectNodesOnDrag
        proOptions={{ hideAttribution: true }}
      >
        <Background
          variant={BackgroundVariant.Dots}
          gap={24}
          size={1}
          color={darkMode ? '#334155' : '#e2e8f0'}
        />
        <Controls 
          position="bottom-right"
          showInteractive={false}
          showFitView={false}
        />
        {/* 自定义全屏按钮 */}
        <Panel position="bottom-right" style={{ marginBottom: 104 }}>
          <Tooltip title={isFullscreen ? '退出全屏' : '全屏'} placement="left">
            <button
              onClick={toggleFullscreen}
              style={{
                width: 28,
                height: 28,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: 'none',
                background: '#fff',
                cursor: 'pointer',
                borderRadius: 6,
                boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                color: '#64748b',
                fontSize: 14,
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => { (e.target as HTMLElement).style.background = '#f8fafc'; }}
              onMouseLeave={(e) => { (e.target as HTMLElement).style.background = '#fff'; }}
            >
              {isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
            </button>
          </Tooltip>
        </Panel>
        <MiniMap
          position="bottom-left"
          nodeColor={(node) => {
            // 根据节点类型着色
            const type = node.data?.type;
            if (node.data?.status === 'SUCCESS') return '#10b981';
            if (node.data?.status === 'FAILED') return '#ef4444';
            if (node.data?.status === 'RUNNING') return '#3b82f6';
            return '#94a3b8';
          }}
          maskColor="rgba(248, 250, 252, 0.8)"
          pannable
          zoomable
        />
      </ReactFlow>
      {selectedEdgeId && (
        <div style={{ position: 'absolute', top: 16, right: 16, zIndex: 5 }}>
          <EdgeConfigPanel edgeId={selectedEdgeId} darkMode={darkMode} />
        </div>
      )}
    </div>
  );
};

const FlowCanvas: React.FC<{ darkMode?: boolean }> = ({ darkMode }) => {
  return (
    <ReactFlowProvider>
      <FlowCanvasInner darkMode={darkMode} />
    </ReactFlowProvider>
  );
};

export default FlowCanvas;
