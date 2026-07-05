import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { Button, Card, Form, Input, InputNumber, Select, Switch, Tabs, Badge, Empty, Modal, Tooltip } from 'antd';
import { DeleteOutlined, PlayCircleOutlined, SettingOutlined, CodeOutlined, DatabaseOutlined, SafetyCertificateOutlined, ThunderboltOutlined, WarningOutlined, ClockCircleOutlined, CommentOutlined } from '@ant-design/icons';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { ERROR_STRATEGIES } from '@/constants/errorStrategies';
import { NODE_TYPE_OPTIONS } from '@/constants/nodeTypes';
import { getFunctions } from '@/services/function';
import type { FunctionDefinition } from '@/types/function';
import { flattenSchemaFields } from '@/utils/schemaFields';
import DecoratorConfigPanel from './DecoratorConfigPanel';
import FunctionRefSelect from './FunctionRefSelect';
import NodeParamForm from './NodeParamForm';
import NodeTestModal from './NodeTestModal';
import Editor from '@monaco-editor/react';

interface NodeConfigPanelProps {
  workflowId?: string;
  darkMode?: boolean;
}

/** 面板宽度约束 */
const DEFAULT_WIDTH = 440;
const MIN_WIDTH = 360;
const MAX_WIDTH = 800;
/** 收起时宽度 */
const COLLAPSED_WIDTH = 48;

const COMPACT_ITEM_MB = { marginBottom: 12 };

const SectionTitle: React.FC<{ icon: React.ReactNode; title: string }> = ({ icon, title }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 6, margin: '4px 0 10px', color: 'var(--fluxion-text-secondary)' }}>
    <span style={{ fontSize: 12 }}>{icon}</span>
    <span style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>{title}</span>
    <div style={{ flex: 1, height: 1, background: 'var(--fluxion-border)' }} />
  </div>
);

