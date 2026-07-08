package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfFunction
import com.fluxion.admin.generated.model.FunctionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/**
 * Spring Data JPA repository for [WfFunction] function-registry entities.
 *
 * Uniqueness is checked per (scope, [appGroup], functionName) to let PLATFORM and PRIVATE
 * tenants host functions with identical short names without collision.
 */
interface WfFunctionRepository : JpaRepository<WfFunction, Long> {

    /** Global lookup used by the runtime function registry to validate references. */
    fun findByFunctionName(functionName: String): Optional<WfFunction>

    /** Filter by dispatch type (BUILTIN / SCRIPT_GROOVY / EXTERNAL / ...). */
    fun findByFunctionType(functionType: String): List<WfFunction>

    /** Filter by runtime lifecycle status (ACTIVE = invocable). */
    fun findByStatus(status: FunctionStatus): List<WfFunction>

    /** Load PLATFORM-scoped functions only. */
    fun findByScope(scope: String): List<WfFunction>

    /** Load PRIVATE-scoped functions for one tenant. */
    fun findByScopeAndAppGroup(scope: String, appGroup: String): List<WfFunction>

    /** Uniqueness pre-check for PLATFORM / MARKETPLACE scope (appGroup disregarded). */
    fun findByScopeAndFunctionName(scope: String, functionName: String): Optional<WfFunction>

    /** Uniqueness pre-check for PRIVATE scope (includes appGroup discriminator). */
    fun findByScopeAndAppGroupAndFunctionName(scope: String, appGroup: String, functionName: String): Optional<WfFunction>
}
