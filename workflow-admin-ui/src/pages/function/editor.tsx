import { history, useParams, useSearchParams } from '@umijs/max';
import { Button, Card, Col, Drawer, Form, Input, Radio, Row, Select, Space, Spin, Tooltip, message } from 'antd';
import {
  SaveOutlined,
  PlayCircleOutlined,
  ArrowLeftOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  ApiOutlined,
  CodeOutlined,
  DatabaseOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { createFunction, getFunction, updateFunction } from '@/services/function';
import { useClickDebounce } from '@/utils/useClickDebounce';
import ScriptEditor from './components/ScriptEditor';
import FunctionTestPanel from './components/FunctionTestPanel';
import SchemaFieldEditor from './components/SchemaFieldEditor';
import ExternalServiceForm from './components/ExternalServiceForm';
import type { FunctionDefinition, FunctionCategory, FunctionNodeType } from '@/types/function';

const IS_DEV = process.env.NODE_ENV === 'development';

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
  BUILTIN: '#722ed1',
  CUSTOM: '#6366f1',
  SCRIPT: '#13c2c2',
  EXTERNAL: '#fa8c16',
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
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<FunctionDefinition | null>(null);
  // BUILTIN 函数仅允许查看，禁止编辑（依赖 data，必须在 data 声明之后）
  const readonly = searchParams.get('readonly') === '1' || data?.category === 'BUILTIN';
  const [loadError, setLoadError] = useState(false);
  const [script, setScript] = useState('');
  const [testVisible, setTestVisible] = useState(false);
  const [testFullScreen, setTestFullScreen] = useState(false);
  const isEdit = !!id;

  useEffect(() => {
    if (id) {
      setLoading(true);
      setLoadError(false);
      getFunction(id, { silent: true })
        .then((data) => {
          setData(data);
          setScript(data.script || '');
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

  const handleSave = useClickDebounce(async () => {
    const values = await form.validateFields();
    const payload: FunctionDefinition = { ...values, script };
    if (isEdit) {
      await updateFunction(id!, payload);
    } else {
      await createFunction(payload);
    }
    message.success('保存成功');
    history.push('/function');
  });

  if (loading) {
    return (
      <Card title={readonly ? '函数详情' : isEdit ? '编辑函数' : '注册函数'}>
        <Spin spinning />
      </Card>
    );
  }

  if (isEdit && loadError) {
    return (
      <Card title="函数详情" extra={<Button onClick={() => history.push('/function')}>返回</Button>}>
        加载函数失败
      </Card>
    );
  }

  const initialValues = isEdit && data ? data : { category: 'CUSTOM', nodeType: 'CUSTOM', status: 'ACTIVE', engine: 'Groovy', scope: 'PRIVATE' };

  return (
    <Card
      title={
        <Space size={8}>
          <span style={{ fontSize: 16, fontWeight: 600 }}>{readonly ? '函数详情' : isEdit ? '编辑函数' : '注册函数'}</span>
        </Space>
      }
      styles={{ body: { padding: '16px 20px' } }}
      extra={
        <Space size={8}>
          {!readonly && (
            <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
              保存
            </Button>
          )}
          <Tooltip title="在右侧抽屉中测试函数">
            <Button
              icon={<PlayCircleOutlined />}
              onClick={() => setTestVisible(!testVisible)}
              style={testVisible ? { color: '#6366f1', borderColor: '#6366f1' } : {}}
            >
              {testVisible ? '收起测试' : '测试'}
            </Button>
          </Tooltip>
          <Button icon={<ArrowLeftOutlined />} onClick={() => history.push('/function')}>返回</Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" initialValues={initialValues}>
        <Row gutter={[12, 0]}>
          <Col xs={24} md={16}>
            <Form.Item name="name" label="函数名称" rules={[{ required: true }]} style={{ marginBottom: 12 }}>
              <Input placeholder="如 rpc:userService.exists" disabled={readonly} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="category" label="类别" rules={[{ required: true }]} style={{ marginBottom: 12 }}>
              <Radio.Group disabled={readonly}>
                {categories.map((c) => (
                  <Radio.Button key={c.value} value={c.value} style={form.getFieldValue('category') === c.value ? { color: categoryColors[c.value], borderColor: categoryColors[c.value], fontWeight: 600 } : {}}>
                    {categoryIcons[c.value]} {c.label}
                  </Radio.Button>
                ))}
              </Radio.Group>
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item name={['config', 'description']} label="函数描述" style={{ marginBottom: 12 }}>
              <Input.TextArea autoSize={{ minRows: 1, maxRows: 3 }} placeholder="函数用途说明" disabled={readonly} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="nodeType" label="节点类型" rules={[{ required: true }]} style={{ marginBottom: 12 }}>
              <Select options={nodeTypes} disabled={readonly} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="status" label="状态" rules={[{ required: true }]} style={{ marginBottom: 12 }}>
              <Radio.Group disabled={readonly}>
                <Radio.Button value="ACTIVE" style={form.getFieldValue('status') === 'ACTIVE' ? { color: '#52c41a', borderColor: '#b7eb8f', background: '#f6ffed', fontWeight: 600 } : {}}>可用</Radio.Button>
                <Radio.Button value="INACTIVE" style={form.getFieldValue('status') === 'INACTIVE' ? { color: '#ff4d4f', borderColor: '#ffa39e', background: '#fff2f0', fontWeight: 600 } : {}}>停用</Radio.Button>
              </Radio.Group>
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="scope" label="作用域" rules={[{ required: true }]} style={{ marginBottom: 12 }}>
              <Select options={SCOPE_OPTIONS} disabled={readonly} />
            </Form.Item>
          </Col>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.scope !== next.scope}>
            {() =>
              form.getFieldValue('scope') === 'PRIVATE' ? (
                <Col xs={24} md={8}>
                  <Form.Item name="appGroup" label="应用分组 (app_group)" rules={[{ required: true, message: 'PRIVATE 作用域必须指定应用分组' }]} style={{ marginBottom: 12 }}>
                    <Input placeholder="如 my-app" disabled={readonly} />
                  </Form.Item>
                </Col>
              ) : null
            }
          </Form.Item>
        </Row>
        <Form.Item noStyle shouldUpdate={(prev, next) => prev.category !== next.category}>
          {() =>
            form.getFieldValue('category') === 'SCRIPT' ? (
              <Row gutter={[12, 0]}>
                <Col xs={24} md={8}>
                  <Form.Item name="engine" label="脚本引擎" style={{ marginBottom: 12 }}>
                    <Select
                      options={[
                        { value: 'Groovy', label: 'Groovy' },
                        { value: 'SpEL', label: 'SpEL' },
                      ]}
                      disabled={readonly}
                    />
                  </Form.Item>
                </Col>
              </Row>
            ) : null
          }
        </Form.Item>
        <div style={{ margin: '20px 0 8px', display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ width: 4, height: 18, borderRadius: 2, background: '#6366f1' }} />
          <span style={{ fontWeight: 600, fontSize: 14, color: '#1e293b' }}>入参/出参定义</span>
        </div>
        <Card size="small" className="fn-schema-card" style={{ borderLeft: '3px solid #6366f1' }}>
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item name={['config', 'paramSchema']} label="入参 Schema">
                <SchemaFieldEditor readOnly={readonly} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name={['config', 'outputSchema']} label="出参 Schema">
                <SchemaFieldEditor readOnly={readonly} />
              </Form.Item>
            </Col>
          </Row>
        </Card>
        <Form.Item noStyle shouldUpdate={(prev, next) => prev.category !== next.category}>
          {() => {
            const category = form.getFieldValue('category');
            if (category === 'EXTERNAL') {
              return (
                <Card title={<Space><ThunderboltOutlined />外部服务</Space>} size="small" className="fn-section-card" style={{ borderLeft: '3px solid #fa8c16' }}>
                  <Form.Item name={['config']}>
                    <ExternalServiceForm readOnly={readonly} />
                  </Form.Item>
                </Card>
              );
            }
            if (category === 'SCRIPT') {
              return (
                <Card title={<Space><CodeOutlined />脚本/配置</Space>} size="small" className="fn-section-card" style={{ borderLeft: '3px solid #13c2c2' }}>
                  <Form.Item noStyle shouldUpdate={(prev, next) => prev.engine !== next.engine}>
                    {() => (
                      <ScriptEditor
                        language={(form.getFieldValue('engine') || 'Groovy').toLowerCase()}
                        value={script}
                        onChange={setScript}
                        readOnly={readonly}
                        height={320}
                      />
                    )}
                  </Form.Item>
                </Card>
              );
            }
            return null;
          }}
        </Form.Item>
      </Form>
      <Drawer
        title="函数测试"
        placement="right"
        width={testFullScreen ? '90vw' : 800}
        open={testVisible && !!id}
        onClose={() => setTestVisible(false)}
        styles={{ body: { padding: 12 } }}
        extra={
          <Button
            icon={testFullScreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
            size="small"
            onClick={() => setTestFullScreen(!testFullScreen)}
          >
            {testFullScreen ? '收起' : '全屏'}
          </Button>
        }
      >
        {id && <FunctionTestPanel functionId={id} functionDefinition={data} />}
      </Drawer>
    </Card>
  );
};

export default FunctionEditor;
