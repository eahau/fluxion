/**
 * Typed API client generated from `doc/openapi.yaml`.
 *
 * This module wires the zero-runtime `openapi-typescript` path types (see
 * `src/types/api.generated.d.ts`) together with the tiny `openapi-fetch`
 * runtime to produce a fully type-checked HTTP client.
 *
 * Two usage styles are supported:
 *
 *  1. **Raw tuple style** (return value / error discriminated union, never throws):
 *     ```ts
 *     const { data, error } = await client.GET('/api/admin/functions', {
 *       params: { query: { keyword: 'db' } }
 *     });
 *     if (error) { /* handle error *​/ } else { /* use data *​/ }
 *     ```
 *
 *  2. **Convenience style** – every exported service wrapper (see the files
 *     next to this one) returns `Promise<T>` and throws on error, matching
 *     the legacy `apiGet/apiPost` helper semantics used throughout the app.
 *
 * The underlying HTTP transport is **not** `globalThis.fetch` — it is
 * bridged to Umi Max's built-in `request()` helper so that:
 *  - the existing proxy config (`config/proxy.ts`) and auth interceptors are used
 *  - `{ code, data, message }` wrapped responses are unwrapped transparently
 *  - global error-toast handling can be skipped via `silent: true`
 */
import createClient from 'openapi-fetch';
import { request as umiRequest } from '@umijs/max';
import type { paths } from '@/types/api.generated';

/**
 * Build the business error that legacy `apiGet/apiPost` helpers produced so
 * callers that catch `error.name === 'BizError'` continue to work.
 */
function buildBizError(res: any): Error {
  const error: any = new Error(res.message || res.error || 'Request failed');
  error.response = { data: res };
  error.name = 'BizError';
  return error;
}

/**
 * Normalise the two response shapes the backend may send:
 *   1. Bare payload – typical for newer OpenAPI-generated endpoints
 *   2. `{ code, data, message, requestId }` envelope – legacy / debug endpoints
 */
function unwrapResponse<T>(res: any): T {
  if (res && typeof res === 'object' && typeof res.code === 'number') {
    if (res.code !== 0) throw buildBizError(res);
    return res.data as T;
  }
  return res as T;
}

/**
 * Adapt Umi Max `request()` into the standard `(input: Request) =>
 * Promise<Response>` signature `openapi-fetch` expects.
 *
 * This keeps all of the existing interceptors (auth header injection,
 * 401 → login redirect, global Ant Design message.error notifications)
 * intact while giving us full compile-time type safety.
 */
async function umiFetch(request: Request): Promise<Response> {
  const originalUrl = request.url;
  const method = request.method as 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

  const urlObj = new URL(originalUrl, 'http://localhost');
  const pathname = urlObj.pathname;
  const queryEntries = Array.from(urlObj.searchParams.entries());
  const params = queryEntries.length ? Object.fromEntries(queryEntries) : undefined;

  const reqOptions: Record<string, any> = { method };
  if (params) reqOptions.params = params;

  if (method !== 'GET' && method !== 'DELETE') {
    const contentType = request.headers.get('content-type') || '';
    if (contentType.includes('multipart/form-data')) {
      const fd = await request.formData().catch(() => undefined);
      if (fd) reqOptions.data = fd;
    } else if (request.body && contentType.includes('json')) {
      const text = await request.text();
      reqOptions.data = text ? JSON.parse(text) : {};
      reqOptions.requestType = 'json';
    } else if (request.body) {
      const text = await request.text();
      reqOptions.data = text;
    }
  }

  // Pass through silent-flag marker when a caller encodes it as
  // `headers['x-skip-global-error']: '1'` (convention used by service wrappers).
  if (request.headers.get('x-skip-global-error') === '1') {
    reqOptions.skipGlobalError = true;
  }

  try {
    const res: any = await umiRequest(pathname, reqOptions);
    const unwrapped = unwrapResponse<any>(res);
    const responseText =
      unwrapped === undefined || unwrapped === null
        ? ''
        : typeof unwrapped === 'string'
          ? unwrapped
          : JSON.stringify(unwrapped);
    return new Response(responseText, {
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/json; charset=utf-8' },
    });
  } catch (err: any) {
    // Umi's request already surfaces HTTP 4xx/5xx via thrown errors.  Convert
    // back to a Response so openapi-fetch can return the normal `{ error }`
    // tuple; keep `status` honest if available.
    const status = typeof err?.request?.status === 'number' ? err.request.status : 500;
    const bodyStr = JSON.stringify({
      message: err?.message || String(err),
      code: err?.code ?? -1,
    });
    return new Response(bodyStr, {
      status,
      statusText: String(err?.message || 'Error'),
      headers: { 'content-type': 'application/json; charset=utf-8' },
    });
  }
}

/**
 * Primary typed client – tuple-returning, never throws.  Use this directly
 * inside component `useRequest` or server-call helpers when you want fine
 * grained error control.
 *
 * Paths and request/response shapes are 100 % inferred from the OpenAPI spec
 * via `type { paths }` imported above.  Changing `doc/openapi.yaml` +
 * re-running `pnpm openapi` will instantly update what TS reports as valid.
 */
export const client = createClient<paths>({
  baseUrl: '',
  fetch: umiFetch as unknown as (input: Request) => Promise<Response>,
  querySerializer: {
    array: { style: 'form', explode: true }, // ?ids=1&ids=2 — matches Spring default
    object: { style: 'deepObject', explode: true },
  },
});

export default client;
