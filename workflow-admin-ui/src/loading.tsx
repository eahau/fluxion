import React, { useEffect, useState } from 'react';
import { Button, Result, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import PageLoading from '@/components/PageLoading';

const CHUNK_LOAD_TIMEOUT_MS = 12000;

const RouteLoading: React.FC = () => {
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setTimedOut(true);
      console.warn(
        `[route-loader] 路由 chunk 加载超时（> ${CHUNK_LOAD_TIMEOUT_MS / 1000}s），` +
          '可能是模块语法错误或网络异常导致 chunk 下载失败。',
      );
    }, CHUNK_LOAD_TIMEOUT_MS);

    return () => clearTimeout(timer);
  }, []);

  if (timedOut) {
    return (
      <div style={{ padding: 48, display: 'flex', justifyContent: 'center' }}>
        <Result
          status="warning"
          title="页面加载超时"
          subTitle={`路由模块加载超过 ${CHUNK_LOAD_TIMEOUT_MS / 1000} 秒仍未完成，可能存在编译错误或网络问题。请尝试刷新页面，若问题持续请查看控制台错误。`}
          extra={
            <Button type="primary" icon={<ReloadOutlined />} onClick={() => window.location.reload()}>
              刷新重试
            </Button>
          }
        />
      </div>
    );
  }

  return <PageLoading />;
};

export default RouteLoading;
