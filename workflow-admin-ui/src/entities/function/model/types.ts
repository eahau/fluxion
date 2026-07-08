// Entities/Function Model Types（与实体强绑定的核心数据结构）
import type {
  FunctionCategory,
  FunctionNodeType,
  FunctionStatus,
  FunctionDefinition,
} from '@/types/api';

export type { FunctionCategory, FunctionNodeType, FunctionStatus, FunctionDefinition };

/** 实体列表页用到的 Brief 类型（避免直接依赖完整 Definition） */
export interface FunctionBrief {
  id: string;
  name: string;
  description?: string;
  domain?: FunctionCategory;
  status?: FunctionStatus;
  version?: string;
  updatedAt?: number;
}

/** 实体内部用的 paramSchema 原始格式：字符串或对象，都由后端返回 */
export type FunctionParamSchema = Record<string, any> | string | null | undefined;
