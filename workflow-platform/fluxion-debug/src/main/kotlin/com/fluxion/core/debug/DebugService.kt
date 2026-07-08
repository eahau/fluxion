package com.fluxion.core.debug

import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.function.FunctionResolver
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.core.value.RerunResult
import org.slf4j.LoggerFactory
import java.util.stream.Collectors

/**
 * 鐠嬪啳鐦張宥呭 閳?閹绘劒绶甸柌宥嗘杹閵嗕焦鏌囬悙骞库偓浣稿礋濮濄儲澧界悰宀€鐡戠拫鍐槸閼宠棄濮? *
 * 閺嶇绺炬导妯哄◢閿涙艾鍩勯悽?ImmutableExecutionState 閻ㄥ嫪绗夐崣顖氬綁閻楄鈧嶇礉
 *          閺冪娀娓堕柌宥嗘煀閹笛嗩攽閸撳秶鐤嗛懞鍌滃仯閸楀啿褰茬划鍓р€橀幁銏狀槻閸掗鎹㈤幇蹇氬Ν閻愬湱濮搁幀浣碘偓? */
class DebugService(
    private val engine: WorkflowEngine,
    private val executionLogRepository: ExecutionLogRepository,
    private val definitionLoader: DefinitionLoader
) {
    private val log = LoggerFactory.getLogger(DebugService::class.java)

    // 閳光偓閳光偓閳光偓 Rerun 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    /**
     * 娴犲孩瀵氱€规俺濡悙鐟扮磻婵鍣搁弨鐐⒔鐞涘被鈧?     *
     * 閸掆晝鏁ら崢鐔奉潗閹笛嗩攽閻樿埖鈧胶娈戞稉宥呭讲閸欐ê鎻╅悡褝绱濋幁銏狀槻閻╊喗鐖ｉ懞鍌滃仯娑斿澧犻惃鍕閺堝濡悙纭呯翻閸戠尨绱?     * 閻掕泛鎮楁禒搴ｆ窗閺嶅洩濡悙鐟扮磻婵鍣搁弬鐗堝⒔鐞涘苯鎮楃紒顓″Ν閻愮櫢绱欓弨顖涘瘮鐟曞棛娲婃潏鎾冲弳閿涘鈧?     *
     * @param executionId    閸樼喎顫愰幍褑顢戦惃?ID
     * @param targetNodeId   娴犲骸鎽㈡稉顏囧Ν閻愮懓绱戞慨瀣櫢閺€?     * @param overrideInput  鐟曞棛娲婃潏鎾冲弳閿涘潱ull 閸掓瑤濞囬悽銊ュ妞硅精濡悙纭呯翻閸戠尨绱?     * @return 闁插秵鏂佺紒鎾寸亯閿涘牆鎯堥弬鎵畱閺堚偓缂佸牏濮搁幀浣告嫲閹笛嗩攽鏉炪劏鎶楅敍?     */
    suspend fun rerunFromNode(
        executionId: String,
        targetNodeId: String,
        overrideInput: Any?,
        functionRegistry: FunctionResolver = engine.functionRegistry
    ): RerunResult {
        val originalResult = executionLogRepository.findByExecutionId(executionId)
            ?: throw IllegalArgumentException("Execution not found: `$executionId")

        val originalState = originalResult.finalState!!
        val def = loadDefinition(executionId)

        val targetIndex = findNodeIndex(def, targetNodeId)
        require(targetIndex >= 0) { "Node not found in workflow: $targetNodeId" }

        // Preserve all node outputs before the target node
        val priorOutputs = originalState.nodeOutputs.entries
            .filter { (key, _) -> isBeforeNode(def, key, targetNodeId) }
            .associate { it.key to it.value }

        val replayState = ImmutableExecutionState
            .start(def, originalState.inputs)
            .mergeNodeOutputs(priorOutputs)

        val directInput = overrideInput ?: getPrevNodeOutput(originalState, def, targetNodeId)

        val nodesToRerun = def.nodes.subList(targetIndex, def.nodes.size)

        log.info("Rerunning from node [{}] in execution [{}], {} nodes to execute",
            targetNodeId, executionId, nodesToRerun.size)

        return engine.executePartial(nodesToRerun, directInput, replayState, functionRegistry)
    }

    // 閳光偓閳光偓閳光偓 Step / Continue 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    /**
     * 閸楁洘顒為幍褑顢戦敍姘⒔鐞涘奔绗呮稉鈧稉顏囧Ν閻愮懓鎮楅弳鍌氫粻閵?     *
     * @param snap  瑜版挸澧犵拫鍐槸韫囶偆鍙?     * @return 閺傛壆娈戠拫鍐槸韫囶偆鍙庨敍鍧xtNodeIndex + 1閿?     */
    suspend fun step(snap: DebugSnapshot, functionRegistry: FunctionResolver = engine.functionRegistry): DebugSnapshot {
        val def = definitionLoader.load(snap.workflowId)
        return doStep(snap, def, functionRegistry)
    }

    /**
     * 缂佈呯敾閹笛嗩攽閻╂潙鍩岄柆鍥у煂閺傤厾鍋ｉ幋鏍т紣娴ｆ粍绁︾紒鎾存将閵?     *
     * @param snap  瑜版挸澧犵拫鍐槸韫囶偆鍙?     * @return 閺傛壆娈戠拫鍐槸韫囶偆鍙庨敍鍧usedAtNodeId 娑撳秳璐?null 鐞涖劎銇氶崑婊冩躬閺傤厾鍋ｉ敍?     */
    suspend fun continueToBreakpoint(snap: DebugSnapshot, functionRegistry: FunctionResolver = engine.functionRegistry): DebugSnapshot {
        val def = definitionLoader.load(snap.workflowId)
        var current = snap
        val nodes = def.nodes

        while (current.nextNodeIndex < nodes.size) {
            current = doStep(current, def, functionRegistry)
            if (current.pausedAtNodeId != null) break
        }
        return current
    }

    // 閳光偓閳光偓閳光偓 Snapshot 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    /**
     * 娴犲孩澧界悰宀€绮ㄩ弸婊冨灡瀵ゅ搫鍨垫慨瀣殶鐠囨洖鎻╅悡褋鈧?     *
     * 鐏?EngineResult.finalState 鐏忎浇顥婃稉?DebugSnapshot閿?     * 閺€顖涘瘮鐠佸墽鐤嗛弬顓犲仯闂嗗棗鎮庨崪?Mock 闁板秶鐤嗛妴?     */
    fun createSnapshot(
        result: EngineResult,
        workflowId: String,
        workflowVersion: Int,
        breakpoints: Set<String>?,
        mockConfig: MockConfig = MockConfig.EMPTY
    ): DebugSnapshot = DebugSnapshot(
        workflowId = workflowId,
        workflowVersion = workflowVersion,
        executionState = result.finalState!!,
        traces = result.trace,
        breakpoints = breakpoints ?: emptySet(),
        mockConfig = mockConfig,
        pausedAtNodeId = null,
        nextNodeIndex = result.trace.size
    )

    // 閳光偓閳光偓閳光偓 Private Helpers 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    /** 閹笛嗩攽閸楁洘顒為敍鍫濆敶闁劍鏌熷▔鏇礆閿涙碍浠径宥勭瑐娑撳鏋冮妴浣瑰⒔鐞涘矁濡悙骞库偓浣规纯閺傛壆濮搁幀?*/
    private suspend fun doStep(snap: DebugSnapshot, def: WorkflowDefinition, functionRegistry: FunctionResolver): DebugSnapshot {
        val ctx = DebugSnapshot.restore(snap, def)
        val idx = ctx.nextNodeIndex
        val nodes = def.nodes

        if (idx >= nodes.size) return snap

        val node = nodes[idx]
        val state = ctx.executionState

        val directInput: Any? = if (idx == 0) state.inputs else state.getNodeOutput(nodes[idx - 1].id)

        val trace = ctx.traces.toMutableList()
        val record = engine.executeNode(node, directInput, state, functionRegistry)
        trace.add(record)

        val newState = state.withNodeOutput(node.id, record.output)
        val pausedAt = if (ctx.breakpoints.contains(node.id)) node.id else null

        return DebugContext(newState, ctx.mockConfig, ctx.breakpoints, trace, idx + 1)
            .toSnapshot(snap.workflowId, snap.workflowVersion, pausedAt)
    }

    private fun loadDefinition(executionId: String): WorkflowDefinition {
        val workflowId = executionLogRepository.findWorkflowIdByExecutionId(executionId)
            ?: throw IllegalArgumentException("No workflow bound to execution: `$executionId")
        return definitionLoader.load(workflowId)
    }

    private fun findNodeIndex(def: WorkflowDefinition, nodeId: String): Int =
        def.nodes.indexOfFirst { it.id == nodeId }

    private fun isBeforeNode(def: WorkflowDefinition, nodeAId: String, nodeBId: String): Boolean {
        val a = findNodeIndex(def, nodeAId)
        val b = findNodeIndex(def, nodeBId)
        return a >= 0 && b >= 0 && a < b
    }

    private fun getPrevNodeOutput(state: ImmutableExecutionState, def: WorkflowDefinition, targetNodeId: String): Any? {
        val idx = findNodeIndex(def, targetNodeId)
        if (idx <= 0) return state.inputs
        return state.getNodeOutput(def.nodes[idx - 1].id)
    }

    // 閳光偓閳光偓閳光偓 SPI Interfaces 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    /** 閹笛嗩攽閺冦儱绻旀禒鎾冲亶 SPI閿涘牏鏁?workflow-admin 鐎圭偟骞囬敍?*/
    interface ExecutionLogRepository {
        fun findByExecutionId(executionId: String): EngineResult?
        fun findWorkflowIdByExecutionId(executionId: String): String?
    }

    /** 瀹搞儰缍斿ù浣哥暰娑斿濮炴潪钘夋珤 SPI閿涘牏鏁?workflow-admin 鐎圭偟骞囬敍?*/
    interface DefinitionLoader {
        fun load(workflowId: String): WorkflowDefinition
    }
}
