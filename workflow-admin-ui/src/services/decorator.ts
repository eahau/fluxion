import { client, unwrap, silentHeaders } from '@/sdk';
import type { DecoratorDefinition } from '@/types/api';
import type { RequestOptions } from './request';

export async function listDecorators(options?: RequestOptions) {
  return unwrap(
    await client.GET('/api/admin/decorators', { headers: silentHeaders(options) }),
  ) as DecoratorDefinition[];
}
