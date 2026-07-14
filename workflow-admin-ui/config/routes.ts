export default [
  { path: '/login', component: '@/pages/login', layout: false },
  {
    path: '/',
    component: '@/layouts/BasicLayout',
    routes: [
      { path: '/', component: '@/pages/home', exact: true },
      {
        path: '/schema',
        name: 'Schema 管理',
        icon: 'FileTextOutlined',
        routes: [
          { path: '/schema', component: '@/pages/schema/list', exact: true },
          { path: '/schema/editor', component: '@/pages/schema/editor', exact: true },
          { path: '/schema/editor/:id', component: '@/pages/schema/editor', exact: true },
          { path: '/schema/versions/:id', component: '@/pages/schema/versions', exact: true },
        ],
      },
      {
        path: '/workflow',
        name: '函数集合',
        icon: 'PartitionOutlined',
        routes: [
          { path: '/workflow', component: '@/pages/workflow/list', exact: true },
          { path: '/workflow/designer', component: '@/pages/workflow/designer', exact: true },
          { path: '/workflow/designer/:id', component: '@/pages/workflow/designer', exact: true },
          { path: '/workflow/detail/:id', component: '@/pages/workflow/detail', exact: true },
          { path: '/workflow/execution/:workflowId?', component: '@/pages/workflow/execution', exact: true },
        ],
      },
      {
        path: '/function',
        name: '函数管理',
        icon: 'FunctionOutlined',
        routes: [
          { path: '/function', component: '@/pages/function/list', exact: true },
          { path: '/function/editor', component: '@/pages/function/editor', exact: true },
          { path: '/function/editor/:id', component: '@/pages/function/editor', exact: true },
        ],
      },
      {
        path: '/app',
        name: '应用管理',
        icon: 'AppstoreOutlined',
        routes: [
          { path: '/app', component: '@/pages/app/list', exact: true },
          { path: '/app/:id', component: '@/pages/app/detail', exact: true },
        ],
      },
      {
        path: '/resource',
        name: '资源中心',
        icon: 'DatabaseOutlined',
        routes: [
          { path: '/resource', component: '@/pages/resource/list', exact: true },
          { path: '/resource/:id', component: '@/pages/resource/editor', exact: true },
        ],
      },
      {
        path: '/sandbox',
        name: '沙盒管理',
        icon: 'ExperimentOutlined',
        routes: [
          { path: '/sandbox', component: '@/pages/sandbox/list', exact: true },
        ],
      },
      {
        path: '/monitor',
        name: '监控面板',
        icon: 'DashboardOutlined',
        routes: [
          { path: '/monitor', component: '@/pages/monitor/dashboard', exact: true },
          { path: '/monitor/trace/:executionId?', component: '@/pages/monitor/trace', exact: true },
        ],
      },
      {
        path: '/instance',
        name: '运行面实例',
        icon: 'ClusterOutlined',
        routes: [{ path: '/instance', component: '@/pages/instance/index', exact: true }],
      },
      {
        path: '/marketplace',
        name: '工作流市场',
        icon: 'ShopOutlined',
        routes: [
          { path: '/marketplace', component: '@/pages/marketplace/index', exact: true },
        ],
      },
      {
        path: '/system',
        name: '系统管理',
        icon: 'SettingOutlined',
        access: 'canAdmin',
        routes: [
          { path: '/system/user', component: '@/pages/system/user', exact: true, access: 'canAdmin' },
          { path: '/system/role', component: '@/pages/system/role', exact: true, access: 'canAdmin' },
          { path: '/system/audit', component: '@/pages/system/audit', exact: true, access: 'canAdmin' },
        ],
      },
    ],
  },
];
