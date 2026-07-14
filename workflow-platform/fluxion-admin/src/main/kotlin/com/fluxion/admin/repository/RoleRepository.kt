package com.fluxion.admin.repository

import com.fluxion.admin.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for [Role] entities.
 *
 * Primary key type is `String` because the Role entity uses business PK `roleName`.
 * The owned `permissions` collection is loaded eagerly (FetchType.EAGER on the entity)
 * so callers never deal with lazy-init exceptions after session close.
 */
@Repository
interface RoleRepository : JpaRepository<Role, String> {

    /** Load a role by its unique role name. */
    fun findByRoleName(roleName: String): Role?
}
