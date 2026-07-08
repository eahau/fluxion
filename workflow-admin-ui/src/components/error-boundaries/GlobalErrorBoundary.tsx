import React from 'react';
import { Alert, Button, Result, Space } from 'antd';
import { history } from '@umijs/max';
import { ReloadOutlined, HomeOutlined } from '@ant-design/icons';

interface GlobalErrorBoundaryProps {
  children?: React.ReactNode;
}

interface GlobalErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
}

class GlobalErrorBoundary extends React.Component<
  GlobalErrorBoundaryProps,
  GlobalErrorBoundaryState
> {
  state: GlobalErrorBoundaryState = {
    hasError: false,
    error: null,
    errorInfo: null,
  };

  static getDerivedStateFromError(error: Error): Partial<GlobalErrorBoundaryState> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    // 控制台保留完整堆栈，便于排查。不 throw 以避免再次触发全局崩溃。
    // eslint-disable-next-line no-console
    console.error('[GlobalErrorBoundary] Uncaught error:', error, errorInfo);
    this.setState({ errorInfo });
  }

  private handleReload = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    setTimeout(() => window.location.reload(), 50);
  };

  private handleHome = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    history.push('/');
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children ?? null;
    }

    const { error, errorInfo } = this.state;
    const isDev = process.env.NODE_ENV === 'development';

    return (
      <div
        style={{
          minHeight: '100vh',
          padding: 40,
          background:
            'radial-gradient(circle at 20% 20%, #fff 0%, #f5f7fb 55%, #eef1ff 100%)',
        }}
      >
        <Result
          status="error"
          title="界面出现未处理异常"
          subTitle={
            <span>
              已启用全局兜底，其余页面仍可正常访问。可返回首页或尝试刷新当前页。
              {!isDev && (
                <>
                  <br />
                  <span style={{ opacity: 0.6 }}>
                    详细错误信息已记录至控制台（开发模式下可直接展开堆栈）。
                  </span>
                </>
              )}
            </span>
          }
          extra={
            <Space>
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                onClick={this.handleReload}
              >
                重新加载
              </Button>
              <Button icon={<HomeOutlined />} onClick={this.handleHome}>
                返回首页
              </Button>
            </Space>
          }
        >
          {isDev && error && (
            <Alert
              type="error"
              showIcon
              message={error.name || 'Runtime Error'}
              description={error.message || '未知异常'}
              style={{ textAlign: 'left' }}
            />
          )}
          {isDev && errorInfo?.componentStack && (
            <pre
              style={{
                marginTop: 16,
                textAlign: 'left',
                background: '#fff1f0',
                padding: 16,
                borderRadius: 8,
                maxHeight: 360,
                overflow: 'auto',
                fontSize: 12,
                color: '#cf1322',
                border: '1px solid #ffa39e',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
              }}
            >
              {errorInfo.componentStack}
            </pre>
          )}
        </Result>
      </div>
    );
  }
}

export default GlobalErrorBoundary;
