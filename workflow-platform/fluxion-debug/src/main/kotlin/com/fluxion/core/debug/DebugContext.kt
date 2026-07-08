package com.fluxion.core.debug

import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.mock.MockRule
import com.fluxion.core.value.NodeExecutionRecord

/**
 * 鐠嬪啳鐦稉濠佺瑓閺?閳?閹镐焦婀佺拫鍐槸娴兼俺鐦介惃鍕箥鐞涘本妞傞悩鑸碘偓浣碘偓? *
 * 閸栧懎鎯堣ぐ鎾冲閹笛嗩攽閻樿埖鈧降鈧府ock 闁板秶鐤嗛妴浣规焽閻愬綊娉﹂崥鍫涒偓浣瑰⒔鐞涘矁寤烘潻鐟版嫲娑撳绔撮懞鍌滃仯缁便垹绱╅妴? * 閸欘垶鈧俺绻?[toSnapshot] 鏉烆剚宕叉稉鍝勫讲鎼村繐鍨崠鏍畱 [DebugSnapshot]閿涘苯鐤勯悳鐗堟￥閻樿埖鈧礁澧犻崥搴ｎ伂娴溿倓绨伴妴? */
class DebugContext(
    /** 瑜版挸澧犳稉宥呭讲閸欐ɑ澧界悰宀€濮搁幀?*/
    val executionState: ImmutableExecutionState,
    /** Mock 闁板秶鐤嗛敍鍫濆讲閸斻劍鈧椒鎱ㄩ弨鐧哥礆 */
    var mockConfig: MockConfig,
    /** 閺傤厾鍋ｉ懞鍌滃仯 ID 闂嗗棗鎮?*/
    val breakpoints: Set<String>,
    /** 瀹稿弶澧界悰宀冨Ν閻愬湱娈戞潪銊ㄦ姉鐠佹澘缍?*/
    val traces: List<NodeExecutionRecord>,
    /** 娑撳绔存稉顏勭窡閹笛嗩攽閼哄倻鍋ｉ惃鍕偍瀵?*/
    var nextNodeIndex: Int
) {
    /** 閹恒劏绻橀崚棰佺瑓娑撯偓娑擃亣濡悙?*/
    fun advanceNextNode() { nextNodeIndex++ }

    /** 濞ｈ濮?閺囧瓨鏌?Mock 鐟欏嫬鍨?*/
    fun addMockRule(rule: MockRule) {
        val rules = mockConfig.rules.toMutableList()
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) {
            rules[idx] = rule
        } else {
            rules.add(rule)
        }
        this.mockConfig = mockConfig.copy(rules = rules)
    }

    /** 缁夊娅?Mock 鐟欏嫬鍨?*/
    fun removeMockRule(ruleId: String) {
        val rules = mockConfig.rules.filterNot { it.id == ruleId }
        this.mockConfig = mockConfig.copy(rules = rules)
    }

    /** 閺勵垰鎯侀崥顖滄暏 Mock */
    fun setMockEnabled(enabled: Boolean) {
        this.mockConfig = mockConfig.copy(enabled = enabled)
    }

    /** 鏉烆剚宕叉稉鍝勫讲鎼村繐鍨崠鏍畱韫囶偆鍙庨敍鍫㈡暏娴滃骸澧犻崥搴ｎ伂娴肩姾绶敍?*/
    fun toSnapshot(workflowId: String, workflowVersion: Int, pausedAtNodeId: String?): DebugSnapshot =
        DebugSnapshot(
            workflowId = workflowId,
            workflowVersion = workflowVersion,
            executionState = executionState,
            traces = traces,
            breakpoints = breakpoints,
            mockConfig = mockConfig,
            pausedAtNodeId = pausedAtNodeId,
            nextNodeIndex = nextNodeIndex
        )
}

/**
 * 鐠嬪啳鐦悩鑸碘偓浣告彥閻?v3閵? *
 * 娑撳秴褰查崣妯糕偓浣稿讲鎼村繐鍨崠鏍畱鐠嬪啳鐦导姘崇樈閻樿埖鈧緤绱濋悽銊ょ艾閸撳秴鎮楃粩顖欑炊鏉堟挶鈧? * 闁俺绻?[DebugSnapshot.restore] 閸欘垱妫ら悩鑸碘偓浣逛划婢跺秳璐?[DebugContext]閵? */
data class DebugSnapshot(
    /** 瀹搞儰缍斿ù浣哥暰娑?ID */
    val workflowId: String,
    /** 瀹搞儰缍斿ù浣哄閺堫剙褰块敍鍫熶划婢跺秵妞傞弽锟犵崣娑撯偓閼峰瓨鈧嶇礆 */
    val workflowVersion: Int,
    /** 娑撳秴褰查崣妯诲⒔鐞涘瞼濮搁幀渚婄礄閼哄倻鍋ｆ潏鎾冲毉閵嗕浇绶崗銉ｂ偓浣稿帗娣団剝浼呴敍?*/
    val executionState: ImmutableExecutionState,
    /** 瀹稿弶澧界悰宀冨Ν閻愬湱娈戞潪銊ㄦ姉鐠佹澘缍?*/
    val traces: List<NodeExecutionRecord>,
    /** 閺傤厾鍋ｉ懞鍌滃仯 ID 闂嗗棗鎮?*/
    val breakpoints: Set<String>,
    /** Mock 闁板秶鐤?*/
    val mockConfig: MockConfig,
    /** 瑜版挸澧犻弳鍌氫粻閻ㄥ嫯濡悙?ID閿涘潱ull 鐞涖劎銇氶張顏呮畯閸嬫粣绱?*/
    val pausedAtNodeId: String?,
    /** 娑撳绔存稉顏勭窡閹笛嗩攽閼哄倻鍋ｉ惃鍕偍瀵?*/
    val nextNodeIndex: Int
) {
    companion object {
        /** 娴?DebugSnapshot 閹垹顦茬拫鍐槸娑撳﹣绗呴弬鍥风礄閺冪姵婀囬崝锛勵伂 Session閿?*/
        @JvmStatic
        fun restore(snap: DebugSnapshot, def: WorkflowDefinition): DebugContext {
            check(snap.workflowVersion == def.version) {
                "Workflow version mismatch: snapshot=${snap.workflowVersion}, definition=${def.version}"
            }
            return DebugContext(
                snap.executionState, snap.mockConfig,
                snap.breakpoints, snap.traces, snap.nextNodeIndex
            )
        }
    }
}
