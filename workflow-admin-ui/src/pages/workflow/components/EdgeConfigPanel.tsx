import { useEffect, useMemo, useRef, useState } from 'react';
import { AutoComplete, Button, Card, Input, Radio, Select, Space, Switch, Typography } from 'antd';
import { useClickDebounce } from '@/utils/useClickDebounce';
import { DeleteOutlined, CloseOutlined, PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { getFunctions } from '@/services/function';
import type { FunctionDefinition } from '@/types/function';
import { flattenSchemaFields } from '@/utils/schemaFields';
import type { ConditionalEdgeData } from '@/utils/flowConverter';
import { formatConditionalLabel } from '@/utils/flowConverter';

const { Text } = Typography;

/**
 * 可用操作符列表。
 *
 * 反向操作符（neq / notIn / isNotNull）已移除，通过对应基础操作符 + negate 实现。
 */
const OPERATORS = [
  { value: 'eq', label: '==' },
  { value: 'gt', label: '>' },
  { value: 'gte', label: '>=' },
  { value: 'lt', label: '<' },
  { value: 'lte', label: '<=' },
  { value: 'in', label: 'in' },
  { value: 'contains', label: 'contains' },
  { value: 'isNull', label: 'is null' },
  { value: 'startsWith', label: 'starts' },
  { value: 'endsWith', label: 'ends' },
  { value: 'regex', label: 'regex' },
];

/** 不需要 value 输入的操作符 */
const VALUE_LESS_OPERATORS = new Set(['isNull']);

interface EdgeConfigPanelProps {
  edgeId: string;
  darkMode?: boolean;
}

const EdgeConfigPanel: React.FC<EdgeConfigPanelProps> = ({ edgeId, darkMode }) => {
  const { edges, nodes, workflowMeta, updateEdgeData, setSelectedEdge } = useWorkflowStore();

  const edge = useMemo(() => edges.find((e) => e.id === edgeId), [edges, edgeId]);
  const sourceNode = useMemo(
    () => nodes.find((n) => n.id === edge?.source),
    [nodes, edge?.source],
  );
  const targetNode = useMemo(
    () => nodes.find((n) => n.id === edge?.target),
    [nodes, edge?.target],
  );

  // 加载源节点函数元信息，用于字段自动补全
  const funcCacheRef = useRef<Map<string, { outputSchema?: Record<string, any> }>>(new Map());
  const [funcLoaded, setFuncLoaded] = useState(false);
  useEffect(() => {
    getFunctions({ page: 0, pageSize: 1000 }, { silent: true })
      .then((res) => {
        const list = res.list || [];
        list.forEach((fn: FunctionDefinition) => {
          if (!funcCacheRef.current.has(fn.name)) {
            funcCacheRef.current.set(fn.name, {
              outputSchema: fn.config?.outputSchema as Record<string, any>,
            });
          }
        });
        setFuncLoaded(true);
      })
      .catch(() => {});
  }, []);

  const sourceFields = useMemo(() => {
    const fields: string[] = [];
    if (sourceNode?.data?.functionRef) {
      const meta = funcCacheRef.current.get(sourceNode.data.functionRef);
      if (meta?.outputSchema?.properties) {
        flattenSchemaFields(meta.outputSchema.properties, '', fields);
      }
    } else {
      const inputSchema = workflowMeta.inputSchema as Record<string, any> | undefined;
      if (inputSchema?.properties) {
        flattenSchemaFields(inputSchema.properties, '', fields);
      }
    }
    return [...new Set(fields)];
  }, [sourceNode, workflowMeta, funcLoaded]);

  const fieldOptions = useMemo(
    () => sourceFields.map((f) => ({ value: f, label: f })),
    [sourceFields],
  );

  const initialData = (edge?.data as ConditionalEdgeData | undefined) || {};
  const hasRules = !!initialData.rules && initialData.rules.length > 0;
  const [mode, setMode] = useState<'expression' | 'rules'>(hasRules ? 'rules' : 'expression');
  const [condition, setCondition] = useState(initialData.condition || '');
  const [logic, setLogic] = useState<'and' | 'or'>(initialData.logic || 'and');
  const [negate, setNegate] = useState(initialData.negate || false);
  const [rules, setRules] = useState<{ field: string; operator: string; value?: any; negate?: boolean }[]>(
    initialData.rules?.length ? initialData.rules : [{ field: '', operator: 'eq', value: '' }],
  );

  if (!edge) return null;

  const handleSave = useClickDebounce(() => {
    const data: ConditionalEdgeData =
      mode === 'expression'
        ? { condition: condition || undefined, negate: negate || undefined }
        : {
            logic,
            negate: negate || undefined,
            rules: rules.filter((r) => r.field || r.operator),
          };
    updateEdgeData(edgeId, {
      data,
      label: formatConditionalLabel(data),
    });
  });

  const handleDelete = useClickDebounce(() => {
    setSelectedEdge(null);
    useWorkflowStore.setState((state) => ({
      edges: state.edges.filter((e) => e.id !== edgeId),
    }));
  });

  const updateRule = (idx: number, patch: Partial<{ field: string; operator: string; value?: any; negate?: boolean }>) => {
    const next = [...rules];
    next[idx] = { ...next[idx], ...patch };
    setRules(next);
  };

  const addRule = () => {
    setRules([...rules, { field: '', operator: 'eq', value: '' }]);
  };

  const removeRule = (idx: number) => {
    setRules(rules.filter((_, i) => i !== idx));
  };

  return (
    <Card
      size="small"
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Text strong style={{ fontSize: 13 }}>条件边配置</Text>
          <div style={{ flex: 1 }} />
          <Button type="text" size="small" icon={<CloseOutlined />} onClick={() => setSelectedEdge(null)} />
        </div>
      }
      style={{
        width: 360,
        borderRadius: 10,
        boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
        background: darkMode ? '#1e293b' : '#fff',
        borderColor: darkMode ? 'rgba(255,255,255,0.08)' : '#e2e8f0',
      }}
      styles={{
        header: {
          borderBottom: `1px solid ${darkMode ? 'rgba(255,255,255,0.08)' : '#f1f5f9'}`,
          padding: '10px 14px',
        },
        body: { padding: 14 },
      }}
    >
      <div style={{ marginBottom: 12 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          源节点：{sourceNode?.data?.label || edge.source} → 目标节点：{targetNode?.data?.label || edge.target}
        </Text>
      </div>

      <Radio.Group
        size="small"
        value={mode}
        onChange={(e) => setMode(e.target.value)}
        style={{ marginBottom: 12 }}
      >
        <Radio.Button value="expression">表达式</Radio.Button>
        <Radio.Button value="rules">结构化规则</Radio.Button>
      </Radio.Group>

      {mode === 'expression' ? (
        <div>
          <Text style={{ fontSize: 12, display: 'block', marginBottom: 6 }}>AviatorScript 表达式（完全兼容 JEXL / SpEL 语法）</Text>
          <Input.TextArea
            rows={3}
            placeholder="如 status == 'APPROVED'"
            value={condition}
            onChange={(e) => setCondition(e.target.value)}
            style={{ fontSize: 12, background: darkMode ? '#0f172a' : '#fff', color: darkMode ? '#e2e8f0' : undefined }}
          />
        </div>
      ) : (
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Text style={{ fontSize: 12 }}>组合逻辑</Text>
            <Select
              size="small"
              value={logic}
              onChange={(v) => setLogic(v)}
              options={[
                { value: 'and', label: '全部满足 (AND)' },
                { value: 'or', label: '任一满足 (OR)' },
              ]}
              style={{ width: 140 }}
            />
            <div style={{ flex: 1 }} />
            <Switch
              size="small"
              checked={negate}
              checkedChildren="NOT"
              unCheckedChildren="NOT"
              title="整体取反"
              onChange={(v) => setNegate(v)}
            />
          </div>

          {rules.map((rule, idx) => {
            const hideValue = VALUE_LESS_OPERATORS.has(rule.operator);
            return (
            <div
              key={idx}
              style={{
                display: 'flex',
                gap: 6,
                alignItems: 'center',
                marginBottom: 8,
                padding: 8,
                borderRadius: 6,
                background: darkMode ? 'rgba(255,255,255,0.04)' : '#f8fafc',
              }}
            >
              <AutoComplete
                size="small"
                placeholder="字段名"
                value={rule.field}
                options={fieldOptions}
                onChange={(v) => updateRule(idx, { field: v })}
                style={{ flex: 1.5, minWidth: 0 }}
                filterOption={(input, option) =>
                  (option?.value as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
                }
                allowClear
              />
              <Select
                size="small"
                value={rule.operator}
                onChange={(v) => updateRule(idx, { operator: v })}
                options={OPERATORS}
                style={{ flex: 1, minWidth: 0 }}
              />
              {!hideValue && (
              <Input
                size="small"
                placeholder="值"
                value={rule.value ?? ''}
                onChange={(e) => updateRule(idx, { value: e.target.value })}
                style={{ flex: 1.5, minWidth: 0 }}
              />
              )}
              <Switch
                size="small"
                checked={rule.negate || false}
                checkedChildren="NOT"
                unCheckedChildren="NOT"
                title="取反"
                onChange={(v) => updateRule(idx, { negate: v })}
              />
              <Button
                type="text"
                size="small"
                danger
                icon={<MinusCircleOutlined />}
                onClick={() => removeRule(idx)}
              />
            </div>
            );
          })}

          <Button type="dashed" size="small" icon={<PlusOutlined />} onClick={addRule} style={{ width: '100%' }}>
            添加规则
          </Button>
        </div>
      )}

      <Space style={{ marginTop: 16, width: '100%', justifyContent: 'space-between' }}>
        <Button type="primary" size="small" onClick={handleSave}>
          保存
        </Button>
        <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={handleDelete}>
          删除连线
        </Button>
      </Space>
    </Card>
  );
};

export default EdgeConfigPanel;
