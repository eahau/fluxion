package com.fluxion.admin.meta

import com.fluxion.core.value.FunctionMeta
import com.fluxion.functionmeta.api.FunctionMetaRegistry
import com.fluxion.admin.service.WfFunctionService
import org.slf4j.LoggerFactory
import org.slf4j.debug

/**
 * 閸╄桨绨弫鐗堝祦鎼?`wf_function` 閻?[FunctionMetaRegistry] 鐎圭偟骞囬敍鍦揹min 缁狅紕鎮婇棃顫礆閵?
 *
 * 閺佺増宓佸ù浣告倻閿涘牅绗岄悽銊﹀煕缁撅箑鐣炬稉鈧懛杈剧窗**DB 婵绮撻崘娆忓弳閿涘矂鍘ょ純顔昏厬韫囧啩绡冮崣鎴濈**閿涘绱?
 * - **閸?*閿涙艾鍤遍弫鏉垮晸閹垮秳缍旂紒?[WfFunctionService]閿涘潉save` / `publish` / `deprecate`閿涘鎯ゆ惔?`wf_function`閿?
 *   閸楃偨鈧瓕B 婵绮撻崘娆忓弳閵嗗秶娈戦惇鐔烘祲濠ф劧绱?
 * - **鐠?*閿涙碍婀板▔銊ュ斀鐞涖劋绮?`wf_function` 鐠囪褰囬敍灞肩稊娑?[com.fluxion.functionmeta.api.FunctionMetaManager]
 *   閸?Admin 娓氀呮畱閺佺増宓佸┃鎰剁幢
 * - **閸欐垵绔?*閿涙瓟WfFunctionService] 閸︺劌鍟撴惔鎾虫倵闁俺绻?`FunctionConfigPublisher` 閹?FunctionMeta 閹恒劌鍩岄柊宥囩枂娑擃厼绺鹃敍?
 *   娓?Worker 娓?`FunctionMetaConfigApplier` 鐠併垽妲勯敍鍫濆祮閵嗗矂鍘ょ純顔昏厬韫囧啩绡冮崣鎴濈閵嗗稄绱氶妴?
 *
 * 閻㈠彉绨?DB 閺?FunctionMeta 閻?SSOT閿涘本婀扮€圭偟骞囨稉宥嗘暜閹?`register` / `unregister` 閻╁瓨甯撮崘娆忓敶鐎?
 * 閿涘牆鍟撻崗銉ョ安鐠?[WfFunctionService] 閽€钘夌氨閿涘绱濆銈咁槱娑?no-op閵?
 */
class JdbcFunctionMetaRegistry(
    private val functionService: WfFunctionService
) : FunctionMetaRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun get(name: String, version: Long?): FunctionMeta? =
        functionService.getSnapshot(name)

    override fun list(): List<FunctionMeta> =
        functionService.loadAllEnabledSnapshots()

    override fun listVersions(name: String): List<Long> = emptyList()

    override fun register(meta: FunctionMeta) {
        // DB 閺?SSOT閿涘苯鍟撻崗銉ф暠 WfFunctionService 鐠愮喕鐭楅敍娑欘劃婢跺嫬鎷烽悾銉礄no-op閿?
        log.debug { "JdbcFunctionMetaRegistry.register ignored (DB is SSOT): name=${meta.functionName}" }
    }

    override fun unregister(name: String): Boolean {
        log.debug { "JdbcFunctionMetaRegistry.unregister ignored (DB is SSOT): name=$name" }
        return false
    }

    override fun clear() {
        // no-op
    }
}
