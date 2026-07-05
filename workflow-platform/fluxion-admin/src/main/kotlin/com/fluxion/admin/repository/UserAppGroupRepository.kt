package com.fluxion.admin.repository

import com.fluxion.admin.entity.UserAppGroup
import org.springframework.data.jpa.repository.JpaRepository

interface UserAppGroupRepository : JpaRepository<UserAppGroup, Long> {

    /** 查询用户所属的所有 app_group */
    fun findByUsername(username: String): List<UserAppGroup>

    /** 查询某 app_group 下的所有用户 */
    fun findByAppGroup(appGroup: String): List<UserAppGroup>

    /** 检查用户是否属于指定 app_group */
    fun existsByUsernameAndAppGroup(username: String, appGroup: String): Boolean

    /** 删除用户与指定 app_group 的关联 */
    fun deleteByUsernameAndAppGroup(username: String, appGroup: String)

    /** 删除用户的所有 app_group 关联 */
    fun deleteByUsername(username: String)
}
