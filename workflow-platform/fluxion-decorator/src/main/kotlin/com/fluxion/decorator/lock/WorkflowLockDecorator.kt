package com.fluxion.decorator.lock

import com.fluxion.decorator.decorator.WorkflowDecorator
import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.decoratorParams
import com.fluxion.core.value.EngineResult
import org.slf4j.*

/**
 * 宸ヤ綔娴佺骇鍒嗗竷寮忛攣瑁呴グ鍣ㄣ€? *
 * 鍦ㄦ暣娈靛伐浣滄祦鎵ц鍓嶅悗鍔犻攣锛岀‘淇濆悓涓€閿侀敭鐨勫伐浣滄祦璇锋眰涓茶鎵ц銆? * 閫傜敤浜庨渶瑕佽法鑺傜偣銆佽法璋冪敤淇濇寔涓€鑷存€х殑鍦烘櫙锛堝搴撳瓨鎵ｅ噺銆佽鍗曠姸鎬佹満锛夈€? *
 * 閰嶇疆鍙傛暟锛堥€氳繃 [WorkflowDefinition.workflowDecoratorParams] 鐨?"workflow:lock" 閿級锛? * - `lockKey`: 闈欐€侀攣閿紝浼樺厛绾ф渶楂? * - `lockKeyExpression`: 閿侀敭妯℃澘锛屾敮鎸?`${'$'}{path}` 鍗犱綅绗︼紙path 涓虹偣鍙峰祵濂楄矾寰勶級锛屽彲鐢ㄥ彉閲忥細
 *     input      鈥?宸ヤ綔娴佸師濮嬪叆鍙? *     workflowId 鈥?宸ヤ綔娴?ID
 *   渚嬪锛歚"lock:${'$'}{workflowId}:${'$'}{input.userId}"`
 * - `waitMillis`:  鏈€澶х瓑寰呴攣鏃堕棿锛堟绉掞級锛岄粯璁?0锛堟嬁涓嶅埌绔嬪嵆澶辫触锛? * - `leaseMillis`: 閿佹渶澶ф寔鏈夋椂闂达紙姣锛夛紝榛樿 30000
 * - `retry`:       鑾峰彇澶辫触鍚庣殑閲嶈瘯娆℃暟锛岄粯璁?0
 * - `retryIntervalMillis`: 閲嶈瘯闂撮殧锛堟绉掞級锛岄粯璁?100
 * - `sync`:        鏄惁鍚屾闃诲鑾峰彇閿侊紝榛樿 false锛堜负 true 鏃舵嬁涓嶅埌閿佷竴鐩撮樆濉烇紝鎱庣敤锛? * - `failOnLocked`: 鑾峰彇閿佸け璐ユ椂鏄惁鎶涘紓甯革紝榛樿 true
 * - `prefix`:      閿侀敭鍓嶇紑锛岄粯璁?"fluxion:lock:workflow:"
 *
 * 閿侀敭鐢熸垚浼樺厛绾э細lockKey > lockKeyExpression > 榛樿 "{prefix}{workflowId}"
 */
class WorkflowLockDecorator(
    private val lockProvider: DistributedLockProvider
) : WorkflowDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "workflow:lock"

    override suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult {
        val context = LockContext(
            params = def.decoratorParams(name()),
            appGroup = def.appGroup,
            domain = def.id,
            defaultPrefix = "fluxion:lock:workflow:",
            defaultBusinessKey = def.id,
            contextDescription = "workflow [${def.id}]",
            variables = mapOf(
                "input" to rawInput,
                "workflowId" to def.id
            )
        )
        return lockProvider.lockSuspendingWithContext(context, log, execute)
    }
}
