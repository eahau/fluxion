// features/index.ts — Features Layer Public API
//
// 按功能垂直切片（Feature-Sliced Design）：
//   - features/function-test          Dispatcher 统一入口（供 pages/* 直接使用）
//   - features/db-function-test       DB 函数测试（SQL）
//   - features/redis-function-test    Redis 函数测试
//   - features/condition-function-test 条件/过滤函数测试
//   - features/generic-function-test  通用函数测试（Script / External HTTP / 其他自定义）
//
// 依赖方向（FSD 规则）：
//   pages/*        → features/*（单向，pages 不能反向被引）
//   features/*     → entities/* + shared/*（单向，feature 之间默认不互相 import）
//   但 function-test 这个 Dispatcher 例外：它的职责就是组合其他 feature。

export * from './function-test';
export * from './db-function-test';
export * from './redis-function-test';
export * from './condition-function-test';
export * from './generic-function-test';
