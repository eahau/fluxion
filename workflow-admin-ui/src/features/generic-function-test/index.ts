// features/generic-function-test/index.ts
//
// 职责：通用函数测试（Script 脚本、External HTTP 等非 DB/Redis/Condition 类型）
// 依赖方向：generic-function-test → entities/* + shared/*（单向，不引其他 feature）

export * from './api';
export type { UseGenericTestInput, UseGenericTestResult } from './model/useGenericTest';
export { useGenericTest } from './model/useGenericTest';
export * from './ui';
