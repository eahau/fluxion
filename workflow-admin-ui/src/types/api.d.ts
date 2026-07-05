/**
 * 全局 API 类型声明
 * 从 OpenAPI 规范自动生成的类型 re-export
 */
import type { ApiResponse, PageResponse, CurrentUser } from './api';

declare global {
  namespace API {
    export type { ApiResponse, PageResponse, CurrentUser };
  }
}

export {};
