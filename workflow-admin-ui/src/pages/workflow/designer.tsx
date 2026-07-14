import { history, useParams } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import { Spin, Modal, message } from 'antd';
import { ReactFlowProvider } from 'reactflow';
import { getWorkflow, createWorkflow, updateWorkflow } from '@/services/workflow';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { useClickDebounce } from '@/utils/useClickDebounce';
import { useAutoLayout } from '@/hooks/useAutoLayout';
import type { AutoLayoutOptions } from '@/hooks/useAutoLayout';
import FlowCanvas from './components/FlowCanvas';
import DesignerLeftPanel from './components/DesignerLeftPanel';
import NodeConfigPanel from './components/NodeConfigPanel';
import Toolbar from './components/Toolbar';
import DebugPanel from './components/DebugPanel';
import WorkflowMetaPanel from './components/WorkflowMetaPanel';
import TriggerDrawer from './components/TriggerDrawer';
import ComponentErrorBoundary from '@/components/error-boundaries/ComponentErrorBoundary';
import type { WorkflowDefinition } from '@/types/workflow';

const defaultDefinition: WorkflowDefinition = {
  name: '新建函数集合',
  category: 'BUSINESS',
  inputSchemaFormat: 'json-schema',
  outputSchemaFormat: 'json-schema',
  nodes: [],
  triggers: [],
};

const WorkflowDesigner: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [loading, setLoading] = useState(false);
  const [debugVisible, setDebugVisible] = useState(false);
  const [metaVisible, setMetaVisible] = useState(false);
  const [triggerVisible, setTriggerVisible] = useState(false);
  const [darkMode] = useState(() => localStorage.getItem('fluxion-dark-mode') === 'true');
  const [layoutDirection, setLayoutDirection] = useState<'TB' | 'LR'>('TB');
  const { loadDefinition, toDefinition, workflowMeta, setWorkflowMeta } = useWorkflowStore();
  const { autoLayout } = useAutoLayout();

  const handleLayout = useCallback(
    (dir: 'TB' | 'LR') => {
      setLayoutDirection(dir);
      autoLayout({ direction: dir });
    },
    [autoLayout],
  );

  useEffect(() => {
    if (id) {
      setLoading(true);
      getWorkflow(id)
        .then((def) => loadDefinition(def))
        .finally(() => setLoading(false));
    } else {
      loadDefinition(defaultDefinition);
    }
  }, [id]);

  const handleSave = useClickDebounce(async () => {
    // 保存前自动整理布局（修正歪斜的连接线等）
    autoLayout({ direction: layoutDirection });
    const def = toDefinition();
    const isMeta = def.category === 'META';
    if (isMeta) {
      Modal.confirm({
        title: '⚠️ 正在修改元工作流',
        content: '元工作流驱动管理后台自身，错误配置可能导致后台不可用。是否继续？',
        onOk: async () => {
          await doSave(def);
        },
      });
    } else {
      await doSave(def);
    }
  });

  const doSave = async (def: WorkflowDefinition) => {
    if (id) {
      await updateWorkflow(id, def);
      message.success('保存成功');
      history.push('/workflow');
    } else {
      const created = await createWorkflow(def);
      message.success('创建成功');
      // 跳转到编辑页，后续保存将走 update 路径
      history.replace(`/workflow/designer/${created.id}`);
    }
  };

  return (
    <ReactFlowProvider>
      <div id="workflow-designer-root" className={darkMode ? 'fluxion-dark' : ''} style={{ position: 'fixed', inset: 0, zIndex: 1000, display: 'flex', flexDirection: 'column', height: '100vh', background: darkMode ? '#0f172a' : '#fff' }}>
        {loading && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10, background: darkMode ? 'rgba(15,23,42,0.8)' : 'rgba(255,255,255,0.6)' }}>
            <Spin size="large" />
          </div>
        )}
        <Toolbar
          title={workflowMeta.name || '新建函数集合'}
          onSave={handleSave}
          onDebug={() => setDebugVisible(!debugVisible)}
          debugActive={debugVisible}
          onOpenTrigger={() => setTriggerVisible(true)}
          onOpenMeta={() => setMetaVisible(true)}
          layoutDirection={layoutDirection}
          onLayoutChange={handleLayout}
          darkMode={darkMode}
        />
        <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
          <DesignerLeftPanel darkMode={darkMode} />
          <div style={{ flex: 1, position: 'relative' }}>
            <FlowCanvas darkMode={darkMode} />
          </div>
          <div style={{ height: '100%', flexShrink: 0 }}>
            <NodeConfigPanel workflowId={id} darkMode={darkMode} onOpenMeta={() => setMetaVisible(true)} />
          </div>
        </div>
        {debugVisible && id && <DebugPanel workflowId={id} />}
        <ComponentErrorBoundary title="函数集合配置" onRetry={() => { setMetaVisible(false); setTimeout(() => setMetaVisible(true), 80); }}>
          <WorkflowMetaPanel visible={metaVisible} onClose={() => setMetaVisible(false)} onOpenTrigger={() => { setMetaVisible(false); setTriggerVisible(true); }} />
        </ComponentErrorBoundary>
        <ComponentErrorBoundary title="触发器配置" onRetry={() => { setTriggerVisible(false); setTimeout(() => setTriggerVisible(true), 80); }}>
          <TriggerDrawer visible={triggerVisible} onClose={() => setTriggerVisible(false)} />
        </ComponentErrorBoundary>
      </div>
    </ReactFlowProvider>
  );
};

export default WorkflowDesigner;
