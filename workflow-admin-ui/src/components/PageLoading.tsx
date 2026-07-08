import React from 'react';
import { Spin } from 'antd';

const PageLoading: React.FC = () => (
  <div
    style={{
      minHeight: 240,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    }}
  >
    <Spin size="large" tip="页面加载中..." />
  </div>
);

export default PageLoading;
