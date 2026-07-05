import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Collapse, List, Modal, Spin, Tag, Timeline, Tabs, message, Switch, Tooltip } from 'antd';
import {
  CaretRightOutlined,
  ReloadOutlined,
  StepForwardOutlined,
  PauseCircleOutlined,
  PushpinOutlined,
  DeleteOutlined,
  HistoryOutlined,
  EditOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
} from '@ant-design/icons';
import JsonEditor from '@/components/JsonEditor';
import { generateSampleFromSchema } from '@/utils/schemaSampleGenerator';
import {
  debugWorkflow,
  debugWorkflowStep,
  debugWorkflowNode,
  rerunWorkflowNode,
  getDebugHistory,
} from '@/services/workflow';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { useDebugStore } from '@/stores/useDebugStore';
import MockServicePanel from './MockServicePanel';
import type { NodeTrace, DebugExecutionRecord } from '@/types/workflow';
import { useClickDebounce } from '@/utils/useClickDebounce';

interface DebugPanelProps {
  workflowId: string;
}

const DebugPanel: React.FC<DebugPanelProps> = ({ workflowId }) => {
  const [inputData, setInputData] = useState<any>({});
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('debug');
  const [editingTrace, setEditingTrace] = useState<NodeTrace | null>(null);
  const [editedInput, setEditedInput] = useState<any>({});
  const [showInlineOutput, setShowInlineOutput] = useState(true);
  const [panelHeight, setPanelHeight] = useState(420);
  const [isDragging, setIsDragging] = useState(false);
  const { snapshot, setSnapshot, currentResult, setCurrentResult, pinData, pinTrace, unpin } =
    useDebugStore();
  const { executionHistory, setExecutionHistory, loadFromHistory, mockConfigs, setMockConfigs } =
    useDebugStore();
  const { nodes, updateNodeData, workflowMeta } = useWorkflowStore();

  const normalizeGeneratedInput = (value: any) => {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      return value;
    }
    return {};
  };

  // 面板打开时，根据工作流 inputSchema 自动生成测试输入
  useEffect(() => {
    const inputSchema = workflowMeta?.inputSchema as Record<string, any> | undefined;
    const sample = normalizeGeneratedInput(generateSampleFromSchema(inputSchema));
    if (Object.keys(sample).length > 0) {
      setInputData(sample);
    }
  }, [workflowMeta?.inputSchema]);

  useEffect(() => {
    loadHistory();
  }, [workflowId]);

  const loadHistory = async () => {
    try {
      const res = await getDebugHistory(workflowId, 1, 20, { silent: true });
      setExecutionHistory(res.list || []);
    } catch (e) {
      // 历史接口可选，失败不阻断
    }
  };

  const updateCanvasNodes = (traces?: NodeTrace[]) => {
    if (!traces) return;
    const traceMap = new Map(traces.map((t) => [t.nodeId, t]));
    const pinnedNodeIds = new Set(pinData.map((p) => p.nodeId));

    nodes.forEach((node) => {
      const trace = traceMap.get(node.id);
      if (trace) {
        updateNodeData(node.id, {
          status: trace.status,
          durationMs: trace.durationMs,
          isValid: trace.status === 'SUCCESS',
          inlineOutput: showInlineOutput ? trace.output : undefined,
          isPinned: pinnedNodeIds.has(node.id),
        });
      }
    });
  };

  const clearCanvasState = () => {
    nodes.forEach((node) => {
      updateNodeData(node.id, {
        status: undefined,
        durationMs: undefined,
        isValid: undefined,
        inlineOutput: undefined,
        isPinned: false,
      });
    });
  };

  const handleExecute = useClickDebounce(async () => {
    setLoading(true);
    try {
      clearCanvasState();
      const payloadInput = inputData && typeof inputData === 'object' && !Array.isArray(inputData) ? inputData : {};
      const res = await debugWorkflow(workflowId, payloadInput, undefined, {
        enabled: true,
        rules: mockConfigs,
      });
      setCurrentResult(res);
      setSnapshot(res.snapshot);
      updateCanvasNodes(res.traces);
      await loadHistory();
    } catch (e: any) {
      console.error('调试执行失败:', e);
    } finally {
      setLoading(false);
    }
  });

  const handleStep = useClickDebounce(async () => {
    if (!snapshot) return;
    setLoading(true);
    try {
      const res = await debugWorkflowStep(workflowId, snapshot);
      setCurrentResult(res);
      setSnapshot(res.snapshot);
      updateCanvasNodes(res.traces);
    } finally {
      setLoading(false);
    }
  });

  const handleRunToBreakpoint = useClickDebounce(async () => {
    if (!snapshot) return;
    setLoading(true);
    try {
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const res = await debugWorkflowStep(workflowId, snapshot);
        setCurrentResult(res);
        setSnapshot(res.snapshot);
        updateCanvasNodes(res.traces);
        if (res.status === 'FAILED' || res.status === 'COMPLETED') break;
        const lastTrace = res.traces?.[res.traces.length - 1];
        const node = nodes.find((n) => n.id === lastTrace?.nodeId);
        if (node?.data?.breakpoint) {
          message.info(`命中断点: ${node.data.label}`);
          break;
        }
      }
    } catch (e: any) {
      console.error('单步执行失败:', e);
    } finally {
      setLoading(false);
    }
  });

  const handleRerunNode = useClickDebounce(async (nodeId: string, newInput?: any) => {
    if (!snapshot) return;
    setLoading(true);
    try {
      const res = await rerunWorkflowNode(workflowId, snapshot, nodeId, newInput);
      setCurrentResult(res);
      setSnapshot(res.snapshot);
      updateCanvasNodes(res.traces);
    } finally {
      setLoading(false);
    }
  });

  const handleLoadHistory = useClickDebounce((record: DebugExecutionRecord) => {
    loadFromHistory(record);
    setInputData(record.input || {});
    clearCanvasState();
    updateCanvasNodes(record.nodeTraces);
    setActiveTab('debug');
  });

  const toggleInlineOutput = () => {
    const newState = !showInlineOutput;
    setShowInlineOutput(newState);
    if (!newState) {
      // 关闭时清除 inline 输出
      nodes.forEach((node) => {
        updateNodeData(node.id, { inlineOutput: undefined });
      });
    } else if (currentResult?.traces) {
      // 开启时重新显示
      updateCanvasNodes(currentResult.traces);
    }
  };

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsDragging(true);
    const startY = e.clientY;
    const startHeight = panelHeight;

    const onMouseMove = (ev: MouseEvent) => {
      const delta = startY - ev.clientY;
      setPanelHeight(Math.min(Math.max(startHeight + delta, 220), window.innerHeight - 120));
    };

    const onMouseUp = () => {
      setIsDragging(false);
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
    document.body.style.cursor = 'ns-resize';
    document.body.style.userSelect = 'none';
  }, [panelHeight]);

  const renderTraceActions = (trace: NodeTrace) => (
    <div style={{ marginTop: 8 }}>
      <Button
        size="small"
        icon={<PushpinOutlined />}
        onClick={() => {
          pinTrace(trace.nodeId!, trace.nodeName!, 'input', trace.inputs?.[trace.nodeId!]);
          updateNodeData(trace.nodeId!, { isPinned: true });
        }}
        style={{ marginRight: 8 }}
      >
        Pin 输入
      </Button>
      <Button
        size="small"
        icon={<PushpinOutlined />}
        onClick={() => {
          pinTrace(trace.nodeId!, trace.nodeName!, 'output', trace.output);
          updateNodeData(trace.nodeId!, { isPinned: true });
        }}
        style={{ marginRight: 8 }}
      >
        Pin 输出
      </Button>
      <Button
        size="small"
        icon={<EditOutlined />}
        onClick={() => {
          setEditingTrace(trace);
          setEditedInput(trace.inputs?.[trace.nodeId!] || {});
        }}
        style={{ marginRight: 8 }}
      >
        编辑输入
      </Button>
      <Button
        size="small"
        icon={<ReloadOutlined />}
        onClick={() => handleRerunNode(trace.nodeId!, trace.inputs?.[trace.nodeId!])}
      >
        重跑此节点
      </Button>
    </div>
  );

  return (
    <Card
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span>调试面板</span>
          <Tooltip title={showInlineOutput ? '隐藏节点内联输出' : '显示节点内联输出'}>
            <Switch
              size="small"
              checked={showInlineOutput}
              onChange={toggleInlineOutput}
              checkedChildren={<EyeOutlined />}
              unCheckedChildren={<EyeInvisibleOutlined />}
            />
          </Tooltip>
        </div>
      }
      size="small"
      style={{ height: panelHeight, overflow: 'auto', position: 'relative', flexShrink: 0 }}
    >
      {/* 拖拽手柄 */}
      <div
        onMouseDown={handleMouseDown}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 6,
          cursor: 'ns-resize',
          zIndex: 10,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div style={{ width: 48, height: 4, background: '#d9d9d9', borderRadius: 2 }} />
      </div>
      {/* 拖拽时遮罩，防止 Monaco/textarea 捕获鼠标事件 */}
      {isDragging && (
        <div style={{ position: 'absolute', inset: 0, zIndex: 9, cursor: 'ns-resize' }} />
      )}
      <Spin spinning={loading}>
        <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
          {
            key: 'debug',
            label: '输入与执行',
            children: (
              <div style={{ display: 'flex', gap: 16 }}>
                <div style={{ width: 320, display: 'flex', flexDirection: 'column', height: panelHeight - 145 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6, flexShrink: 0 }}>
                    <span style={{ fontWeight: 500, fontSize: 13 }}>测试输入</span>
                    <Button
                      size="small"
                      icon={<ReloadOutlined />}
                      onClick={() => {
                        const inputSchema = workflowMeta?.inputSchema as Record<string, any> | undefined;
                        const sample = generateSampleFromSchema(inputSchema);
                        setInputData(Object.keys(sample).length > 0 ? sample : {});
                        message.success('已根据 inputSchema 重新生成测试数据');
                      }}
                    >
                      重新生成
                    </Button>
                  </div>
                  <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
                    <JsonEditor value={inputData} onChange={setInputData} height="100%" />
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 10, flexShrink: 0 }}>
                    <Button
                      type="primary"
                      icon={<CaretRightOutlined />}
                      onClick={handleExecute}
                    >
                      执行调试
                    </Button>
                    {snapshot && (
                      <Button icon={<StepForwardOutlined />} onClick={handleStep}>
                        下一步
                      </Button>
                    )}
                    {snapshot && (
                      <Button icon={<PauseCircleOutlined />} onClick={handleRunToBreakpoint}>
                        运行到断点
                      </Button>
                    )}
                  </div>
                </div>
                <div style={{ flex: 1 }}>
                  {currentResult && (
                    <>
                      <div style={{ marginBottom: 10 }}>
                        <Tag color={currentResult.status === 'COMPLETED' ? 'success' : 'error'}>
                          {currentResult.status}
                        </Tag>
                        <span>总耗时: {currentResult.totalDurationMs}ms</span>
                      </div>
                      <Timeline>
                        {currentResult.traces?.map((trace) => (
                          <Timeline.Item
                            key={trace.nodeId}
                            color={
                              trace.status === 'SUCCESS'
                                ? 'green'
                                : trace.status === 'FAILED'
                                  ? 'red'
                                  : 'gray'
                            }
                          >
                            <Collapse ghost>
                              <Collapse.Panel
                                header={
                                  <span>
                                    {trace.nodeName} <Tag>{trace.durationMs}ms</Tag>{' '}
                                    <Tag
                                      color={trace.status === 'SUCCESS' ? 'success' : 'error'}
                                    >
                                      {trace.status}
                                    </Tag>
                                  </span>
                                }
                                key="1"
                              >
                                <div style={{ fontWeight: 500, marginBottom: 4 }}>输入</div>
                                <JsonEditor value={trace.inputs?.[trace.nodeId!]} readOnly height={120} />
                                <div style={{ fontWeight: 500, marginTop: 8, marginBottom: 4 }}>输出</div>
                                <JsonEditor value={trace.output} readOnly height={120} />
                                {trace.error && <Tag color="error">{trace.error}</Tag>}
                                {renderTraceActions(trace)}
                              </Collapse.Panel>
                            </Collapse>
                          </Timeline.Item>
                        ))}
                      </Timeline>
                      <Card title="最终输出" size="small">
                        <JsonEditor value={currentResult.finalOutput} readOnly height={160} />
                      </Card>
                    </>
                  )}
                </div>
              </div>
            ),
          },
          {
            key: 'history',
            label: <span><HistoryOutlined /> 执行历史</span>,
            children: (
              <List
                size="small"
                dataSource={executionHistory}
                renderItem={(record) => (
                  <List.Item
                    actions={[
                      <Button size="small" onClick={() => handleLoadHistory(record)}>
                        加载
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <span>
                          <Tag color={record.status === 'SUCCESS' ? 'success' : 'error'}>
                            {record.status}
                          </Tag>
                          {record.totalDurationMs}ms
                        </span>
                      }
                      description={new Date(record.timestamp || 0).toLocaleString()}
                    />
                  </List.Item>
                )}
              />
            ),
          },
          {
            key: 'pins',
            label: <span><PushpinOutlined /> Pin Data ({pinData.length})</span>,
            children: (
              <List
                size="small"
                dataSource={pinData}
                locale={{ emptyText: '暂无 Pin 数据。调试时在节点输出中点击 "Pin" 按钮来固定数据。' }}
                renderItem={(pin) => (
                  <List.Item
                    actions={[
                      <Button
                        size="small"
                        icon={<ReloadOutlined />}
                        onClick={() => handleRerunNode(pin.nodeId, pin.data)}
                      >
                        用于重跑
                      </Button>,
                      <Button
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => {
                          unpin(pin.id);
                          const remainingPins = pinData.filter((p) => p.id !== pin.id);
                          const hasOtherPin = remainingPins.some((p) => p.nodeId === pin.nodeId);
                          if (!hasOtherPin) {
                            updateNodeData(pin.nodeId, { isPinned: false });
                          }
                        }}
                      >
                        取消
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={`${pin.nodeName} / ${pin.type === 'input' ? '输入' : '输出'}`}
                      description={new Date(pin.createdAt).toLocaleString()}
                    />
                  </List.Item>
                )}
              />
            ),
          },
          {
            key: 'mock',
            label: 'Mock 服务',
            children: <MockServicePanel configs={mockConfigs} onChange={setMockConfigs} />,
          },
        ]} />
        <Modal
          title={`编辑节点输入: ${editingTrace?.nodeName}`}
          open={!!editingTrace}
          onCancel={() => setEditingTrace(null)}
          onOk={() => {
            if (editingTrace) {
              handleRerunNode(editingTrace.nodeId!, editedInput);
              setEditingTrace(null);
            }
          }}
          width={560}
        >
          <JsonEditor value={editedInput} onChange={setEditedInput} height={300} />
        </Modal>
      </Spin>
    </Card>
  );
};

export default DebugPanel;
