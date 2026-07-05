import { Col, Form, Input, InputNumber, Row, Select, Switch } from 'antd';
import { useEffect } from 'react';

export type ExternalProtocol = 'http' | 'https' | 'grpc' | 'dubbo';

const PROTOCOL_OPTIONS: { value: ExternalProtocol; label: string }[] = [
  { value: 'http', label: 'HTTP' },
  { value: 'https', label: 'HTTPS' },
  { value: 'grpc', label: 'gRPC' },
  { value: 'dubbo', label: 'Dubbo' },
];

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];
const GRPC_METHODS = ['UNARY'];

export interface ExternalServiceConfig {
  protocol: ExternalProtocol;
  /** HTTP: URL; gRPC: 全限定服务名如 com.example.Greeter; Dubbo: 接口全限定名 */
  service: string;
  /** HTTP: 请求方法; gRPC/Dubbo: 方法名 */
  method: string;
  /** gRPC: host:port; Dubbo: 直连地址，如 dubbo://127.0.0.1:20880 */
  endpoint?: string;
  timeoutMs?: number;
  headers?: Record<string, string>;
  /** 工作流入参字段 → 远程调用参数的映射 */
  paramMapping?: Record<string, string>;
  /** 协议特定扩展字段 */
  extras?: Record<string, any>;
}

interface ExternalServiceFormProps {
  value?: ExternalServiceConfig;
  onChange?: (value: ExternalServiceConfig) => void;
  readOnly?: boolean;
}

