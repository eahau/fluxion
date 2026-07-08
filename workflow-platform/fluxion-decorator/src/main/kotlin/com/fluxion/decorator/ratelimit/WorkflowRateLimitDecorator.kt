package com.fluxion.decorator.ratelimit

import com.fluxion.decorator.decorator.WorkflowDecorator
import com.fluxion.core.exception.RateLimitExceededException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.decoratorParams
import com.fluxion.core.model.intParam
import com.fluxion.core.model.stringParam
import com.fluxion.core.redis.RedisKey
import com.fluxion.core.value.EngineResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 瀹搞儰缍斿ù浣洪獓闂勬劖绁︾憗鍛淬偘閸ｃ劊鈧? *
 * 鐎佃鏆ｆ稉顏勪紣娴ｆ粍绁﹂惃鍕⒔鐞涘矂顣堕悳鍥箻鐞涘矂妾洪崚璁圭礉闁倻鏁ゆ禍?API 閸忋儱褰涚痪褎涓嶉悶鍡楁簚閺? *閿涘牆顩ч妴灞剧槨閸掑棝鎸撻張鈧径?1000 濞嗏€冲灡瀵ら缚顓归崡鏇樷偓宥忕礆閵? *
 * 娑撳骸鍤遍弫鎵獓 [com.fluxion.decorator.impl.RateLimitDecorator] 閻ㄥ嫬灏崚顐窗
 * - 閸戣姤鏆熺痪褔妾洪崚璺哄礋娑擃亣濡悙鍦畱鐠嬪啰鏁ゆ０鎴犲芳閿? * - 瀹搞儰缍斿ù浣洪獓闂勬劕鍩楅弫瀛橆唽瀹搞儰缍斿ù浣筋潶鐟欙箑褰傞惃鍕暥閻滃洢鈧? *
 * 闁板秶鐤嗛崣鍌涙殶閿涘牓鈧俺绻?[WorkflowDefinition.workflowDecoratorParams] 閻?"workflow:ratelimit" 闁款噯绱氶敍? * - `upLimited`:     缁愭褰涢崘鍛付婢堆嗩嚞濮瑰倹鏆熼敍宀勭帛鐠?6
 * - `cdSeconds`:     閹垹顦查崨銊︽埂閿涘牏顫楅敍澶涚礉姒涙顓?10
 * - `recoveryPerCd`: 濮ｅ繋閲滈崨銊︽埂閹垹顦查惃鍕姢閻楀本鏆熼敍宀勭帛鐠併倗鐡戞禍?upLimited
 * - `rateLimitKey`:  閺勬儳绱￠梽鎰ウ闁款噯绱濇导妯哄帥缁狙勬付妤? * - `keyPrefix`:     闁款喖澧犵紓鈧敍宀勭帛鐠?"fluxion:ratelimit:workflow:"
 *
 * 闂勬劖绁﹂柨顔炬晸閹存劒绱崗鍫㈤獓閿涙ateLimitKey > 姒涙顓?"{prefix}{workflowId}"
 */
class WorkflowRateLimitDecorator(
    private val store: RateLimitStore
) : WorkflowDecorator {

    override fun name(): String = "workflow:ratelimit"

    override suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult {
        val params = def.decoratorParams(name())
        val config = resolveConfig(params)
        val key = resolveKey(def, params)

        val allowed = withContext(Dispatchers.IO) { store.tryAcquire(key, config) }
        if (!allowed) {
            throw RateLimitExceededException(
                "Workflow [${def.id}] rate limit exceeded (upLimited=${config.upLimited}, cdSeconds=${config.cdSeconds})"
            )
        }
        return execute()
    }

    private fun resolveConfig(params: Map<String, Any>?): RateLimitConfig {
        val upLimited = params.intParam("upLimited", RateLimitConfig.DEFAULT_UP_LIMITED)
        val cdSeconds = params.intParam("cdSeconds", RateLimitConfig.DEFAULT_CD_SECONDS)
        val recoveryPerCd = params.intParam("recoveryPerCd", upLimited)
        return RateLimitConfig(upLimited, cdSeconds, recoveryPerCd)
    }

    private fun resolveKey(def: WorkflowDefinition, params: Map<String, Any>?): String {
        val explicit = params.stringParam("rateLimitKey")
        if (!explicit.isNullOrBlank()) {
            return RedisKey.wrapIfNeeded(def.appGroup, def.id, explicit)
        }
        val prefix = params.stringParam("keyPrefix") ?: "fluxion:ratelimit:workflow:"
        return RedisKey.format(def.appGroup, def.id, "$prefix${def.id}")
    }
}
