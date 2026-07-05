package com.fluxion.admin.service

import com.fluxion.admin.entity.UserAppGroup
import com.fluxion.admin.repository.UserAppGroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 租户（app_group）管理服务
 *
 * 负责用户与应用分组的关联管理，以及租户权限校验。
 *
 * 权限规则：
 *   - ADMIN 角色用户隐式拥有所有 app_group 权限（不需要在此表中添加记录）
 *   - 非 ADMIN 用户只能访问自己在 user_app_groups 中关联的 app_group
 */
@Service
class TenantService(
    private val userAppGroupRepository: UserAppGroupRepository,
) {

    /**
     * 校验用户是否有权访问指定 app_group
     *
     * @param username  用户名
     * @param appGroup  应用分组
     * @param isAdmin   是否为 ADMIN 角色
     * @return true 表示有权访问
     */
    fun hasAccess(username: String, appGroup: String, isAdmin: Boolean = false): Boolean {
        if (isAdmin) return true
        return userAppGroupRepository.existsByUsernameAndAppGroup(username, appGroup)
    }

    /**
     * 获取用户可访问的所有 app_group 列表
     */
    fun getUserAppGroups(username: String): List<String> {
        return userAppGroupRepository.findByUsername(username).map { it.appGroup }
    }

    /**
     * 为用户分配 app_group
     */
    @Transactional
    fun assignAppGroup(username: String, appGroup: String) {
        if (userAppGroupRepository.existsByUsernameAndAppGroup(username, appGroup)) return
        val entity = UserAppGroup().apply {
            this.username = username
            this.appGroup = appGroup
        }
        userAppGroupRepository.save(entity)
    }

    /**
     * 批量为用户分配 app_group
     */
    @Transactional
    fun assignAppGroups(username: String, appGroups: List<String>) {
        appGroups.forEach { assignAppGroup(username, it) }
    }

    /**
     * 移除用户的 app_group 关联
     */
    @Transactional
    fun removeAppGroup(username: String, appGroup: String) {
        userAppGroupRepository.deleteByUsernameAndAppGroup(username, appGroup)
    }

    /**
     * 查询某 app_group 下的所有用户
     */
    fun getMembers(appGroup: String): List<String> {
        return userAppGroupRepository.findByAppGroup(appGroup).map { it.username }
    }
}
