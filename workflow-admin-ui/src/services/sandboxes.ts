import { client, unwrap, silentHeaders } from '@/sdk';
import type { RequestOptions } from './request';
import type { schemas } from '@/sdk';

export type SandboxTicket = schemas['SandboxTicket'];
export type SandboxAcquireRequest = schemas['SandboxAcquireRequest'];

export async function acquireSandbox(req: SandboxAcquireRequest, options?: RequestOptions) {
  return unwrap(
    await client.POST('/api/admin/internal/debug/sandbox/acquire', {
      body: req as any,
      headers: silentHeaders(options),
    }),
  ) as SandboxTicket;
}

export async function releaseSandbox(id: number, options?: RequestOptions) {
  unwrap(
    await client.DELETE('/api/admin/internal/debug/sandbox/{id}', {
      params: { path: { id } },
      headers: silentHeaders(options),
    }),
  );
  return undefined as void;
}

export async function releaseSandboxSession(sessionId: string, options?: RequestOptions) {
  unwrap(
    await client.DELETE('/api/admin/internal/debug/sandbox/session/{sessionId}', {
      params: { path: { sessionId } },
      headers: silentHeaders(options),
    }),
  );
  return undefined as void;
}
