import React from 'react';
import { Alert, Button, Space, Tag, Typography } from 'antd';
import { ReloadOutlined, BugOutlined } from '@ant-design/icons';

export interface ComponentErrorBoundaryProps {
  children?: React.ReactNode;
  /** 组件名（用于错误文案更友好） */
  componentName?: string;
  /** 错误时的自定义兜底 UI（不传则使用默认的 Alert 卡片） */
  fallback?: (props: {
    error: Error | null;
    reset: () => void;
  }) => React.ReactNode;
}

interface ComponentErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

class ComponentErrorBoundary extends React.Component<
  ComponentErrorBoundaryProps,
  ComponentErrorBoundaryState
> {
  state: ComponentErrorBoundaryState = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): Partial<ComponentErrorBoundaryState> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    // eslint-disable-next-line no-console
    console.error(
      '[ComponentErrorBoundary]',
      this.props.componentName || 'Unknown',
      'crashed:',
      error,
      info,
    );
  }

  reset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children ?? null;
    }

    const isDev = process.env.NODE_ENV === 'development';
    const name = this.props.componentName || '该组件';

    if (this.props.fallback) {
      return <>{this.props.fallback({ error: this.state.error, reset: this.reset })}</>;
    }

    return (
      <div
        style={{
          border: '1px dashed #ffa39e',
          background: '#fff1f0',
          borderRadius: 8,
          padding: 12,
          minHeight: 80,
        }}
      >
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Space>
            <Tag color="error" icon={<BugOutlined />}>
              {name} 渲染失败
            </Tag>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              此组件异常不会影响页面其他部分，可重试或临时跳过。
            </Typography.Text>
          </Space>
          {isDev && this.state.error && (
            <Alert
              type="error"
              showIcon
              style={{ fontSize: 12 }}
              message={this.state.error.name || 'Error'}
              description={
                <pre
                  style={{
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    margin: 0,
                    fontSize: 11,
                    color: '#cf1322',
                    background: 'transparent',
                    border: 'none',
                    padding: '4px 0 0',
                  }}
                >
                  {this.state.error.message}
                  {'\n'}
                  {(this.state.error.stack || '').split('\n').slice(0, 10).join('\n')}
                </pre>
              }
            />
          )}
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              type="primary"
              danger
              onClick={this.reset}
            >
              尝试重新渲染
            </Button>
          </div>
        </Space>
      </div>
    );
  }
}

export default ComponentErrorBoundary;