const NodeConfigPanel: React.FC<NodeConfigPanelProps> = ({ workflowId, darkMode }) => {
  const { selectedNodeId, nodes, edges, workflowMeta, updateNodeData, removeNode } = useWorkflowStore();
  const node = nodes.find((n) => n.id === selectedNodeId);
  const [testVisible, setTestVisible] = useState(false);

  // ─── 悬浮展开/收起 ─────────────────────────────────────
  // hovered 仅用于"未选节点"时的悬浮预览交互
  // 有节点选中时，面板始终展开，不受 hover 影响
  const [hovered, setHovered] = useState(false);

  // 有节点选中 → 强制展开；无节点 → 由 hovered 控制
  const expanded = !!node || hovered;

  // ─── 可拖拽宽度 ─────────────────────────────────────────
  const [panelWidth, setPanelWidth] = useState(DEFAULT_WIDTH);
  const draggingRef = useRef(false);
  const startXRef = useRef(0);
  const startWidthRef = useRef(DEFAULT_WIDTH);

  const onMouseMove = useCallback((e: MouseEvent) => {
    if (!draggingRef.current) return;
    const delta = startXRef.current - e.clientX;
    const newWidth = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidthRef.current + delta));
    setPanelWidth(newWidth);
  }, []);

  const onMouseUp = useCallback(() => {
    draggingRef.current = false;
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
  }, []);

  useEffect(() => {
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
    return () => {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };
  }, [onMouseMove, onMouseUp]);

  const handleDragStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    draggingRef.current = true;
    startXRef.current = e.clientX;
    startWidthRef.current = panelWidth;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, [panelWidth]);

  // 拖拽中不触发收起（防止拖拽时鼠标移出导致面板收缩）
  const handleMouseLeave = useCallback(() => {
    if (!draggingRef.current) setHovered(false);
  }, []);

  // 函数元信息缓存
  const [funcMeta, setFuncMeta] = useState<{
    paramSchema?: Record<string, any>;
    outputSchema?: Record<string, any>;
  } | null>(null);
  const funcCacheRef = useRef<Map<string, { paramSchema?: Record<string, any>; outputSchema?: Record<string, any>; nodeType?: string; category?: string }>>(new Map());

  useEffect(() => {
    if (!node?.data.functionRef) {
      setFuncMeta(null);
      return;
    }
    const ref = node.data.functionRef;
    if (funcCacheRef.current.has(ref)) {
      setFuncMeta(funcCacheRef.current.get(ref)!);
      return;
    }
    setFuncMeta(null);
    getFunctions({ page: 0, pageSize: 1000 }, { silent: true })
      .then((res) => {
        const list = res.list || [];
        list.forEach((fn: FunctionDefinition) => {
          if (!funcCacheRef.current.has(fn.name)) {
            funcCacheRef.current.set(fn.name, {
              paramSchema: fn.config?.paramSchema as Record<string, any>,
              outputSchema: fn.config?.outputSchema as Record<string, any>,
              nodeType: fn.nodeType,
              category: fn.category,
            });
          }
        });
        setFuncMeta(funcCacheRef.current.get(ref) || null);
      })
      .catch(() => {});
  }, [node?.data.functionRef]);

  const isBuiltinFunction = funcCacheRef.current.get(node?.data.functionRef || '')?.category === 'BUILTIN';

  // functionRef → nodeType 自动联动
  const prevFuncRef = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (!node?.data.functionRef || !selectedNodeId) return;
    const ref = node.data.functionRef;
    if (prevFuncRef.current === ref) return;
    prevFuncRef.current = ref;
    const cached = funcCacheRef.current.get(ref);
    if (cached?.nodeType && cached.nodeType !== node.data.type) {
      updateNodeData(selectedNodeId, { type: cached.nodeType });
    }
  }, [node?.data.functionRef, selectedNodeId]);

  useEffect(() => {
    if (!funcMeta?.nodeType || !selectedNodeId || !node) return;
    if (!isBuiltinFunction) return;
    if (funcMeta.nodeType !== node.data.type) {
      updateNodeData(selectedNodeId, { type: funcMeta.nodeType });
    }
  }, [funcMeta?.nodeType, selectedNodeId, isBuiltinFunction]);

  // 上游字段自动补全
  const upstreamFields = useMemo(() => {
    if (!selectedNodeId) return [];
    const parentIds = edges.filter((e) => e.target === selectedNodeId).map((e) => e.source);
    const fields: string[] = [];
    if (parentIds.length > 0) {
      for (const pid of parentIds) {
        const parentNode = nodes.find((n) => n.id === pid);
        if (!parentNode?.data.functionRef) continue;
        const meta = funcCacheRef.current.get(parentNode.data.functionRef);
        if (meta?.outputSchema?.properties) {
          flattenSchemaFields(meta.outputSchema.properties, '', fields);
        }
      }
    } else {
      const inputSchema = workflowMeta.inputSchema as Record<string, any> | undefined;
      if (inputSchema?.properties) {
        flattenSchemaFields(inputSchema.properties, '', fields);
      }
    }
    return [...new Set(fields)];
  }, [selectedNodeId, edges, nodes, workflowMeta, funcMeta]);

  const inputSchemaFields = useMemo(() => {
    const inputSchema = workflowMeta.inputSchema as Record<string, any> | undefined;
    if (!inputSchema?.properties) return [];
    const fields: string[] = [];
    flattenSchemaFields(inputSchema.properties, '', fields);
    return [...new Set(fields)];
  }, [workflowMeta]);

  // ─── 未选节点时（空状态） ───────────────────────────────
  if (!node) {
    return (
      <div
        className="fluxion-designer-config-panel"
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={handleMouseLeave}
        style={{
          display: 'flex',
          height: '100%',
          width: expanded ? COLLAPSED_WIDTH + DEFAULT_WIDTH : COLLAPSED_WIDTH,
          transition: 'width 0.2s ease',
          overflow: 'hidden',
          position: 'relative',
          zIndex: 10,
        }}
      >
        {/* 展开内容 */}
        <div style={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: DEFAULT_WIDTH,
          opacity: expanded ? 1 : 0,
          pointerEvents: expanded ? 'auto' : 'none',
          transition: 'opacity 0.15s ease',
          display: 'flex',
        }}>
          <div
            onMouseDown={handleDragStart}
            style={{ width: 5, cursor: 'col-resize', background: 'transparent', flexShrink: 0, position: 'relative' }}
          >
            <div style={{ position: 'absolute', left: 2, top: 0, bottom: 0, width: 1, background: 'var(--fluxion-border)' }} />
          </div>
          <Card
            title="工作流配置"
            style={{ width: DEFAULT_WIDTH, borderRadius: 0, borderTop: 'none', borderBottom: 'none', height: '100%' }}
          >
            <Empty description="选择一个节点或配置工作流元信息" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          </Card>
        </div>

        {/* 收起时的图标条 */}
        <div style={{
          width: COLLAPSED_WIDTH,
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderLeft: '1px solid var(--fluxion-border)',
          background: 'var(--fluxion-bg)',
          flexShrink: 0,
        }}>
          <SettingOutlined style={{ color: expanded ? 'var(--fluxion-primary)' : 'var(--fluxion-text-muted)', fontSize: 22, transition: 'color 0.2s' }} />
        </div>
      </div>
    );
  }

  // ─── 已选节点 ───────────────────────────────────────────
  return (
    <div
      className="fluxion-designer-config-panel"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={handleMouseLeave}
      style={{
        display: 'flex',
        height: '100%',
        width: expanded ? COLLAPSED_WIDTH + panelWidth : COLLAPSED_WIDTH,
        transition: 'width 0.2s ease',
        overflow: 'hidden',
        position: 'relative',
        zIndex: 10,
      }}
    >
      {/* 展开内容 */}
      <div style={{
        position: 'absolute',
        left: 0,
        top: 0,
        bottom: 0,
        width: panelWidth,
        opacity: expanded ? 1 : 0,
        pointerEvents: expanded ? 'auto' : 'none',
        transition: 'opacity 0.15s ease',
        display: 'flex',
      }}>
        {/* 拖拽手柄 */}
        <div
          onMouseDown={handleDragStart}
          style={{
            width: 5,
            cursor: 'col-resize',
            background: 'transparent',
            flexShrink: 0,
            position: 'relative',
          }}
        >
          <div style={{ position: 'absolute', left: 2, top: 0, bottom: 0, width: 1, background: 'var(--fluxion-border)' }} />
        </div>

        <Card
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <DatabaseOutlined />
              <span style={{ fontWeight: 600 }}>{node.data.label}</span>
            </div>
          }
          style={{ width: panelWidth, borderRadius: 0, borderTop: 'none', borderBottom: 'none', height: '100%', flexShrink: 0 }}
          styles={{ body: { padding: 0, height: 'calc(100% - 57px)', overflow: 'hidden' } }}
          extra={
            <>
              {workflowId && (
                <Button
                  icon={<PlayCircleOutlined />}
                  size="small"
                  style={{ marginRight: 8 }}
                  onClick={() => setTestVisible(true)}
                >
                  测试
                </Button>
              )}
              <Button
                icon={<DeleteOutlined />}
                danger
                size="small"
                onClick={() => {
                  Modal.confirm({
                    title: '确认删除节点',
                    content: `将删除节点「${node.data.label}」及其所有连线，此操作不可撤销。`,
                    okText: '删除',
                    okButtonProps: { danger: true },
                    cancelText: '取消',
                    onOk: () => removeNode(node.id),
                  });
                }}
              >
                删除
              </Button>
            </>
          }
        >
          <style>{`
            .node-config-tabs.ant-tabs-left > .ant-tabs-nav {
              min-width: 56px;
              border-right: 1px solid var(--fluxion-border);
            }
            .node-config-tabs.ant-tabs-left > .ant-tabs-nav .ant-tabs-tab {
              padding: 10px 12px;
              margin: 0;
              font-size: 12px;
            }
            .node-config-tabs.ant-tabs-left > .ant-tabs-content-holder {
              flex: 1;
              min-width: 0;
              overflow-y: auto;
            }
            .node-config-tabs.ant-tabs-left > .ant-tabs-content-holder > .ant-tabs-content {
              min-height: 100%;
            }
            .node-config-tabs.ant-tabs-left > .ant-tabs-content-holder > .ant-tabs-content > .ant-tabs-tabpane-active {
              padding: 0 4px;
            }
          `}</style>
          <Tabs
            defaultActiveKey="basic"
            tabPosition="left"
            className="node-config-tabs"
            style={{ height: '100%' }}
            items={[
              {
                key: 'basic',
                label: '基础',
                children: (
                  <Form layout="vertical" style={{ padding: '4px 4px 12px' }}>
                    <SectionTitle icon={<SettingOutlined />} title="基础设置" />
                    <Form.Item label="节点名称" style={COMPACT_ITEM_MB}>
                      <Input
                        value={node.data.label}
                        onChange={(e) => updateNodeData(node.id, { label: e.target.value })}
                        placeholder="节点显示名称"
                      />
                    </Form.Item>
                    <Form.Item label="节点类型" style={COMPACT_ITEM_MB}>
                      <Select
                        value={node.data.type}
                        options={NODE_TYPE_OPTIONS.map((t) => ({ value: t.value, label: `${t.icon} ${t.label}` }))}
                        onChange={(v) => updateNodeData(node.id, { type: v })}
                        disabled={isBuiltinFunction}
                        title={isBuiltinFunction ? '内置函数节点类型由函数定义决定，不可手动修改' : undefined}
                      />
                    </Form.Item>
                    <Form.Item label="函数引用" style={COMPACT_ITEM_MB}>
                      <Tooltip title={isBuiltinFunction ? '内置函数引用由后端代码定义，不可修改' : (node.data.functionRef || undefined)} placement="topLeft">
                        <FunctionRefSelect
                          value={node.data.functionRef}
                          onChange={(v) => updateNodeData(node.id, { functionRef: v })}
                          placeholder="如 builtin:paramValidate 或 rpc:userService.exists"
                          disabled={isBuiltinFunction}
                        />
                      </Tooltip>
                    </Form.Item>

                    <SectionTitle icon={<CommentOutlined />} title="高级设置" />
                    <Form.Item label="备注" style={COMPACT_ITEM_MB} tooltip="节点备注，画布上显示">
                      <Input.TextArea
                        value={node.data.remark || ''}
                        onChange={(e) => updateNodeData(node.id, { remark: e.target.value })}
                        placeholder="节点备注说明"
                        autoSize={{ minRows: 2, maxRows: 4 }}
                      />
                    </Form.Item>
                    <Form.Item label="异步执行" style={COMPACT_ITEM_MB} tooltip="开启后节点不阻塞后续节点执行，异常不中止流程">
                      <Switch
                        checked={!!node.data.asyncExecution}
                        onChange={(v) => updateNodeData(node.id, { asyncExecution: v })}
                        checkedChildren="异步"
                        unCheckedChildren="同步"
                      />
                    </Form.Item>

                    <SectionTitle icon={<ThunderboltOutlined />} title="执行控制" />
                    <Form.Item label="超时时间" style={COMPACT_ITEM_MB}>
                      <InputNumber
                        value={node.data.timeoutMs}
                        onChange={(v) => updateNodeData(node.id, { timeoutMs: v })}
                        style={{ width: '100%' }}
                        min={0}
                        step={100}
                        placeholder="默认无超时"
                        addonAfter="ms"
                      />
                    </Form.Item>
                    <Form.Item label="断点调试" style={COMPACT_ITEM_MB} tooltip="开启后调试执行将在此节点暂停">
                      <Switch
                        checked={!!node.data.breakpoint}
                        onChange={(v) => updateNodeData(node.id, { breakpoint: v })}
                        checkedChildren="已开启"
                        unCheckedChildren="已关闭"
                      />
                    </Form.Item>

                    <SectionTitle icon={<WarningOutlined />} title="错误处理" />
                    <Form.Item label="错误策略" style={COMPACT_ITEM_MB} tooltip="节点执行失败时的处理方式">
                      <Select
                        value={node.data.errorStrategy}
                        allowClear
                        placeholder="默认继续执行"
                        options={ERROR_STRATEGIES.map((s) => ({ value: s.value, label: s.label }))}
                        onChange={(v) => updateNodeData(node.id, { errorStrategy: v })}
                      />
                    </Form.Item>
                  </Form>
                ),
              },
              {
                key: 'params',
                label: '参数',
                children: (
                  <div style={{ padding: '4px 4px 12px' }}>
                    <NodeParamForm
                      nodeType={node.data.type}
                      functionRef={node.data.functionRef}
                      paramSchema={funcMeta?.paramSchema}
                      outputSchema={funcMeta?.outputSchema}
                      upstreamFields={upstreamFields}
                      inputSchemaFields={inputSchemaFields}
                      value={node.data.params || {}}
                      onChange={(v) => updateNodeData(node.id, { params: v })}
                    />
                  </div>
                ),
              },
              {
                key: 'rawParams',
                label: '原始 JSON',
                children: (
                  <div style={{ padding: '4px 4px 12px' }}>
                    <Editor
                      height={320}
                      language="json"
                      value={JSON.stringify(node.data.params || {}, null, 2)}
                      onChange={(v) => {
                        try {
                          updateNodeData(node.id, { params: JSON.parse(v || '{}') });
                        } catch (e) {}
                      }}
                      options={{ minimap: { enabled: false } }}
                    />
                  </div>
                ),
              },
              {
                key: 'decorators',
                label: (
                  <span>
                    装饰器
                    {node.data.decorators && node.data.decorators.length > 0 && (
                      <Badge count={node.data.decorators.length} style={{ marginLeft: 4 }} />
                    )}
                  </span>
                ),
                children: (
                  <div style={{ padding: '4px 4px 12px' }}>
                    <DecoratorConfigPanel
                      nodeId={node.id}
                      decorators={node.data.decorators || []}
                      decoratorParams={node.data.decoratorParams || {}}
                      onChange={(decorators, decoratorParams) =>
                        updateNodeData(node.id, { decorators, decoratorParams })
                      }
                    />
                  </div>
                ),
              },
            ]}
          />
        </Card>
      </div>

      {/* 收起时的图标条（始终可见，位于最右侧） */}
      <div style={{
        width: COLLAPSED_WIDTH,
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderLeft: '1px solid var(--fluxion-border)',
        background: 'var(--fluxion-bg)',
        flexShrink: 0,
      }}>
        <SettingOutlined style={{ color: expanded ? 'var(--fluxion-primary)' : 'var(--fluxion-text-muted)', fontSize: 22, transition: 'color 0.2s' }} />
      </div>

      {workflowId && (
        <NodeTestModal
          workflowId={workflowId}
          node={node}
          paramSchema={funcMeta?.paramSchema}
          visible={testVisible}
          onClose={() => setTestVisible(false)}
        />
      )}
    </div>
  );
};

export default NodeConfigPanel;
