/**
 * API 类型定义
 * 从 OpenAPI 规范自动生成的类型
 */
import type { components } from './api.generated';

// ============ 通用类型 ============
export type ApiResponse<T> = {
  code: number;
  data: T;
  message: string;
  requestId: string;
};

export type PageResponse<T> = {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
};

export type CurrentUser = components['schemas']['CurrentUser'] & {
  access: NonNullable<components['schemas']['UserAccess']>;
};

// ============ Workflow 类型 ============
export type NodeType = components['schemas']['NodeType'];
export type ErrorStrategy = components['schemas']['ErrorStrategy'];
export type WorkflowCategory = components['schemas']['WorkflowCategory'];
export type WorkflowScope = components['schemas']['WorkflowScope'];
export type WorkflowStatus = components['schemas']['WorkflowStatus'];

export type WorkflowNode = components['schemas']['WorkflowNode'];
export type WorkflowDefinition = components['schemas']['WorkflowDefinition'];
export type NodeTrace = components['schemas']['NodeTrace'];
export type DebugResult = components['schemas']['DebugResult'];
export type DebugExecutionRecord = components['schemas']['DebugExecutionRecord'];

export type MockConfig = components['schemas']['MockConfig'];
export type MockRule = components['schemas']['MockRule'];
export type MockCondition = components['schemas']['MockCondition'];
export type FieldMatcher = components['schemas']['FieldMatcher'];

export type DecoratorDefinition = components['schemas']['DecoratorDefinition'];

// ============ Schema 类型 ============
/** 单个 Schema 类型标签值 */
export type SchemaTypeTag = 'INPUT' | 'OUTPUT' | 'EVENT';

export interface SchemaDefinition {
  id?: string | number;
  name?: string;
  schemaName?: string;
  /** 类型标签（逗号分隔字符串，如 "INPUT,OUTPUT"），支持多类型复用 */
  schemaType?: string;
  /** Schema 格式：json-schema / protobuf / avro */
  schemaFormat: 'json-schema' | 'protobuf' | 'avro';
  schema?: any;
  schemaJson?: string;
  description?: string | null;
  /** 是否已冻结（锁定），冻结后普通用户不可修改/删除 */
  frozen?: boolean;
  /** 作用域：PLATFORM/PRIVATE */
  scope?: string;
  /** 所属应用分组（scope=PRIVATE 时必填） */
  appGroup?: string;
  version?: number;
  refCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

// ============ Function 类型 ============
export type FunctionCategory = components['schemas']['FunctionCategory'];
export type FunctionNodeType = components['schemas']['FunctionNodeType'];
export type FunctionStatus = components['schemas']['FunctionStatus'];
export type FunctionDefinition = components['schemas']['FunctionDefinition'];

// ============ Instance / Publish 类型 ============
export type InstanceInfo = components['schemas']['InstanceInfo'];
export type AppGroup = components['schemas']['AppGroup'];
export type PublishTarget = components['schemas']['PublishTarget'];
export type PublishType = components['schemas']['PublishType'];

// ============ Monitor 类型 ============
export type MetricsOverview = components['schemas']['MetricsOverview'];
export type NodeLatency = components['schemas']['NodeLatency'];
export type ErrorLog = components['schemas']['ErrorLog'];
export type TraceDetail = components['schemas']['TraceDetail'];
export type TraceNode = components['schemas']['TraceNode'];
export type MetricsTrend = components['schemas']['MetricsTrend'];
export type TrendPoint = components['schemas']['TrendPoint'];
export type ExecutionRecord = components['schemas']['ExecutionRecord'];
