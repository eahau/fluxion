import { history, useParams, useSearchParams } from '@umijs/max';
import { Button, Card, Col, Divider, Empty, Form, Input, Radio, Row, Select, Space, Spin, Tabs, Tag, Tooltip, message } from 'antd';
import {
  SaveOutlined,
  PlayCircleOutlined,
  ArrowLeftOutlined,
  ApiOutlined,
  DatabaseOutlined,
  SettingOutlined,
  ExperimentOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  CodeOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createFunction, getFunction, updateFunction } from '@/services/function';
import { FunctionTestPanel } from '@/features/function-test';
import SchemaFieldEditor from './components/SchemaFieldEditor';
import SchemaPreview from '@/pages/schema/components/SchemaPreview';
import type { FunctionDefinition, FunctionCategory, FunctionNodeType } from '@/types/function';

const IS_DEV = process.env.NODE_ENV === 'development';

const TAB_CONTENT_STYLE: React.CSSProperties = { padding: '16px 20px 12px 20px' };

const MOCK_FILTER_FUNCTION: FunctionDefinition = {
  id: 'builtin:filter',
  name: 'builtin:filter',
  category: 'BUILTIN',
  nodeType: 'FILTER',
  engine: 'groovy',
  script: '',
  config: {
    description: '条件过滤，根据规则组合结果返回 true/false，配合条件分支使用',
  },
  status: 'ACTIVE',
  scope: 'PLATFORM',
};

const SCOPE_OPTIONS = [
  { value: 'PLATFORM', label: '平台内置' },
  { value: 'PRIVATE', label: '应用私有' },
];

const categoryIcons: Record<string, React.ReactNode> = {
  BUILTIN: <DatabaseOutlined />,
  CUSTOM: <ApiOutlined />,
  SCRIPT: <CodeOutlined />,
  EXTERNAL: <ThunderboltOutlined />,
};

const categoryColors: Record<string, string> = {
  BUILTIN: '#2563eb',
  CUSTOM: '#6366f1',
  SCRIPT: '#0d9488',
  EXTERNAL: '#ea580c',
};

const categories: { value: FunctionCategory; label: string }[] = [
  { value: 'BUILTIN', label: '内置' },
  { value: 'CUSTOM', label: '自定义' },
  { value: 'SCRIPT', label: '脚本' },
  { value: 'EXTERNAL', label: '外部' },
];

const nodeTypes: { value: FunctionNodeType; label: string }[] = [
  { value: 'PARAM_VALIDATE', label: '入参校验' },
  { value: 'DYNAMIC_VALIDATE', label: '动态校验' },
  { value: 'DATA_QUERY', label: '数据查询' },
  { value: 'DATA_TRANSFORM', label: '数据转换' },
  { value: 'ASSEMBLE_RESPONSE', label: '组装响应' },
  { value: 'SCRIPT', label: '脚本执行' },
  { value: 'CUSTOM', label: '自定义' },
  { value: 'CONDITION_BRANCH', label: '条件分支' },
  { value: 'FILTER', label: '条件过滤' },
  { value: 'PARALLEL', label: '并行执行' },
  { value: 'SUB_WORKFLOW', label: '子工作流' },
  { value: 'EIP_ROUTER', label: 'EIP 路由' },
];

