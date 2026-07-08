import React, { useState } from 'react';
import { Button, Form, Input, message, Typography } from 'antd';
import { LockOutlined, UserOutlined, PartitionOutlined } from '@ant-design/icons';
import { client, unwrap } from '@/sdk';
import { useClickDebounce } from '@/utils/useClickDebounce';

const { Title, Text } = Typography;

interface LoginForm {
  username: string;
  password: string;
}

interface LoginResponse {
  token: string;
  type: string;
}

const LoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);

  const handleLogin = useClickDebounce(async (values: LoginForm) => {
    setLoading(true);
    try {
      const res = unwrap(
        await client.POST('/api/admin/auth/login', { body: values as any }),
      ) as unknown as LoginResponse;
      localStorage.setItem('workflow-admin-token', res.token);
      message.success('登录成功');
      window.location.href = '/';
    } catch (error: any) {
      console.error('登录失败:', error);
    } finally {
      setLoading(false);
    }
  });

  return (
    <div className="login-page">
      {/* 背景装饰 */}
      <div className="login-bg">
        <div className="login-bg-gradient" />
        <div className="login-bg-grid" />
        {/* 浮动装饰元素 */}
        <div className="login-float login-float-1">
          <PartitionOutlined style={{ fontSize: 40, color: 'rgba(255,255,255,0.15)' }} />
        </div>
        <div className="login-float login-float-2">
          <PartitionOutlined style={{ fontSize: 28, color: 'rgba(255,255,255,0.1)' }} />
        </div>
        <div className="login-float login-float-3">
          <PartitionOutlined style={{ fontSize: 52, color: 'rgba(255,255,255,0.08)' }} />
        </div>
      </div>

      {/* 登录卡片 */}
      <div className="login-container">
        <div className="login-card">
          {/* 品牌区域 */}
          <div className="login-brand">
            <div className="login-logo">
              <PartitionOutlined style={{ fontSize: 32, color: '#fff' }} />
            </div>
            <Title level={3} style={{ margin: '16px 0 4px', color: '#1e293b' }}>
              Fluxion
            </Title>
            <Text type="secondary" style={{ fontSize: 14 }}>
              函数式工作流编排平台
            </Text>
          </div>

          {/* 表单区域 */}
          <Form<LoginForm>
            name="login"
            size="large"
            initialValues={{ username: 'admin', password: 'admin' }}
            onFinish={handleLogin}
            className="login-form"
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input prefix={<UserOutlined style={{ color: '#8c8c8c' }} />} placeholder="用户名" />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password prefix={<LockOutlined style={{ color: '#8c8c8c' }} />} placeholder="密码" />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0 }}>
              <Button type="primary" htmlType="submit" loading={loading} block size="large" className="login-btn">
                登 录
              </Button>
            </Form.Item>
          </Form>

          {/* 底部信息 */}
          <div className="login-footer">
            <Text type="secondary" style={{ fontSize: 12 }}>
              Fluxion Workflow Engine v1.0
            </Text>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
