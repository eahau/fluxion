// Shared/Types：跨 feature/entity 的通用 TS 类型
export interface PaginationMeta {
  page?: number;
  pageSize?: number;
  total?: number;
}

export interface UnifiedResponse<T> {
  code?: number;
  message?: string;
  data?: T;
  success?: boolean;
}

export type ApiSortDir = 'asc' | 'desc';

export * from './function-test-common';
