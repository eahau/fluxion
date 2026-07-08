/**
 * Public facade for the typed API SDK.
 *
 * Two entry points:
 *
 *   - `client`     – tuple-return client (openapi-fetch runtime).  Never throws,
 *                    returns a discriminated union `{ data, error, response }`.
 *   - `unwrap(...)` – throw-on-error helper to convert the tuple into the legacy
 *                     `Promise<T>` semantics used by the old `apiGet/apiPost` helpers.
 *
 * Typical usage inside a service wrapper (e.g. `src/services/function.ts`):
 *
 * ```ts
 * import { client, unwrap } from '@/sdk';
 *
 * export async function getFunctions(params?) {
 *   return unwrap(
 *     await client.GET('/api/admin/functions', {
 *       params: { query: params }
 *     })
 *   );
 * }
 * ```
 *
 * Re-exports all OpenAPI-generated component schema types as `schemas`, plus
 * the paths type for consumers that want to build additional typed helpers.
 */
import type { paths, components, operations } from '@/types/api.generated';
import { client } from './client';
import type { RequestOptions as LegacyRequestOptions } from '@/services/request';

export { client };
export type { paths, operations };
export type schemas = components['schemas'];
/** Re-exported legacy request options so service/entity wrappers can keep their signatures unchanged. */
export type RequestOptions = LegacyRequestOptions;

/**
 * Convert the openapi-fetch discriminated-union result into a plain
 * `Promise<T>` that throws on error — keeps 100 % compatibility with the
 * legacy `apiGet<T>(url)` service signatures used everywhere before SDK.
 *
 * Error throwing behaviour:
 *   - HTTP 4xx / 5xx → build and throw BizError with `response` + `name='BizError'`
 *   - Backend business envelope `{ code: != 0, ... }` → already converted by the
 *     fetch bridge; the tuple here already has `error` populated.
 */
export function unwrap<T>(
  result: { data?: T; error?: any; response: Response },
): T {
  if (result.error) {
    const error: any = new Error(
      typeof result.error === 'string'
        ? result.error
        : result.error?.message || result.error?.error || 'Request failed',
    );
    error.response = { data: result.error };
    error.name = 'BizError';
    throw error;
  }
  return result.data as T;
}

/**
 * Map the legacy `RequestOptions.silent` flag to the convention the custom
 * fetch recognises (header `x-skip-global-error: 1`).  Spread this into the
 * `headers` of any `client.GET` / `client.POST` call when the caller passes
 * `silent: true`.
 */
export function silentHeaders(options?: { silent?: boolean }): Record<string, string> {
  return options?.silent ? { 'x-skip-global-error': '1' } : {};
}
