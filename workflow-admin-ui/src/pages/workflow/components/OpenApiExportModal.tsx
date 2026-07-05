import { useState } from 'react';
import { Modal, Form, Input, Radio, Button, Space, message } from 'antd';
import { exportOpenAPI } from '@/services/workflow';
import { useClickDebounce } from '@/utils/useClickDebounce';

interface OpenApiExportModalProps {
  workflowIds?: string[];
  visible: boolean;
  onClose: () => void;
}

function downloadBlob(content: string, filename: string, type: string) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

const OpenApiExportModal: React.FC<OpenApiExportModalProps> = ({ workflowIds, visible, onClose }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleExport = useClickDebounce(async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      const result = await exportOpenAPI(workflowIds, values.title, values.version);
      const isYaml = values.format === 'yaml';
      const content = isYaml ? objectToYaml(result) : JSON.stringify(result, null, 2);
      const filename = `openapi-${values.version || '1.0.0'}.${isYaml ? 'yaml' : 'json'}`;
      downloadBlob(content, filename, isYaml ? 'application/yaml' : 'application/json');
      message.success('导出成功');
      onClose();
    } catch (e) {
      console.error('导出失败:', e);
    } finally {
      setLoading(false);
    }
  });

  return (
    <Modal
      title="导出 OpenAPI"
      open={visible}
      onCancel={onClose}
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={loading} onClick={handleExport}>
            导出
          </Button>
        </Space>
      }
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ format: 'yaml' }}>
        <Form.Item name="title" label="文档标题">
          <Input placeholder="工作流 OpenAPI" />
        </Form.Item>
        <Form.Item name="version" label="版本号">
          <Input placeholder="1.0.0" />
        </Form.Item>
        <Form.Item name="format" label="导出格式">
          <Radio.Group>
            <Radio value="yaml">YAML</Radio>
            <Radio value="json">JSON</Radio>
          </Radio.Group>
        </Form.Item>
      </Form>
    </Modal>
  );
};

function objectToYaml(obj: any, indent = 0): string {
  if (obj === null || obj === undefined) return '';
  if (typeof obj !== 'object') return String(obj);
  if (Array.isArray(obj)) {
    if (obj.length === 0) return '[]';
    return obj.map((item) => `${' '.repeat(indent)}- ${objectToYaml(item, indent + 2).trimStart()}`).join('\n');
  }
  const entries = Object.entries(obj);
  if (entries.length === 0) return '{}';
  return entries
    .map(([key, value]) => {
      if (value === null || value === undefined) return `${' '.repeat(indent)}${key}:`;
      if (typeof value === 'object') {
        return `${' '.repeat(indent)}${key}:\n${objectToYaml(value, indent + 2)}`;
      }
      return `${' '.repeat(indent)}${key}: ${String(value)}`;
    })
    .join('\n');
}

export default OpenApiExportModal;
