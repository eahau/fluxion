package com.fluxion.admin.repository

import com.fluxion.admin.entity.AppResourceBinding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * Spring Data JPA repository for [AppResourceBinding] app↔resource N:M junctions.
 */
interface AppResourceBindingRepository : JpaRepository<AppResourceBinding, Long> {

    /** All bindings for a given app — joins are fetched eagerly so the controller can render alias/scope. */
    @Query("SELECT b FROM AppResourceBinding b JOIN FETCH b.app JOIN FETCH b.resource WHERE b.app.id = :appId ORDER BY b.resource.resourceType, b.id")
    fun findAllByAppIdWithResource(appId: Long): List<AppResourceBinding>

    /** Type-filtered subset (used by the node-param dropdown for dbExecute / redisCommand). */
    @Query("SELECT b FROM AppResourceBinding b JOIN FETCH b.app JOIN FETCH b.resource r WHERE b.app.id = :appId AND r.resourceType = :resourceType ORDER BY b.resourceScope DESC, b.id")
    fun findByAppIdAndResourceTypeWithResource(appId: Long, resourceType: String): List<AppResourceBinding>

    /** Unique-lookup helper with eager joins to survive serialization outside the persistence context. */
    @Query("SELECT b FROM AppResourceBinding b JOIN FETCH b.app JOIN FETCH b.resource WHERE b.id = :id")
    override fun findById(id: Long): java.util.Optional<AppResourceBinding>

    /** Unique-lookup helper for binding-upsert endpoints. */
    @Query("SELECT b FROM AppResourceBinding b JOIN FETCH b.app JOIN FETCH b.resource WHERE b.app.id = :appId AND b.resource.id = :resourceId")
    fun findByAppIdAndResourceId(appId: Long, resourceId: Long): AppResourceBinding?
}
