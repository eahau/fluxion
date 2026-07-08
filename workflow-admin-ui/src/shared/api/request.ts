// Shared/API Layer：全局基础 HTTP 客户端（所有 feature/entity 的 API 封装最终都走这里）
import { request as umiRequest } from '@umijs/max';

function buildBizError(res: any): Error {
  const error: any = new Error(res.message || res.error || '请求失败');
  error.response = { data: res };
  error.name = 'BizError';
  return error;
}

/**
 * 兼容两种后端返回格式：
 *  1. 裸返回（OpenAPI 生成接口默认）
 *  2. { code, data, message } 包装（旧/调试接口）
 */
function unwrapResponse<T>(res: any): T {
  if (res && typeof res === 'object' && typeof res.code === 'number') {
    if (res.code !== 0) throw buildBizError(res);
    return res.data as T;
  }
  return res as T;
}

export interface RequestOptions {
  /** true → 跳过全局 errorHandler 的 message.error，调用方自行 catch */
  silent?: boolean;
}

export async function apiGet<T>(
  url: string,
  params?: Record<string, any>,
  options?: RequestOptions,
): Promise<T> {
  const res = await umiRequest<any>(url, { method: 'GET', params, skipGlobalError: options?.silent });
  return unwrapResponse<T>(res);
}

export async function apiPost<T>(url: string, data?: any, options?: RequestOptions): Promise<T> {
  const body = data ?? {};
  const reqOptions: any = { method: 'POST', data: body, skipGlobalError: options?.silent };
  if (!(body instanceof FormData)) reqOptions.requestType = 'json';
  const res = await umiRequest<any>(url, reqOptions);
  return unwrapResponse<T>(res);
}

export async function apiPut<T>(url: string, data?: any, options?: RequestOptions): Promise<T> {
  const body = data ?? {};
  const reqOptions: any = { method: 'PUT', data: body, skipGlobalError: options?.silent };
  if (!(body instanceof FormData)) reqOptions.requestType = 'json';
  const res = await umiRequest<any>(url, reqOptions);
  return unwrapResponse<T>(res);
}

export async function apiDelete<T>(url: string, options?: RequestOptions): Promise<T> {
  const res = await umiRequest<any>(url, { method: 'DELETE', skipGlobalError: options?.silent });
  return unwrapResponse<T>(res);
}
