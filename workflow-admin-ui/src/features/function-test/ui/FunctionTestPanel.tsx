// features/function-test/ui/FunctionTestPanel.tsx — Dispatcher
//
// 本文件是函数测试的统一入口（Dispatcher / Facade），不再承载任何业务逻辑。
// 职责：根据 functionId / domain / paramSchema 特征，把渲染分发给对应的垂直切片 feature：
//   - condition-function-test：builtin:filter / builtin:conditionBranch
//   - db-function-test：domain=db 或含 sql 字段
//   - redis-function-test：含 redis 命令 Schema
//   - generic-function-test：兜底（Script、External HTTP、其他自定义）
//
// 依赖约束：这里允许 import 其他 feature（因为 Dispatcher 本质上就是跨 feature 的组合层），
// 但它不实现任何业务逻辑，仅做"类型判断 → 组件分发"。
// —— 新增的其他函数测试种类请新建独立 feature 并在此加入分发规则。
//
// 容错保障：所有子 feature 用 React.lazy 做动态 import，
// 单个子 feature 模块初始化 throw / No matching export / 语法错，只影响它自己，
// 不会把整个 editor 路由 chunk 一起挂死（避免"函数详情一直转圈"）。

import React, { Suspense, useMemo } from 'react';
import { Spin } from 'antd';
import type { FunctionDefinition } from '@/types/function';
import { isConditionFunction } from '@/utils/conditionSchema';
import { hasSqlField, isRedisCommandSchema, parseSchema } from '@/shared/lib/json-schema';
import ComponentErrorBoundary from '@/components/error-boundaries/ComponentErrorBoundary';

const LazyDbTestPanel = React.lazy(
  () => import('@/features/db-function-test/ui/DbTestPanel'),
);
const LazyRedisTestPanel = React.lazy(
  () => import('@/features/redis-function-test/ui/RedisTestPanel'),
);
const LazyConditionTestPanel = React.lazy(
  () => import('@/features/condition-function-test/ui/ConditionTestPanel'),
);
const LazyGenericTestPanel = React.lazy(
  () => import('@/features/generic-function-test/ui/GenericTestPanel'),
);

export interface FunctionTestPanelProps {
  functionId: string;
  functionDefinition?: FunctionDefinition | null;
  mode?: 'function' | 'node';
  /** 节点测试时，若入参 schema 已由上游确定，传入后条件测试面板将锁定选择 */
  fixedInputSchema?: Record<string, any>;
  onExecuteTest?: (payload: { inputs: any }) => Promise<any>;
  /** 节点当前配置的参数（如 filter 的 rules），用于预填充测试面板 */
  nodeParams?: Record<string, any>;
  /** paramValidate 的锁定校验 Schema（只读展示），测试输入基于此 schema 生成 */
  lockedSchema?: Record<string, any>;
}

type TestKind = 'condition' | 'db' | 'redis' | 'generic';

function resolveKind(
  functionId: string,
  functionDefinition: FunctionDefinition | null | undefined,
): TestKind {
  if (isConditionFunction(functionId)) return 'condition';

  const domain = (functionDefinition?.config as any)?.domain as string | undefined;
  const rawParamSchema = functionDefinition?.config?.paramSchema;
  const paramSchema = parseSchema(rawParamSchema) as Record<string, any> | null;

  if (domain === 'db' || hasSqlField(paramSchema as any)) return 'db';
  if (isRedisCommandSchema(paramSchema as any)) return 'redis';
  return 'generic';
}

function withBoundaryAndSuspense(
  componentName: string,
  Comp: React.LazyExoticComponent<React.ComponentType<any>>,
  props: any,
): React.ReactElement {
  return (
    <ComponentErrorBoundary componentName={componentName}>
      <Suspense
        fallback={
          <div style={{ minHeight: 180, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Spin />
          </div>
        }
      >
        <Comp {...props} />
      </Suspense>
    </ComponentErrorBoundary>
  );
}

const FunctionTestPanelInner: React.FC<FunctionTestPanelProps> = ({
  functionId,
  functionDefinition,
  fixedInputSchema,
  onExecuteTest,
  nodeParams,
  lockedSchema,
}) => {
  const kind = useMemo<TestKind>(
    () => resolveKind(functionId, functionDefinition),
    [functionId, functionDefinition],
  );

  const sharedProps = { functionId, functionDefinition, fixedInputSchema, onExecuteTest, nodeParams, lockedSchema };

  switch (kind) {
    case 'condition':
      return withBoundaryAndSuspense(
        `条件/过滤函数测试（${functionId || '未命名'}）`,
        LazyConditionTestPanel,
        sharedProps,
      );
    case 'db':
      return withBoundaryAndSuspense(
        `DB 函数测试（${functionId || '未命名'}）`,
        LazyDbTestPanel,
        sharedProps,
      );
    case 'redis':
      return withBoundaryAndSuspense(
        `Redis 函数测试（${functionId || '未命名'}）`,
        LazyRedisTestPanel,
        sharedProps,
      );
    case 'generic':
    default:
      return withBoundaryAndSuspense(
        `通用函数测试（${functionId || '未命名'}）`,
        LazyGenericTestPanel,
        sharedProps,
      );
  }
};

const FunctionTestPanel: React.FC<FunctionTestPanelProps> = (props) => (
  <ComponentErrorBoundary componentName={`函数测试面板（${props.functionId || '未命名'}）`}>
    <FunctionTestPanelInner {...props} />
  </ComponentErrorBoundary>
);

export default FunctionTestPanel;
