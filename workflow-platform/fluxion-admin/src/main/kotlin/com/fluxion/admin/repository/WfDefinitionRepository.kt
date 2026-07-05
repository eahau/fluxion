package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfDefinition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface WfDefinitionRepository : JpaRepository<WfDefinition, Long> {

    fun findByWorkflowId(workflowId: String): Optional<WfDefinition>

    fun findByStatus(status: String): List<WfDefinition>

    /** 查询协议绑定 Key（路由时使用） */
    fun findByProtocolAndBindKey(protocol: String, bindKey: String): Optional<WfDefinition>

    /** 查询指定协议 + 方法 + 绑定路径的工作流（用于重复检查） */
    fun findByProtocolAndMethodAndBindKey(protocol: String, method: String?, bindKey: String?): Optional<WfDefinition>

    /** 按 scope 查询路由冲突（PLATFORM/MARKETPLACE 场景，app_group 为 null） */
    fun findByScopeAndProtocolAndMethodAndBindKey(
        scope: String, protocol: String, method: String?, bindKey: String?
    ): Optional<WfDefinition>

    /** 按 scope + app_group 查询路由冲突（PRIVATE 场景） */
    fun findByScopeAndAppGroupAndProtocolAndMethodAndBindKey(
        scope: String, appGroup: String?, protocol: String, method: String?, bindKey: String?
    ): Optional<WfDefinition>

    /** 按 scope 过滤查询所有 ACTIVE 工作流 */
    @Query("SELECT d FROM WfDefinition d WHERE d.status = 'ACTIVE' AND d.scope = :scope")
    fun findAllActiveByScope(scope: String): List<WfDefinition>

    /** 按 scope + app_group 过滤查询 ACTIVE 工作流 */
    @Query("SELECT d FROM WfDefinition d WHERE d.status = 'ACTIVE' AND d.scope = :scope AND d.appGroup = :appGroup")
    fun findAllActiveByScopeAndAppGroup(scope: String, appGroup: String): List<WfDefinition>

    /** 查询所有 ACTIVE 状态的工作流（引擎启动时加载） */
    @Query("SELECT d FROM WfDefinition d WHERE d.status = 'ACTIVE'")
    fun findAllActive(): List<WfDefinition>
}
