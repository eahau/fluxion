// features/function-test/index.ts — Dispatcher 层对外 Public API
//
// 这里只暴露 Dispatcher 组件和它的 Props 类型。
// 原 function-test 的业务逻辑（useFunctionTest、ResultTableOutput、TableBrowser 等）
// 已经迁入：
//   - shared/ui/function-test/*         跨 feature 的通用 UI 组件
//   - shared/lib/sql-history.ts         SQL 历史读写
//   - shared/types/function-test-common 公共类型
//   - features/db-function-test/*       DB 专属
//   - features/redis-function-test/*    Redis 专属
//   - features/condition-function-test/* 条件/过滤 专属
//   - features/generic-function-test/*  通用（Script/External 等）

export type { FunctionTestPanelProps } from './ui/FunctionTestPanel';
export { default as FunctionTestPanel } from './ui/FunctionTestPanel';
