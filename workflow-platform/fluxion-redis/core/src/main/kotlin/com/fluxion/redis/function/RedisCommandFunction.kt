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
 * Built-in workflow function `builtin:redisCommand` that exposes the Redis command
 * surface authored through the admin designer.
 *
 * Two configuration modes are supported and disambiguated at runtime:
 *   1. **Structured** — callers pick a command from an enum dropdown and populate
 *      individual fields `command` / `key` / `args` / `script` / `keys` / `commands`.
 *   2. **Raw**       — callers fill in `raw` as a free-form Redis CLI string which
 *      is then tokenized, template-rendered, and dispatched.
 *
 * The `key` (and each item in `args` / `keys`) accepts either a structured binding
 * map (`{"$ref": "input.userId", "prefix": "user:"}`) or a template string with
 * `${var}` placeholders; both forms are resolved through `NodeInput.resolveBinding`.
 */
class RedisCommandFunction(
    private val redisAdapter: RedisClientAdapter
) : WorkflowFunction<Any> {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Dispatch a Redis invocation based on the author-provided parameter shape.
     *
     * Supported execution paths: raw CLI string → structured PIPELINE batch →
     * structured EVAL script → normal single command (default).
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

    // ─── Raw CLI string parsing ────────────────────────────────────────────────

    /**
     * Execute a raw Redis CLI style string provided via the `raw` parameter.
     *
     * The raw string is first run through `NodeInput.resolveBinding` to expand
     * `${var}` templates and structured bindings; the remainder is then split into
     * tokens respecting double/single quoting and backslash escape sequences.
     *
     * Token interpretation:
     *   - tokens[0]   → command
     *   - tokens[1]   → key (except for EVAL)
     *   - tokens[2+]  → positional args
     *
     * For EVAL the raw token stream follows `EVAL script numkeys key [key ...] arg [arg ...]`
     * and is routed through a dedicated parser to correctly split KEYS / ARGV.
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
     * Split a Redis CLI style string into tokens respecting quoted substrings and
     * backslash escapes.
     *
     * Examples:
     *   `SET user:1001 name "John Doe"` → `[SET, user:1001, name, John Doe]`
     *   `EVAL "return KEYS[1]" 1 key1 arg1` → `[EVAL, return KEYS[1], 1, key1, arg1]`
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
     * Parse an EVAL token stream generated from raw mode and execute it.
     * Expected token order: `EVAL script numkeys key [key ...] arg [arg ...]`.
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

    // ─── Key resolution ────────────────────────────────────────────────────────

    /**
     * Resolve the `key` parameter for structured invocations.
     *
     * Accepts either a Map (structured binding `$ref` + prefix/suffix) or a raw String
     * with `${var}` template placeholders; both are delegated to `NodeInput.resolveBinding`.
     * If any `${...}` placeholder survives resolution a warning is logged so workflow
     * authors can track down missing inputs.
     */
    private fun resolveKeyParam(rawValue: Any?, input: NodeInput): String {
        if (rawValue == null) return ""
        val resolved = input.resolveBinding(rawValue) ?: ""
        if (resolved.contains("\${")) {
            log.warn { "Redis key contains unresolved template placeholders: [$resolved]" }
        }
        return resolved
    }

    // ─── Args resolution ───────────────────────────────────────────────────────

    /**
     * Resolve the `args` list; each element may be a plain literal, a template string
     * or a structured binding map.
     */
    private fun resolveArgsList(rawValue: Any?, input: NodeInput): List<String> {
        if (rawValue !is List<*>) return emptyList()
        return rawValue.map { item ->
            input.resolveBinding(item) ?: ""
        }
    }

    // ─── Pipeline ──────────────────────────────────────────────────────────────

    /** Execute a structured `commands` batch through the adapter pipeline API. */
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

    // ─── EVAL (Lua) ────────────────────────────────────────────────────────────

    /** Execute a structured Lua script invocation (EVAL command path). */
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

    /** Wrap a user-provided key with `{appGroup:workflowId}:hash-tag` when not already tagged. */
    private fun wrapKey(input: NodeInput, key: String): String =
        RedisKey.wrapIfNeeded(input.meta?.appGroup, input.meta?.workflowId ?: "", key)
}
