import { Link, Outlet, useLocation, history } from '@umijs/max';
import {
  ClusterOutlined,
  DashboardOutlined,
  FileTextOutlined,
  FunctionOutlined,
  HomeOutlined,
  PartitionOutlined,
  SearchOutlined,
  SettingOutlined,
  ShopOutlined,
  BellOutlined,
  UserOutlined,
  LogoutOutlined,
  SunOutlined,
  MoonOutlined,
} from '@ant-design/icons';
import { Layout, Menu, theme, Avatar, Dropdown, Space, Typography, Breadcrumb, Input, Badge, Tag } from 'antd';
import React, { useState, useMemo, useEffect } from 'react';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

const menuItems = [
  { key: '/', icon: <HomeOutlined />, label: <Link to="/">首页总览</Link> },
  { key: '/workflow', icon: <PartitionOutlined />, label: <Link to="/workflow">工作流</Link> },
  { key: '/function', icon: <FunctionOutlined />, label: <Link to="/function">函数</Link> },
  { key: '/schema', icon: <FileTextOutlined />, label: <Link to="/schema">Schema</Link> },
  { key: '/monitor', icon: <DashboardOutlined />, label: <Link to="/monitor">监控</Link> },
  { key: '/instance', icon: <ClusterOutlined />, label: <Link to="/instance">实例</Link> },
  { key: '/marketplace', icon: <ShopOutlined />, label: <Link to="/marketplace">市场</Link> },
  { key: '/system', icon: <SettingOutlined />, label: <Link to="/system/user">系统</Link> },
];

const breadcrumbMap: Record<string, string> = {
  '/': '首页',
  '/schema': 'Schema 管理',
  '/workflow': '工作流',
  '/function': '函数',
  '/monitor': '监控',
  '/instance': '实例',
  '/marketplace': '市场',
  '/system': '系统管理',
  '/designer': '设计器',
  '/detail': '详情',
  '/editor': '编辑',
  '/execution': '执行历史',
  '/trace': '链路追踪',
  '/dashboard': '仪表盘',
  '/list': '列表',
  '/user': '用户',
  '/role': '角色',
  '/audit': '审计',
};

