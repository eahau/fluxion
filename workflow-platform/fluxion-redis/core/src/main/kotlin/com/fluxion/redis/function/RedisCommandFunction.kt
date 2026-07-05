package com.fluxion.redis.function

import com.fluxion.redis.meta.RedisFunctionMetas

import com.fluxion.core.exception.InvalidParamException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionResult
import com.fluxion.core.redis.RedisKey
import com.fluxion.redis.spi.RedisClientAdapter
import com.fluxion.redis.spi.RedisRawCommand
import org.slf4j.*

/**
 * 通用 Redis 命令函数（builtin:redisCommand）
 *
 * 支持两种配置方式：
 *   1. 结构化参数：选择 command + 填写 key/args/script/keys/commands
 *   2. 原始命令行：在 raw 字段直接输入 Redis CLI 风格完整命令
 *
 * key 参数支持两种格式：
 *   1. 结构化绑定：{"$ref": "input.userId", "prefix": "user:"}
 *   2. 模板字符串："user:${username}:${password}"（${var} 自动解析）
 */
class RedisCommandFunction(
    private val redisAdapter: RedisClientAdapter
) : WorkflowFunction<Any> {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 执行 Redis 命令。
     *
     * 支持命令：GET/SET/DEL/... 常规命令，PIPELINE 批量执行，EVAL Lua 脚本，
     * 以及 raw 原始命令行直接输入。
     */
    override fun apply(input: NodeInput): FunctionResult<Any> {
        val result: Any? = if (input.param<String>("raw")?.isNotBlank() == true) {
            executeRawCommand(input)
        } else {
            when (val command: String = input.requireParam<String>("command").uppercase()) {
                "PIPELINE" -> executePipeline(input)
                "EVAL" -> executeEval(input)
                else -> {
                    val resolvedKey = resolveKeyParam(input.param<Any?>("key"), input)
                        .let { wrapKey(input, it) }
                    val args = resolveArgsList(input.param<Any?>("args"), input)
                    log.debug { "redisCommand [$command] key=[$resolvedKey] args=$args" }
                    redisAdapter.execute(command, resolvedKey, args)
                }
            }
        }

        return FunctionResult.success(result).uncheckedCast<FunctionResult<Any>>()!!
    }

    override fun meta() = RedisFunctionMetas.REDIS_COMMAND

    // ─── Raw 命令行解析 ───────────────────────────────────────────

    /**
     * 执行原始 Redis CLI 风格命令行。
     *
     * raw 字符串先经过 [NodeInput.resolveBinding] 解析 ${var} 模板变量，
     * 再按 Redis CLI 规则拆分为 token：
     * - 空格分隔普通 token
     * - 双引号 / 单引号包裹含空格的 token
     * - 反斜杠转义
     *
     * 解析结果：
     * - 第一词作为 command
     * - 第二词作为 key（EVAL 除外）
     * - 其余作为 args
     *
     * EVAL 特殊处理：tokens 顺序为 EVAL script numkeys key [key ...] arg [arg ...]
     */
    private fun executeRawCommand(input: NodeInput): Any? {
        val rawTemplate: String = input.requireParam("raw")
        val resolved = input.resolveBinding(rawTemplate) ?: ""
        if (resolved.isBlank()) {
            throw InvalidParamException("raw command is empty after template resolution")
        }
        if (resolved.contains("\${")) {
            log.warn { "Redis raw command contains unresolved template placeholders: [$resolved]" }
        }

        val tokens = tokenizeRedisCli(resolved)
        if (tokens.isEmpty()) {
            throw InvalidParamException("raw command contains no tokens")
        }

        return when (val command = tokens[0].uppercase()) {
            "PIPELINE" -> throw InvalidParamException(
                "PIPELINE is not supported in raw mode, use structured 'commands' param"
            )
            "EVAL" -> parseAndExecuteEval(tokens, input)
            else -> {
                val key = if (tokens.size > 1) tokens[1] else ""
                val args = if (tokens.size > 2) tokens.subList(2, tokens.size) else emptyList()
                val wrappedKey = wrapKey(input, key)
                log.debug { "redisCommand raw [$command] key=[$wrappedKey] args=$args" }
                redisAdapter.execute(command, wrappedKey, args)
            }
        }
    }

    /**
     * 按 Redis CLI 风格拆分命令行字符串。
     *
     * 支持双引号 / 单引号包裹含空格的 token，以及反斜杠转义。
     * 示例：
     *   SET user:1001 name "John Doe" -> [SET, user:1001, name, John Doe]
     *   EVAL "return KEYS[1]" 1 key1 arg1 -> [EVAL, return KEYS[1], 1, key1, arg1]
     */
    private fun tokenizeRedisCli(raw: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false

        for (ch in raw) {
            when {
                escaped -> {
                    sb.append(ch)
                    escaped = false
                }
                ch == '\\' -> escaped = true
                !inDoubleQuote && !inSingleQuote && ch == '\'' -> inSingleQuote = true
                !inDoubleQuote && !inSingleQuote && ch == '"' -> inDoubleQuote = true
                inSingleQuote && ch == '\'' -> inSingleQuote = false
                inDoubleQuote && ch == '"' -> inDoubleQuote = false
                !inDoubleQuote && !inSingleQuote && ch.isWhitespace() -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        return tokens
    }

    /**
     * 解析 raw 模式下的 EVAL 命令。
     * 格式：EVAL script numkeys key [key ...] arg [arg ...]
     */
    private fun parseAndExecuteEval(tokens: List<String>, input: NodeInput): Any? {
        if (tokens.size < 4) {
            throw InvalidParamException("EVAL raw command requires: EVAL script numkeys key [key ...] arg [arg ...]")
        }
        val script = tokens[1]
        val numKeys = tokens[2].toIntOrNull()
            ?: throw InvalidParamException("EVAL numkeys must be an integer, got [${tokens[2]}]")

        val keyStart = 3
        val keyEnd = keyStart + numKeys
        if (keyEnd > tokens.size) {
            throw InvalidParamException(
                "EVAL raw command has insufficient keys: expected $numKeys keys, got ${tokens.size - keyStart}"
            )
        }
        val keys = tokens.subList(keyStart, keyEnd).map { wrapKey(input, it) }
        val args = if (keyEnd < tokens.size) tokens.subList(keyEnd, tokens.size) else emptyList()
        log.debug { "redisCommand raw EVAL keys=$keys args=$args" }
        return redisAdapter.eval(script, keys, args)
    }

    // ─── Key 解析 ────────────────────────────────────────────────

    /**
     * 解析 key 参数，统一委托 NodeInput.resolveBinding：
     * - Map → 结构化绑定（$ref + prefix/suffix）
     * - String → ${var} 模板插值
     *
     * 解析后若 key 仍包含未解析的 ${...} 占位符，记录 warn 日志辅助排查。
     */
    private fun resolveKeyParam(rawValue: Any?, input: NodeInput): String {
        if (rawValue == null) return ""
        val resolved = input.resolveBinding(rawValue) ?: ""
        if (resolved.contains("\${")) {
            log.warn { "Redis key contains unresolved template placeholders: [$resolved]" }
        }
        return resolved
    }

    // ─── Args 解析 ──────────────────────────────────────────────

    /**
     * 解析 args 列表，每个元素可以是：
     * - 字面量字符串："fieldValue"
     * - 模板字符串："user:${userId}"
     * - 绑定对象：{"$ref": "input.userName"}
     */
    private fun resolveArgsList(rawValue: Any?, input: NodeInput): List<String> {
        if (rawValue !is List<*>) return emptyList()
        return rawValue.map { item ->
            input.resolveBinding(item) ?: ""
        }
    }

    // ─── Pipeline ─────────────────────────────────────────────

    /** 执行 Redis Pipeline 批量命令（原子性发送，减少网络往返） */
    private fun executePipeline(input: NodeInput): List<Any?> {
        val cmdsParam: Any? = input.param("commands")
        require(cmdsParam is List<*>) { "PIPELINE command requires 'commands' param as List" }

        val commands = cmdsParam.mapNotNull { item ->
            if (item is Map<*, *>) {
                val cmdMap = item.uncheckedCast<Map<String, Any>>()!!
                val cmd = cmdMap.getOrDefault("command", "").toString().uppercase()
                val key = resolveKeyParam(cmdMap["key"], input).let { wrapKey(input, it) }
                val args = resolveArgsList(cmdMap["args"], input)
                RedisRawCommand.of(cmd, key, args)
            } else null
        }
        log.debug { "redisCommand PIPELINE (${commands.size} commands)" }
        return redisAdapter.pipeline(commands)
    }

    // ─── EVAL (Lua) ──────────────────────────────────────────

    /** 执行 Lua 脚本（EVAL 命令） */
    private fun executeEval(input: NodeInput): Any? {
        val script: String = input.requireParam("script")

        val keysRaw: Any? = input.param("keys")
        val keys = if (keysRaw is List<*>) {
            keysRaw.map { input.resolveBinding(it) ?: "" }.map { wrapKey(input, it) }
        } else emptyList()

        val args = resolveArgsList(input.param("args"), input)

        log.debug { "redisCommand EVAL keys=$keys args=$args" }
        return redisAdapter.eval(script, keys, args)
    }

    /** 按 Redis Key 命名规范包装用户配置的 key（若已带 hash tag 则保持原样）。 */
    private fun wrapKey(input: NodeInput, key: String): String =
        RedisKey.wrapIfNeeded(input.meta?.appGroup, input.meta?.workflowId ?: "", key)
}
