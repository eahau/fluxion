import { defineConfig } from '@umijs/max';
import routes from './routes';
import proxy from './proxy';

export default defineConfig({
  title: '函数式工作流配置后台',
  antd: {
    theme: {
      token: {
        colorPrimary: '#6366f1',
        colorLink: '#6366f1',
        colorInfo: '#6366f1',
        borderRadius: 8,
      },
    },
  },
  access: {},
  model: {},
  initialState: {},
  request: {},
  layout: false,
  routes,
  proxy,
  npmClient: 'npm',
  locale: {
    default: 'zh-CN',
    useLocalStorage: true,
  },
  theme: {
    'primary-color': '#6366f1',
  },
  chainWebpack(config) {
    config.watchOptions({
      ignored: [
        '**/node_modules/**',
        '**/.umi/**',
        '**/.git/**',
        '**/dist/**',
        '**/build/**',
        '**/pagefile.sys',
        '**/DumpStack.log.tmp',
        '**/System Volume Information/**',
      ],
    });
  },
  devtool: process.env.NODE_ENV === 'development' ? 'source-map' : false,
  esbuildMinifyIIFE: true,
});
