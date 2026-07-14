package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * User-to-application-group membership entity used for multi-tenant data isolation.
 *
 * Each row grants a user visibility into a specific `appGroup`. The pair
 * `(username, app_group)` is enforced unique by the `uk_user_app_group` constraint.
 * Used by `SecurityContextHelper.accessibleAppGroups()` to scope PRIVATE workflows/schemas.
 *
 * Source table: `user_app_groups`.
 *
 * Collaborates with: UserAppGroupRepository, SecurityContextHelper, WfSchemaService (scope filtering).
 */
@Entity
@Table(
    name = "user_app_groups",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_app_group", columnNames = ["username", "app_group"])
    ]
)
class UserAppGroup : BaseEntity() {

    /** Owning user username, maps to column `username`. */
    @Column(name = "username", nullable = false, length = 64)
    var username: String = ""

    /** Application group identifier the user is a member of, maps to column `app_group`. */
    @Column(name = "app_group", nullable = false, length = 128)
    var appGroup: String = ""
}
