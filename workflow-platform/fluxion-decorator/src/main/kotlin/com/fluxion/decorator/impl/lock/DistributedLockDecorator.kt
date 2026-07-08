package com.fluxion.decorator.impl.lock

import com.fluxion.decorator.decorator.NodeDecorator
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.model.decoratorParams
import com.fluxion.decorator.lock.LockContext
import com.fluxion.decorator.lock.lockWithContext
import org.slf4j.*

/**
 * 鍒嗗竷寮忛攣瑁呴グ鍣?鈥?鍑芥暟绾т簰鏂ャ€? *
 * 鍦ㄨ妭鐐规墽琛屽墠灏濊瘯鑾峰彇鍒嗗竷寮忛攣锛屾墽琛屽悗锛堟棤璁烘垚鍔熸垨澶辫触锛夐噴鏀俱€? * 閫傜敤浜庡鍚屼竴鍑芥暟鎴栧悓涓€涓氬姟閿殑骞跺彂璋冪敤杩涜涓茶鍖栥€? *
 * 閰嶇疆鍙傛暟锛堥€氳繃 [WorkflowNode.decoratorParams] 鐨?"lock:distributed" 閿級锛? * - `lockKey`: 闈欐€侀攣閿紝浼樺厛绾ф渶楂? * - `lockKeyExpression`: 閿侀敭妯℃澘锛屾敮鎸?`${'$'}{path}` 鍗犱綅绗︼紙path 涓虹偣鍙峰祵濂楄矾寰勶級锛屽彲鐢ㄥ彉閲忥細
 *     input        鈥?褰撳墠鑺傜偣鐨?directInput
 *     nodeId       鈥?鑺傜偣 ID
 *     nodeName     鈥?鑺傜偣鍚嶇О
 *     workflowId   鈥?宸ヤ綔娴?ID
 *     executionId  鈥?鎵ц ID
 *     functionRef  鈥?鍑芥暟寮曠敤
 *   渚嬪锛歚"lock:${'$'}{workflowId}:${'$'}{nodeId}"`
 * - `waitMillis`:  鏈€澶х瓑寰呴攣鏃堕棿锛堟绉掞級锛岄粯璁?0锛堟嬁涓嶅埌绔嬪嵆澶辫触锛? * - `leaseMillis`: 閿佹渶澶ф寔鏈夋椂闂达紙姣锛夛紝榛樿 30000
 * - `retry`:       鑾峰彇澶辫触鍚庣殑閲嶈瘯娆℃暟锛岄粯璁?0
 * - `retryIntervalMillis`: 閲嶈瘯闂撮殧锛堟绉掞級锛岄粯璁?100
 * - `sync`:        鏄惁鍚屾闃诲鑾峰彇閿侊紝榛樿 false锛堜负 true 鏃舵嬁涓嶅埌閿佷竴鐩撮樆濉烇紝鎱庣敤锛? * - `failOnLocked`: 鑾峰彇閿佸け璐ユ椂鏄惁鎶涘紓甯革紝榛樿 true
 * - `prefix`:      閿侀敭鍓嶇紑锛岄粯璁?"fluxion:lock:"
 *
 * 閿侀敭鐢熸垚浼樺厛绾э細lockKey > lockKeyExpression > 榛樿 "{prefix}{nodeId}"
 */
class DistributedLockDecorator(
    private val lockProvider: DistributedLockProvider
) : NodeDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "lock:distributed"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> {
        return WorkflowFunction { input ->
            val context = LockContext(
                params = node.decoratorParams(name()),
                appGroup = node.appGroup,
                domain = node.workflowId,
                defaultPrefix = "fluxion:lock:",
                defaultBusinessKey = node.id,
                contextDescription = "node [${node.name}]",
                variables = mapOf(
                    "input" to input.directInput,
                    "nodeId" to node.id,
                    "nodeName" to node.name,
                    "workflowId" to node.workflowId,
                    "executionId" to (input.meta?.executionId ?: ""),
                    "functionRef" to node.functionRef
                )
            )
            lockProvider.lockWithContext(context, log) { function.apply(input) }
        }
    }
}
