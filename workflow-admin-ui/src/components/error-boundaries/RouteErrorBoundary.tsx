import React from 'react';
import { Alert, Button, Result, Space, Typography } from 'antd';
import { history, useLocation } from '@umijs/max';
import {
  ReloadOutlined,
  HomeOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';

interface RouteErrorBoundaryProps {
  children?: React.ReactNode;
  /** 该路由对应的业务模块名（用于错误文案更友好），不填则取自 pathname */
  moduleName?: string;
}

interface RouteErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

class RouteErrorBoundaryInner extends React.Component<
  RouteErrorBoundaryProps & { pathname: string },
  RouteErrorBoundaryState
> {
  state: RouteErrorBoundaryState = { hasError: false, error: null };

  static getDerivedStateFromProps(
    nextProps: RouteErrorBoundaryProps & { pathname: string },
    prevState: RouteErrorBoundaryState & { lastPathname?: string },
  ): Partial<RouteErrorBoundaryState> | null {
    if (prevState.lastPathname !== undefined && nextProps.pathname !== prevState.lastPathname && prevState.hasError) {
      return { hasError: false, error: null, lastPathname: nextProps.pathname } as any;
    }
    if (prevState.lastPathname === undefined) {
      return { lastPathname: nextProps.pathname } as any;
    }
    return { lastPathname: nextProps.pathname } as any;
  }

  static getDerivedStateFromError(error: Error): Partial<RouteErrorBoundaryState> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    // eslint-disable-next-line no-console
    console.error('[RouteErrorBoundary] Page crash:', this.props.pathname, error, info);
  }

  private handleReload = () => {
    this.setState({ hasError: false, error: null });
    setTimeout(() => window.location.reload(), 50);
  };

  private handleHome = () => {
    this.setState({ hasError: false, error: null });
    history.push('/');
  };

  private handleBack = () => {
    this.setState({ hasError: false, error: null });
    history.go(-1);
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children ?? null;
    }

    const isDev = process.env.NODE_ENV === 'development';
    const moduleLabel =
      this.props.moduleName ||
      this.props.pathname ||
      '当前页面';

    return (
      <div style={{ padding: 16 }}>
        <Result
          status="warning"
          title={`${moduleLabel} 暂时无法打开`}
          subTitle={
            <span>
              该模块存在代码问题，但不会影响其他页面。您仍可通过左侧菜单或导航访问其他功能。
            </span>
          }
          extra={
            <Space wrap>
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                onClick={this.handleReload}
              >
                重新加载
              </Button>
              <Button icon={<ArrowLeftOutlined />} onClick={this.handleBack}>
                返回上一页
              </Button>
              <Button icon={<HomeOutlined />} onClick={this.handleHome}>
                返回首页
              </Button>
            </Space>
          }
        >
          {isDev && this.state.error && (
            <Alert
              type="error"
              showIcon
              message={
                <Typography.Text code style={{ color: 'inherit' }}>
                  {this.state.error.name || 'Error'}
                </Typography.Text>
              }
              description={
                <pre
                  style={{
                    marginTop: 8,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    fontSize: 12,
                    margin: 0,
                    color: '#cf1322',
                    background: 'transparent',
                    border: 'none',
                    padding: 0,
                  }}
                >
                  {this.state.error.message}
                  {'\n'}
                  {this.state.error.stack}
                </pre>
              }
              style={{ textAlign: 'left' }}
            />
          )}
        </Result>
      </div>
    );
  }
}

const RouteErrorBoundary: React.FC<RouteErrorBoundaryProps> = ({
  children,
  moduleName,
}) => {
  const location = useLocation();
  return (
    <RouteErrorBoundaryInner pathname={location.pathname} moduleName={moduleName}>
      {children}
    </RouteErrorBoundaryInner>
  );
};

export default RouteErrorBoundary;
