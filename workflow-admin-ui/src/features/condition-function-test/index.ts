// features/condition-function-test/index.ts
//
// 职责：条件/过滤函数（builtin:filter、builtin:conditionBranch）测试
// 依赖方向：condition-function-test → entities/* + shared/*（单向，不引其他 feature）

export * from './api';
export * from './lib/utils';
export type { UseConditionTestInput, UseConditionTestResult } from './model/useConditionTest';
export { useConditionTest } from './model/useConditionTest';
export * from './ui';