const BasicLayout: React.FC = () => {
  const location = useLocation();
  const { token } = theme.useToken();
  const [collapsed, setCollapsed] = useState(true);
  const [searchOpen, setSearchOpen] = useState(false);
  const [darkMode, setDarkMode] = useState(() => localStorage.getItem('fluxion-dark-mode') === 'true');

  useEffect(() => {
    if (darkMode) {
      document.body.classList.add('fluxion-dark');
    } else {
      document.body.classList.remove('fluxion-dark');
    }
  }, [darkMode]);

  const toggleDarkMode = () => {
    const next = !darkMode;
    setDarkMode(next);
    localStorage.setItem('fluxion-dark-mode', String(next));
    if (next) {
      document.body.classList.add('fluxion-dark');
    } else {
      document.body.classList.remove('fluxion-dark');
    }
  };

  // 生成面包屑
  const breadcrumbs = useMemo(() => {
    const parts = location.pathname.split('/').filter(Boolean);
    const items = [{ title: <span><HomeOutlined /> 首页</span>, href: '/' }];
    let path = '';
    for (const part of parts) {
      path += `/${part}`;
      const label = breadcrumbMap[part] || breadcrumbMap[path] || part;
      items.push({ title: label, href: path });
    }
    return items;
  }, [location.pathname]);

  // 匹配当前高亮的菜单
  const selectedKey = useMemo(() => {
    const path = location.pathname;
    if (path === '/') return '/';
    const match = menuItems.find((m) => m.key !== '/' && path.startsWith(m.key));
    return match?.key || '/';
  }, [location.pathname]);

  const userMenuItems = [
    { key: 'profile', icon: <UserOutlined />, label: '个人中心' },
    { type: 'divider' as const },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ];

  const handleUserMenuClick = ({ key }: { key: string }) => {
    if (key === 'logout') {
      localStorage.removeItem('workflow-admin-token');
      history.push('/login');
    }
  };

  // 快捷键 Ctrl/Cmd+K 打开搜索
  React.useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setSearchOpen((v) => !v);
      }
      if (e.key === 'Escape') setSearchOpen(false);
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  const quickLinks = [
    { label: '新建工作流', path: '/workflow/designer', icon: <PartitionOutlined /> },
    { label: '注册函数', path: '/function/editor', icon: <FunctionOutlined /> },
    { label: '新建 Schema', path: '/schema/editor', icon: <FileTextOutlined /> },
    { label: '监控面板', path: '/monitor', icon: <DashboardOutlined /> },
    { label: '执行历史', path: '/workflow/execution', icon: <ClusterOutlined /> },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }} className={darkMode ? 'fluxion-dark' : ''}>
      {/* 侧边栏 */}
      <div
        onMouseEnter={() => setCollapsed(false)}
        onMouseLeave={() => setCollapsed(true)}
        style={{ position: 'relative', zIndex: 10, background: 'var(--fluxion-bg-sider)' }}
      >
        <Sider
          theme="light"
          className="fluxion-sider"
          width={220}
          collapsed={collapsed}
          collapsedWidth={64}
          trigger={null}
          style={{
            transition: 'all 0.2s',
            overflow: 'hidden',
            borderRight: '1px solid var(--fluxion-border-light)',
            background: 'var(--fluxion-bg-sider)',
          }}
        >
          {/* Logo 区域 */}
          <div className="sider-logo-area" style={{
            padding: '20px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            justifyContent: collapsed ? 'center' : 'flex-start',
            borderBottom: '1px solid var(--fluxion-border-light)',
          }}>
            <div style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}>
              <PartitionOutlined style={{ fontSize: 18, color: '#fff' }} />
            </div>
            <div style={{
              opacity: collapsed ? 0 : 1,
              maxWidth: collapsed ? 0 : 160,
              transition: 'opacity 0.15s ease 0.05s, max-width 0.2s ease',
              overflow: 'hidden',
              whiteSpace: 'nowrap',
            }}>
              <Title level={5} style={{ margin: 0, color: 'var(--fluxion-text)', fontSize: 16, lineHeight: 1.2 }}>
                Fluxion
              </Title>
              <Text style={{ color: 'var(--fluxion-text-muted)', fontSize: 11 }}>
                Workflow Engine
              </Text>
            </div>
          </div>

          <Menu
            mode="inline"
            theme="light"
            selectedKeys={[selectedKey]}
            style={{ borderRight: 0, marginTop: 8 }}
            items={menuItems}
          />
        </Sider>
      </div>

      {/* 右侧主区域 */}
      <Layout>
        {/* Header */}
        <Header className="fluxion-header" style={{
          background: 'var(--fluxion-bg-header)',
          padding: '0 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          height: 56,
          borderBottom: '1px solid var(--fluxion-border-light)',
          boxShadow: '0 1px 4px rgba(0,0,0,0.04)',
        }}>
          {/* 左侧：面包屑 */}
          <Breadcrumb
            items={breadcrumbs}
            style={{ fontSize: 13 }}
          />

          {/* 右侧：搜索 + 主题切换 + 通知 + 用户 */}
          <Space size={16} align="center">
            {/* 环境标识 */}
            <Tag color="green" style={{ margin: 0, fontSize: 11 }}>DEV</Tag>

            {/* 全局搜索 */}
            <div
              className="fluxion-search-box"
              onClick={() => setSearchOpen(true)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '4px 12px',
                borderRadius: 6,
                border: '1px solid var(--fluxion-border)',
                cursor: 'pointer',
                color: 'var(--fluxion-text-muted)',
                background: 'var(--fluxion-bg-elevated)',
                fontSize: 13,
                minWidth: 180,
                transition: 'all 0.2s',
              }}
            >
              <SearchOutlined />
              <span>搜索...</span>
              <Tag style={{ marginLeft: 'auto', fontSize: 10, lineHeight: '16px' }}>⌘K</Tag>
            </div>

            {/* 主题切换 */}
            <div
              onClick={toggleDarkMode}
              style={{
                width: 32,
                height: 32,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 6,
                cursor: 'pointer',
                color: 'var(--fluxion-text-secondary)',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--fluxion-primary)')}
              onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--fluxion-text-secondary)')}
              title={darkMode ? '切换浅色模式' : '切换深色模式'}
            >
              {darkMode ? <SunOutlined style={{ fontSize: 18 }} /> : <MoonOutlined style={{ fontSize: 18 }} />}
            </div>

            {/* 通知 */}
            <Badge count={0} size="small">
              <BellOutlined style={{ fontSize: 18, cursor: 'pointer', color: 'var(--fluxion-text-secondary)' }} />
            </Badge>

            {/* 用户 */}
            <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <Avatar size={30} style={{ background: 'linear-gradient(135deg, #6366f1, #8b5cf6)' }}>
                  A
                </Avatar>
                <Text style={{ fontSize: 13 }}>管理员</Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        {/* Content */}
        <Content className="fluxion-content" style={{
          margin: 0,
          padding: 24,
          overflow: 'auto',
          background: 'var(--fluxion-bg)',
          minHeight: 'calc(100vh - 56px)',
        }}>
          <Outlet />
        </Content>
      </Layout>

      {/* 全局搜索弹窗 */}
      {searchOpen && (
        <div
          className="global-search-overlay"
          onClick={() => setSearchOpen(false)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.45)',
            zIndex: 1000,
            display: 'flex',
            justifyContent: 'center',
            paddingTop: 120,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              width: 560,
              maxHeight: 460,
              background: 'var(--fluxion-bg-elevated)',
              borderRadius: 12,
              boxShadow: '0 24px 64px rgba(0,0,0,0.2)',
              overflow: 'hidden',
              alignSelf: 'flex-start',
              border: '1px solid var(--fluxion-border)',
            }}
          >
            {/* 搜索输入 */}
            <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--fluxion-border)' }}>
              <Input
                size="large"
                placeholder="搜索工作流、函数、Schema..."
                prefix={<SearchOutlined style={{ color: 'var(--fluxion-text-muted)' }} />}
                autoFocus
                suffix={<Tag style={{ fontSize: 10 }} onClick={() => setSearchOpen(false)}>ESC</Tag>}
                bordered={false}
                style={{ fontSize: 16, padding: '4px 0', color: 'var(--fluxion-text)' }}
              />
            </div>
            {/* 快速链接 */}
            <div style={{ padding: '12px 16px' }}>
              <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: 1 }}>
                快速导航
              </Text>
              <div style={{ marginTop: 8 }}>
                {quickLinks.map((link) => (
                  <div
                    key={link.path}
                    onClick={() => { history.push(link.path); setSearchOpen(false); }}
                    style={{
                      padding: '10px 12px',
                      borderRadius: 8,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                      transition: 'background 0.15s',
                      color: 'var(--fluxion-text-secondary)',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--fluxion-surface-hover)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                  >
                    <span style={{ color: 'var(--fluxion-primary-light)', fontSize: 16 }}>{link.icon}</span>
                    <span style={{ fontSize: 14 }}>{link.label}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </Layout>
  );
};

export default BasicLayout;