const FunctionEditor: React.FC = () => {
  // ============================================================
  // SECTION 1: 所有 React Hooks 集中声明
  //   ★ 不穿插任何普通变量 / 普通函数 / 条件 return
  //   ★ 保证每次 render 的 Hook 数量、顺序 100% 一致
  // ============================================================
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<FunctionDefinition | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [script, setScript] = useState('');
  const [activeTab, setActiveTab] = useState('basic');
  const [testFullScreen, setTestFullScreen] = useState(false);

  const fullscreenRef = useRef<HTMLDivElement | null>(null);
  const saveLockRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Hook 1: 监听浏览器原生 fullscreen 事件，同步到 state
  useEffect(() => {
    const onFsChange = () => {
      const isFs = !!document.fullscreenElement;
      setTestFullScreen((prev) => (prev !== isFs ? isFs : prev));
    };
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  // Hook 2: 根据 id 加载函数详情
  useEffect(() => {
    if (id) {
      setLoading(true);
      setLoadError(false);
      getFunction(id, { silent: true })
        .then((fetched) => {
          setData(fetched);
          setScript(fetched.script || '');
        })
        .catch((err) => {
          console.error('editor load error:', err);
          if (IS_DEV) {
            const mock: FunctionDefinition =
              id === 'builtin:filter'
                ? MOCK_FILTER_FUNCTION
                : {
                    ...MOCK_FILTER_FUNCTION,
                    id,
                    name: id,
                    nodeType: (id.includes('condition') ? 'CONDITION_BRANCH' : 'CUSTOM') as any,
                  };
            setData(mock);
            setScript(mock.script || '');
          } else {
            setLoadError(true);
          }
        })
        .finally(() => setLoading(false));
    }
  }, [id]);

  // Hook 3: 清理保存冷却定时器
  useEffect(() => {
    return () => {
      if (saveLockRef.current) clearTimeout(saveLockRef.current);
    };
  }, []);

  // Hook 4: 保存函数（useCallback 确保 Hook 数量稳定，即使闭包依赖变化）
  const handleSave = useCallback(async () => {
    if (saveLockRef.current) return;
    try {
      const values = await form.validateFields();
      const payload: FunctionDefinition = { ...values, script };
      if (id) {
        await updateFunction(id, payload);
      } else {
        await createFunction(payload);
      }
      message.success('保存成功');
      history.push('/function');
    } finally {
      saveLockRef.current = setTimeout(() => {
        saveLockRef.current = null;
      }, 500);
    }
  }, [id, form, script]);

  // Hook 5: 入参 Schema 解析（每次 render 必须执行，不能放在条件分支后）
  const paramSchema = useMemo(() => {
    const isEditNow = !!id;
    const defaults: any = { category: 'CUSTOM', nodeType: 'CUSTOM', status: 'ACTIVE', engine: 'Groovy', scope: 'PRIVATE' };
    const initial = isEditNow && data ? data : defaults;
    const raw = data?.config?.paramSchema ?? (initial as any)?.config?.paramSchema;
    if (!raw) return null;
    if (typeof raw === 'string') {
      try { return JSON.parse(raw); } catch { return null; }
    }
    return raw;
  }, [data]);

  // Hook 6: 出参 Schema 解析
  const outputSchema = useMemo(() => {
    const isEditNow = !!id;
    const defaults: any = { category: 'CUSTOM', nodeType: 'CUSTOM', status: 'ACTIVE', engine: 'Groovy', scope: 'PRIVATE' };
    const initial = isEditNow && data ? data : defaults;
    const raw = data?.config?.outputSchema ?? (initial as any)?.config?.outputSchema;
    if (!raw) return null;
    if (typeof raw === 'string') {
      try { return JSON.parse(raw); } catch { return null; }
    }
    return raw;
  }, [data]);

  // ============================================================
  // SECTION 2: 普通变量计算（非 Hook，顺序无影响）
  //   ★ 此处开始任何代码都不能再调用 Hook
  // ============================================================
  const isEdit = !!id;
  const readonly = searchParams.get('readonly') === '1' || data?.category === 'BUILTIN';
  const initialValues: any = isEdit && data
    ? data
    : { category: 'CUSTOM', nodeType: 'CUSTOM', status: 'ACTIVE', engine: 'Groovy', scope: 'PRIVATE' };
  const pageTitle = readonly ? '函数详情' : isEdit ? '编辑函数' : '注册函数';

  // ============================================================
  // SECTION 3: 普通函数定义（非 Hook，顺序无影响）
  // ============================================================
  const toggleRealFullscreen = async (next: boolean) => {
    const el = fullscreenRef.current;
    if (next && el && !document.fullscreenElement) {
      try {
        if (el.requestFullscreen) {
          await el.requestFullscreen();
        } else if ((el as any).webkitRequestFullscreen) {
          await (el as any).webkitRequestFullscreen();
        } else if ((el as any).msRequestFullscreen) {
          await (el as any).msRequestFullscreen();
        }
        setTestFullScreen(true);
        return;
      } catch (e: any) {
        console.warn('[function-editor] requestFullscreen failed, fallback to viewport-max pseudo:', e?.message);
        setTestFullScreen(true);
      }
    }
    if (!next && document.fullscreenElement) {
      try {
        if (document.exitFullscreen) {
          await document.exitFullscreen();
        } else if ((document as any).webkitExitFullscreen) {
          await (document as any).webkitExitFullscreen();
        } else if ((document as any).msExitFullscreen) {
          await (document as any).msExitFullscreen();
        }
      } catch (e) {
        console.warn('[function-editor] exitFullscreen failed:', e);
      }
    }
    setTestFullScreen(next);
  };

  /**
   * 第一 Tab：基础信息 + 入参/出参 Schema 预览整合
   *   - 上半部：名称/类别/描述/状态/作用域 等基础表单（可编辑）
   *   - 下半部：入参Schema预览 + 出参Schema预览（复用 SchemaPreview 组件，只读）
   *   - 非 BUILTIN 函数（CUSTOM/SCRIPT/EXTERNAL）：仍保留入参/出参编辑器（与 BUILTIN 区分）
   */
  const renderBasicInfo = () => (
    <div className="function-editor-tab-content" style={TAB_CONTENT_STYLE}>
      <Space direction="vertical" size={14} style={{ width: '100%' }}>
        {/* ===== 区块 1：基础信息表单 ===== */}
        <Card
          size="small"
          style={{ borderLeft: '3px solid #2563eb' }}
          title={
            <Space size={6} style={{ fontSize: 13 }}>
              <SettingOutlined style={{ color: '#2563eb' }} />
              <strong>基础信息</strong>
            </Space>
          }
        >
          <Form form={form} layout="vertical" initialValues={initialValues} labelCol={{ flex: '100px' }} wrapperCol={{ flex: 1 }}>
            {/* 第一行：函数名称 + 类别 + 节点类型 */}
            <Row gutter={[12, 0]}>
              <Col xs={24} md={10} lg={9}>
                <Form.Item name="name" label="函数名称" rules={[{ required: true }]} style={{ marginBottom: 8 }}>
                  <Input placeholder="如 rpc:userService.exists" size="small" disabled={readonly} />
                </Form.Item>
              </Col>
              <Col xs={24} md={7} lg={6}>
                <Form.Item
                  label="类别"
                  rules={[{ required: true }]}
                  style={{ marginBottom: 8 }}
                  tooltip="类别由系统根据函数来源自动分配"
                >
                  <Form.Item noStyle name="category" hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, next) =>
                      prev?.name !== next?.name || prev?.category !== next?.category
                    }
                  >
                    {({ getFieldValue }) => {
                      const nameRaw = getFieldValue('name') as string | undefined;
                      const rawFromApi = (getFieldValue('category') || 'CUSTOM') as FunctionCategory;
                      const derived: FunctionCategory | null = (() => {
                        const name = (nameRaw ?? '').trim().toLowerCase();
                        if (!name) return null;
                        if (name.startsWith('builtin:') || (name.startsWith('builtin') && name.length > 8)) return 'BUILTIN';
                        if (name.startsWith('script:') || name.startsWith('script_')) return 'SCRIPT';
                        if (name.startsWith('external:') || name.startsWith('external_')) return 'EXTERNAL';
                        return null;
                      })();
                      const cur = derived ?? rawFromApi;
                      const color = categoryColors[cur] || '#8c8c8c';
                      const label = categories.find((c) => c.value === cur)?.label || cur;
                      const mismatchBadge = derived && derived !== rawFromApi ? (
                        <Tooltip title={`API 返回类别：${rawFromApi}，根据函数名模式修正为 ${cur}`}>
                          <span style={{
                            marginLeft: 4, fontSize: 10, padding: '0 4px',
                            borderRadius: 4, background: `${color}20`, color,
                          }}>修正</span>
                        </Tooltip>
                      ) : null;
                      return (
                        <div
                          style={{
                            background: `${color}10`,
                            color,
                            border: `1px solid ${color}40`,
                            borderRadius: 6,
                            padding: '4px 10px',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 6,
                            fontSize: 12,
                            fontWeight: 500,
                          }}
                        >
                          {categoryIcons[cur]}
                          <span>{label}</span>
                          {mismatchBadge}
                        </div>
                      );
                    }}
                  </Form.Item>
                </Form.Item>
              </Col>
              <Col xs={24} md={7} lg={9}>
                <Form.Item name="nodeType" label="节点类型" rules={[{ required: true }]} style={{ marginBottom: 8 }}>
                  <Select options={nodeTypes} size="small" disabled={readonly} />
                </Form.Item>
              </Col>
            </Row>
            {/* 第二行：函数描述 */}
            <Row gutter={[12, 0]}>
              <Col span={24}>
                <Form.Item name={['config', 'description']} label="函数描述" style={{ marginBottom: 8 }}>
                  <Input.TextArea autoSize={{ minRows: 1, maxRows: 3 }} size="small" placeholder="函数用途说明" disabled={readonly} />
                </Form.Item>
              </Col>
            </Row>
            {/* 第三行：状态 + 作用域 + appGroup */}
            <Row gutter={[12, 0]}>
              <Col xs={24} md={8}>
                <Form.Item name="status" label="状态" rules={[{ required: true }]} style={{ marginBottom: 8 }}>
                  <Radio.Group size="small" disabled={readonly} buttonStyle="solid">
                    <Radio.Button value="ACTIVE" style={form.getFieldValue('status') === 'ACTIVE' ? { color: '#52c41a', fontWeight: 600 } : {}}>可用</Radio.Button>
                    <Radio.Button value="INACTIVE" style={form.getFieldValue('status') === 'INACTIVE' ? { color: '#ff4d4f', fontWeight: 600 } : {}}>停用</Radio.Button>
                  </Radio.Group>
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="scope" label="作用域" rules={[{ required: true }]} style={{ marginBottom: 8 }}>
                  <Select options={SCOPE_OPTIONS} size="small" disabled={readonly} />
                </Form.Item>
              </Col>
              <Form.Item noStyle shouldUpdate={(prev, next) => prev.scope !== next.scope}>
                {() =>
                  form.getFieldValue('scope') === 'PRIVATE' ? (
                    <Col xs={24} md={8}>
                      <Form.Item name="appGroup" label="应用分组" rules={[{ required: true, message: 'PRIVATE 作用域必须指定应用分组' }]} style={{ marginBottom: 8 }}>
                        <Input placeholder="如 my-app" size="small" disabled={readonly} />
                      </Form.Item>
                    </Col>
                  ) : <Col xs={24} md={8} />
                }
              </Form.Item>
            </Row>
            {/* 第四行：脚本引擎（仅SCRIPT类） */}
            <Form.Item noStyle shouldUpdate={(prev, next) => prev.category !== next.category}>
              {() =>
                form.getFieldValue('category') === 'SCRIPT' ? (
                  <Row gutter={[12, 0]}>
                    <Col xs={24} md={8}>
                      <Form.Item name="engine" label="脚本引擎" style={{ marginBottom: 8 }}>
                        <Select
                          options={[
                            { value: 'Groovy', label: 'Groovy' },
                            { value: 'SpEL', label: 'SpEL' },
                          ]}
                          size="small"
                          disabled={readonly}
                        />
                      </Form.Item>
                    </Col>
                  </Row>
                ) : null
              }
            </Form.Item>
          </Form>
        </Card>

        {/* ===== 区块 2：入参/出参 Schema（复用 SchemaPreview 统一展示） ===== */}
        <Row gutter={[14, 0]}>
          <Col xs={24} lg={12}>
            <Card
              size="small"
              style={{ borderLeft: '3px solid #6366f1', height: '100%' }}
              title={
                <Space size={6} style={{ fontSize: 13 }}>
                  <SettingOutlined style={{ color: '#6366f1' }} />
                  <strong>入参 Schema</strong>
                  {!readonly && data?.category !== 'BUILTIN' && (
                    <Tag color="geekblue" size="small" style={{ marginLeft: 6 }}>可编辑</Tag>
                  )}
                </Space>
              }
            >
              {(paramSchema && Object.keys(paramSchema).length > 0) ? (
                <div style={{ padding: '4px 0' }}>
                  <SchemaPreview schema={paramSchema} schemaName={`${data?.name ?? initialValues?.name ?? 'function'}:param`} />
                </div>
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未定义入参 Schema" />
              )}
              {!readonly && data?.category !== 'BUILTIN' && (
                <Divider style={{ margin: '10px 0' }} />
              )}
              {!readonly && data?.category !== 'BUILTIN' && (
                <Form form={form} layout="vertical" initialValues={initialValues} preserve={false}>
                  <Form.Item name={['config', 'paramSchema']} noStyle>
                    <SchemaFieldEditor readOnly={readonly} />
                  </Form.Item>
                </Form>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card
              size="small"
              style={{ borderLeft: '3px solid #0ea5e9', height: '100%' }}
              title={
                <Space size={6} style={{ fontSize: 13 }}>
                  <ApiOutlined style={{ color: '#0ea5e9' }} />
                  <strong>出参 Schema</strong>
                  {!readonly && data?.category !== 'BUILTIN' && (
                    <Tag color="geekblue" size="small" style={{ marginLeft: 6 }}>可编辑</Tag>
                  )}
                </Space>
              }
            >
              {(outputSchema && Object.keys(outputSchema).length > 0) ? (
                <div style={{ padding: '4px 0' }}>
                  <SchemaPreview schema={outputSchema} schemaName={`${data?.name ?? initialValues?.name ?? 'function'}:output`} />
                </div>
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未定义出参 Schema" />
              )}
              {!readonly && data?.category !== 'BUILTIN' && (
                <Divider style={{ margin: '10px 0' }} />
              )}
              {!readonly && data?.category !== 'BUILTIN' && (
                <Form form={form} layout="vertical" initialValues={initialValues} preserve={false}>
                  <Form.Item name={['config', 'outputSchema']} noStyle>
                    <SchemaFieldEditor readOnly={readonly} />
                  </Form.Item>
                </Form>
              )}
            </Card>
          </Col>
        </Row>
      </Space>
    </div>
  );

  const renderTest = () => {
    return (
      <div
        ref={fullscreenRef}
        className="function-test-tab-root"
        style={{
          position: testFullScreen ? 'fixed' : 'relative',
          inset: testFullScreen ? 0 : undefined,
          zIndex: testFullScreen ? 99999 : undefined,
          background: testFullScreen ? '#ffffff' : undefined,
          overflow: testFullScreen ? 'auto' : undefined,
          width: testFullScreen ? '100vw' : undefined,
          height: testFullScreen ? '100vh' : undefined,
          padding: testFullScreen ? 0 : TAB_CONTENT_STYLE.padding,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
          <Space size={6}>
            {!readonly && (
              <Button type="primary" size="small" icon={<SaveOutlined />} onClick={handleSave}>
                先保存再测试
              </Button>
            )}
            <Tooltip title={testFullScreen ? '退出全屏（ESC 也可退出）' : '全屏测试区（真全屏，无留白）'}>
              <Button
                size="small"
                icon={testFullScreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
                onClick={() => toggleRealFullscreen(!testFullScreen)}
              >
                {testFullScreen ? '还原' : '全屏'}
              </Button>
            </Tooltip>
          </Space>
        </div>
        {id ? (
          <div style={{
            border: testFullScreen ? 'none' : '1px solid #f0f0f0',
            borderRadius: testFullScreen ? 0 : 8,
            padding: 0,
            background: testFullScreen ? '#fff' : '#fafafa',
            height: testFullScreen ? 'calc(100vh - 48px)' : 480,
            minHeight: testFullScreen ? 0 : 480,
            overflow: 'auto',
          }}>
            <FunctionTestPanel functionId={id} functionDefinition={data} defaultAppGroup={data?.appGroup ?? null} />
          </div>
        ) : (
          <div style={{ padding: '80px 0', textAlign: 'center' }}>
            <Space direction="vertical" size={8}>
              <ExperimentOutlined style={{ fontSize: 40, color: '#bfbfbf' }} />
              <div style={{ color: '#8c8c8c', fontSize: 13 }}>请先保存函数后再进行测试</div>
            </Space>
          </div>
        )}
      </div>
    );
  };

  const tabItems = [
    { key: 'basic', label: (<Space size={4}><SettingOutlined />基础信息 &amp; 出入参</Space>), children: renderBasicInfo() },
    { key: 'test', label: (<Space size={4}><PlayCircleOutlined />函数测试</Space>), children: renderTest() },
  ];

  // ★ 单一出口：此处用条件短路渲染 loading / error / 正常内容
  //   避免早期 return 导致 React 检测到 "fewer hooks"
  const bodyNode = loading ? (
    <Card title={pageTitle}>
      <Form form={form} style={{ display: 'none' }} />
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '60px 0' }}>
        <Spin />
      </div>
    </Card>
  ) : isEdit && loadError ? (
    <Card title="函数详情" extra={<Button onClick={() => history.push('/function')}>返回</Button>}>
      <div style={{ padding: '40px 20px', color: '#ff4d4f' }}>加载函数失败，请返回列表重试</div>
    </Card>
  ) : (
    <div style={{ margin: 0 }}>
      <Card
        title={
          <Space size={8}>
            <span style={{ fontSize: 15, fontWeight: 600 }}>
              {pageTitle}
            </span>
            {data?.name && (
              <Space size={4}>
                <span style={{ color: '#8c8c8c', fontSize: 12 }}>·</span>
                <span style={{
                  fontSize: 12,
                  color: '#6366f1',
                  background: '#eef2ff',
                  padding: '2px 8px',
                  borderRadius: 10,
                  fontFamily: 'monospace',
                }}>
                  {data.name}
                </span>
              </Space>
            )}
          </Space>
        }
        styles={{ body: { padding: 0 } }}
        extra={
          <Space size={6}>
            {!readonly && (
              <Button type="primary" icon={<SaveOutlined />} size="small" onClick={handleSave}>
                保存
              </Button>
            )}
            <Button size="small" icon={<ArrowLeftOutlined />} onClick={() => history.push('/function')}>
              返回
            </Button>
          </Space>
        }
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          size="small"
          tabBarStyle={{
            padding: '0 16px',
            margin: 0,
            borderBottom: '1px solid #f0f0f0',
          }}
          items={tabItems}
        />
      </Card>
    </div>
  );

  return bodyNode;
};

export default FunctionEditor;
