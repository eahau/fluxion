import { RuntimeConfig, history } from '@umijs/max';
import { message } from 'antd';
import { apiGet } from '@/services/request';
import type { CurrentUser } from '@/types/api';

const IS_DEV = process.env.NODE_ENV === 'development';

const MOCK_CURRENT_USER: CurrentUser = {
  name: '开发管理员',
  avatar: undefined,
  roles: ['ADMIN'],
  permissions: ['*'],
  access: { canView: true, canEdit: true, canPublish: true, canAdmin: true },
};

export const getInitialState = async (): Promise<{
  currentUser?: CurrentUser;
  settings?: Record<string, any>;
}> => {
  const pathname = history.location.pathname;
  if (pathname === '/login') {
    return { settings: {} };
  }
  try {
    const currentUser = await apiGet<CurrentUser>('/api/admin/current-user');
    return { currentUser, settings: {} };
  } catch (err) {
    if (IS_DEV) {
      return { currentUser: MOCK_CURRENT_USER, settings: {} };
    }
    history.push('/login');
    return { settings: {} };
  }
};

/**
 * 从后端错误响应体中提取可读的错误描述。
 *
 * 兼容 Admin 后台统一错误格式 { code, message, timestamp }、
 * 动态路由错误格式 { errorCode, message } 以及旧的 { error, errorMessage } 格式。
 */
function extractErrorMessage(error: any): string {
  if (error?.name === 'BizError') {
    return error.message;
  }
  const data = error?.response?.data;
  if (data && typeof data === 'object') {
    if (typeof data.message === 'string' && data.message) return data.message;
    if (typeof data.error === 'string' && data.error) return data.error;
    if (typeof data.errorMessage === 'string' && data.errorMessage) return data.errorMessage;
  }
  return error?.message || '网络异常';
}

let lastErrorMessage = '';
let lastErrorTime = 0;
const ERROR_DEDUP_INTERVAL = 3000;

/**
 * 对全局错误提示做去重，避免同一消息在短时间（如并发请求、自动刷新）内反复弹窗。
 */
function showGlobalError(msg: string) {
  const now = Date.now();
  if (msg === lastErrorMessage && now - lastErrorTime < ERROR_DEDUP_INTERVAL) {
    return;
  }
  lastErrorMessage = msg;
  lastErrorTime = now;
  message.error(msg);
}

export const request: RuntimeConfig['request'] = {
  timeout: 30000,
  errorConfig: {
    errorThrower: (res: any) => {
      // 兼容包装对象 { code, data, message }（数字 code）
      if (res && typeof res.code === 'number' && res.code !== 0) {
        const error: any = new Error(res?.message || '请求失败');
        error.name = 'BizError';
        error.info = res;
        throw error;
      }
      // 兼容动态路由 / 旧接口 { success: false, error: '...' }
      if (res && res.success === false) {
        const error: any = new Error(res.error || res.message || '请求失败');
        error.name = 'BizError';
        error.info = res;
        throw error;
      }
    },
    errorHandler: (error: any, opts: any) => {
      if (error?.response?.status === 401) {
        localStorage.removeItem('workflow-admin-token');
        if (history.location.pathname !== '/login') {
          history.push('/login');
        }
      } else if (!opts?.skipGlobalError) {
        // 调用方通过 skipGlobalError 自行处理错误（可静默可自定义文案），
        // 避免全局提示与页面级 message.error 重复弹窗。
        showGlobalError(extractErrorMessage(error));
      }
      throw error;
    },
  },
  requestInterceptors: [
    (config) => {
      const token = localStorage.getItem('workflow-admin-token');
      if (token) {
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
  ],
  responseInterceptors: [
    (response) => {
      // 错误提示统一交给 errorHandler/errorThrower，避免与调用方重复弹 message。
      return response;
    },
  ],
};
