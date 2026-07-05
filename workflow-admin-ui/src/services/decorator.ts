import { apiGet } from './request';
import type { DecoratorDefinition } from '@/types/api';
import type { RequestOptions } from './request';

export async function listDecorators(options?: RequestOptions) {
  return apiGet<DecoratorDefinition[]>('/api/admin/decorators', undefined, options);
}
