package com.fluxion.core.mock

import com.fluxion.core.enums.NodeType
import com.fluxion.core.function.FunctionResolver
import com.fluxion.core.function.WorkflowFunction

/**
 * 鐏?Mock 閼宠棄濮忓▔銊ュ弳閵嗗苯鍤遍弫鎷屝掗弸鎰湴閵嗗秶娈?[FunctionResolver] 鐎圭偟骞囬妴?
 *
 * 鐠嬪啳鐦崗銉ュ經閿涘湑ontroller 閻?debug 閹恒儱褰涢敍澶婃躬闂団偓鐟?Mock 閺冭绱濋悽銊︽拱缁瀵樼憗鍦埂鐎?
 * [FunctionRegistry] 閸氬簼绱堕崗銉ョ穿閹垮函绱濇担?[com.fluxion.core.engine.WorkflowEngine]
 * 閻ㄥ嫭澧界悰宀冪箖缁嬪绻氶幐浣哄嚱閸戔偓閳ユ柡鈧柨绱╅幙搴″涧閻鍩岀悮顐㈠瘶鐟佸懎鎮楅惃?[MockWorkflowFunction]閿?
 * 閼奉亣闊╂稉宥呭晙閸栧懎鎯堟禒璁崇秿 mock 閻樿埖鈧礁鍨介弬顓炲瀻閺€顖樷偓?
 *
 * mockConfig 闁俺绻?provider 閸斻劍鈧浇骞忛崣鏍电礉娓氬じ绨幍褑顢戦張鐔荤殶閺佺鐨熺拠?Mock閵?
 */
class MockFunctionRegistry(
    private val inner: FunctionResolver,
    private val mockConfigProvider: () -> MockConfig
) : FunctionResolver {

    override fun resolve(functionRef: String, nodeType: NodeType?): WorkflowFunction<Any> {
        return MockWorkflowFunction(inner.resolve(functionRef, nodeType), mockConfigProvider, functionRef)
    }

    override fun resolve(functionRef: String): WorkflowFunction<Any> {
        return MockWorkflowFunction(inner.resolve(functionRef), mockConfigProvider, functionRef)
    }

    override fun resolve(functionRef: String, nodeType: NodeType?, version: Long): WorkflowFunction<Any> {
        return MockWorkflowFunction(inner.resolve(functionRef, nodeType, version), mockConfigProvider, functionRef)
    }
}
