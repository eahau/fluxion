-- ============================================================
-- V22：清理冗余 Redis 内置函数（除 builtin:redisCommand 外的全部 BUILTIN Redis 单命令函数）
--
-- 背景：此前版本可能通过 DB Seed / 历史迁移遗留了如 builtin:redisGet、builtin:redisExists、
-- builtin:redisHGet 等 30+ 条单命令粒度的内置函数；当前统一通过 builtin:redisCommand +
-- cmd/args 参数覆盖所有 Redis 原生命令，这些单命令函数已废弃不再使用。
--
-- 清理范围：
--   1) 作用域=PLATFORM 且函数类别=BUILTIN 且 domain='redis' 且 function_name 匹配
--      'builtin:redis%' 但 function_name != 'builtin:redisCommand' 的所有行
--   2) 兼容函数名丢失冒号的异常情况：'builtinredis%'（除 builtinredisCommand 外）
--
-- 影响范围：仅 wf_function 表管理展示数据，不触碰执行期 FunctionRegistry（执行期已仅
--           注册 builtin:redisCommand）。
-- ============================================================

DELETE FROM `wf_function`
WHERE
  (
    (`function_name` LIKE 'builtin:redis%' AND `function_name` <> 'builtin:redisCommand')
    OR
    (`function_name` LIKE 'builtinredis%' AND `function_name` <> 'builtinredisCommand')
  );