const ExternalServiceForm: React.FC<ExternalServiceFormProps> = ({ value, onChange, readOnly }) => {
  const [form] = Form.useForm<ExternalServiceConfig>();

  useEffect(() => {
    if (!value) return;
    const displayValue: any = { ...value };
    if (value.paramMapping && typeof value.paramMapping === 'object') {
      displayValue.paramMapping = JSON.stringify(value.paramMapping, null, 2);
    }
    if (value.extras?.parameterTypes && Array.isArray(value.extras.parameterTypes)) {
      displayValue.extras = { ...value.extras, parameterTypes: JSON.stringify(value.extras.parameterTypes) };
    }
    form.setFieldsValue(displayValue);
  }, [value, form]);

  const handleValuesChange = (_: any, all: ExternalServiceConfig) => {
    const normalized = normalizeConfig(all);
    const merged = mergeWithBase(normalized);
    form.setFieldsValue(normalized as any);
    onChange?.(merged);
  };

  const normalizeConfig = (raw: ExternalServiceConfig): ExternalServiceConfig => {
    const copy: ExternalServiceConfig = { ...raw };
    const rawParamMapping: any = raw.paramMapping;
    if (typeof rawParamMapping === 'string' && rawParamMapping.trim()) {
      try {
        copy.paramMapping = JSON.parse(rawParamMapping);
      } catch {
        // 解析失败保留原字符串，后端再做容错
      }
    }
    const rawParameterTypes: any = raw.extras?.parameterTypes;
    if (typeof rawParameterTypes === 'string' && rawParameterTypes.trim()) {
      try {
        copy.extras = { ...copy.extras, parameterTypes: JSON.parse(rawParameterTypes) };
      } catch {
        // 保留原字符串
      }
    }
    return copy;
  };

  const mergeWithBase = (externalConfig: ExternalServiceConfig): ExternalServiceConfig => {
    if (!value) return externalConfig;
    const base = { ...value };
    // 移除旧的外部服务字段，用新值覆盖
    delete (base as any).protocol;
    delete (base as any).service;
    delete (base as any).method;
    delete (base as any).endpoint;
    delete (base as any).timeoutMs;
    delete (base as any).headers;
    delete (base as any).paramMapping;
    delete (base as any).extras;
    return { ...base, ...externalConfig };
  };

  const protocol = Form.useWatch('protocol', form) || value?.protocol || 'http';

  const isHttp = protocol === 'http' || protocol === 'https';
  const isGrpc = protocol === 'grpc';
  const isDubbo = protocol === 'dubbo';

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={value || { protocol: 'http', method: 'GET' }}
      onValuesChange={handleValuesChange}
    >
      <Row gutter={16}>
        <Col xs={24} md={8}>
          <Form.Item name="protocol" label="协议" rules={[{ required: true }]} style={{ marginBottom: 12 }}>
            <Select options={PROTOCOL_OPTIONS} disabled={readOnly} />
          </Form.Item>
        </Col>
        <Col xs={24} md={16}>
          <Form.Item name="service" label={isHttp ? '请求 URL' : isGrpc ? '服务名 (service)' : '接口全限定名'} rules={[{ required: true }]} style={{ marginBottom: 12 }}>
            <Input placeholder={isHttp ? 'https://api.example.com/users/{id}' : isGrpc ? 'com.example.Greeter' : 'com.example.UserService'} disabled={readOnly} />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col xs={24} md={isHttp ? 8 : 12}>
          <Form.Item name="method" label={isHttp ? '请求方法' : '方法名'} rules={[{ required: true }]} style={{ marginBottom: 12 }}>
            {isHttp ? (
              <Select options={HTTP_METHODS.map((m) => ({ value: m, label: m }))} disabled={readOnly} />
            ) : isGrpc ? (
              <Select options={GRPC_METHODS.map((m) => ({ value: m, label: m }))} disabled={readOnly} />
            ) : (
              <Input placeholder="如 getUser" disabled={readOnly} />
            )}
          </Form.Item>
        </Col>
        {!isHttp && (
          <Col xs={24} md={12}>
            <Form.Item name="endpoint" label={isGrpc ? '端点 (host:port)' : '直连地址'} rules={[{ required: isGrpc }]} style={{ marginBottom: 12 }}>
              <Input placeholder={isGrpc ? '127.0.0.1:50051' : 'dubbo://127.0.0.1:20880'} disabled={readOnly} />
            </Form.Item>
          </Col>
        )}
        <Col xs={24} md={isHttp ? 8 : 12}>
          <Form.Item name="timeoutMs" label="超时 (ms)" style={{ marginBottom: 12 }}>
            <InputNumber min={0} step={100} disabled={readOnly} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        {isHttp && (
          <Col xs={24} md={8}>
            <Form.Item name={['headers', 'Content-Type']} label="Content-Type" style={{ marginBottom: 12 }}>
              <Input placeholder="application/json" disabled={readOnly} />
            </Form.Item>
          </Col>
        )}
      </Row>

      {isHttp && (
        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item name={['extras', 'responseType']} label="响应解析" style={{ marginBottom: 12 }}>
              <Select
                options={[
                  { value: 'json', label: 'JSON 对象' },
                  { value: 'text', label: '纯文本' },
                ]}
                disabled={readOnly}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name={['extras', 'bodyTemplate']} label="请求体模板 (可选)" style={{ marginBottom: 12 }}>
              <Input placeholder='{"key":"${input}"}' disabled={readOnly} />
            </Form.Item>
          </Col>
        </Row>
      )}

      {isGrpc && (
        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item name={['extras', 'useTls']} label="启用 TLS" valuePropName="checked" style={{ marginBottom: 12 }}>
              <Switch disabled={readOnly} />
            </Form.Item>
          </Col>
        </Row>
      )}

      {isDubbo && (
        <>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item name={['extras', 'version']} label="版本 (version)" style={{ marginBottom: 12 }}>
                <Input placeholder="1.0.0" disabled={readOnly} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name={['extras', 'group']} label="分组 (group)" style={{ marginBottom: 12 }}>
                <Input placeholder="default" disabled={readOnly} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item name={['extras', 'parameterTypes']} label="参数类型 (JSON 数组)" style={{ marginBottom: 12 }}>
                <Input placeholder='["java.lang.Long"]' disabled={readOnly} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name={['extras', 'registryAddress']} label="注册中心地址 (可选)" style={{ marginBottom: 12 }}>
                <Input placeholder="nacos://127.0.0.1:8848" disabled={readOnly} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item name={['extras', 'applicationName']} label="应用名 (可选)" style={{ marginBottom: 12 }}>
                <Input placeholder="fluxion-external-function" disabled={readOnly} />
              </Form.Item>
            </Col>
          </Row>
        </>
      )}

      <Form.Item name="paramMapping" label="参数映射 (可选，JSON 对象)" style={{ marginBottom: 12 }}>
        <Input.TextArea
          rows={3}
          placeholder='{"remoteField": "workflowField"}'
          disabled={readOnly}
        />
      </Form.Item>
    </Form>
  );
};

export default ExternalServiceForm;
