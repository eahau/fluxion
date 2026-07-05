package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfFunction
import com.fluxion.admin.generated.model.FunctionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface WfFunctionRepository : JpaRepository<WfFunction, Long> {
    fun findByFunctionName(functionName: String): Optional<WfFunction>
    fun findByFunctionType(functionType: String): List<WfFunction>
    fun findByStatus(status: FunctionStatus): List<WfFunction>

    /** 按 scope 查询（PLATFORM 全局函数） */
    fun findByScope(scope: String): List<WfFunction>

    /** 按 scope + app_group 查询（PRIVATE 函数） */
    fun findByScopeAndAppGroup(scope: String, appGroup: String): List<WfFunction>

    /** 按 scope 检查函数名是否已存在（PLATFORM/MARKETPLACE 场景） */
    fun findByScopeAndFunctionName(scope: String, functionName: String): Optional<WfFunction>

    /** 按 scope + app_group 检查函数名是否已存在（PRIVATE 场景） */
    fun findByScopeAndAppGroupAndFunctionName(scope: String, appGroup: String, functionName: String): Optional<WfFunction>
}
