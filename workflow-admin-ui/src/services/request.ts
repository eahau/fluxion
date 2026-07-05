import { request as umiRequest } from '@umijs/max';

/**
 * 从包装对象错误响应中构造 Error，保留原始响应数据以便调用方定位问题。
 */
function buildBizError(res: any): Error {
  const error: any = new Error(res.message || res.error || '请求失败');
  error.response = { data: res };
  error.name = 'BizError';
  return error;
}

/**
 * 后端可能返回两种格式：
 * 1. OpenAPI 生成接口返回裸对象（如 PageResponseFunctionDefinition）
 * 2. 部分旧接口/调试接口返回包装对象 { code, data, message, requestId }
 *
 * 这里兼容两种格式：当响应存在数字 code 字段时按包装处理，否则直接返回。
 */
function unwrapResponse<T>(res: any): T {
  if (res && typeof res === 'object' && typeof res.code === 'number') {
    if (res.code !== 0) throw buildBizError(res);
    return res.data as T;
  }
  return res as T;
}

export interface RequestOptions {
  /** 为 true 时跳过全局 errorHandler 的 message.error，由调用方自行处理错误 */
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
  if (!(body instanceof FormData)) {
    reqOptions.requestType = 'json';
  }
  const res = await umiRequest<any>(url, reqOptions);
  return unwrapResponse<T>(res);
}

export async function apiPut<T>(url: string, data?: any, options?: RequestOptions): Promise<T> {
  const body = data ?? {};
  const reqOptions: any = { method: 'PUT', data: body, skipGlobalError: options?.silent };
  if (!(body instanceof FormData)) {
    reqOptions.requestType = 'json';
  }
  const res = await umiRequest<any>(url, reqOptions);
  return unwrapResponse<T>(res);
}

export async function apiDelete<T>(url: string, options?: RequestOptions): Promise<T> {
  const res = await umiRequest<any>(url, { method: 'DELETE', skipGlobalError: options?.silent });
  return unwrapResponse<T>(res);
}
