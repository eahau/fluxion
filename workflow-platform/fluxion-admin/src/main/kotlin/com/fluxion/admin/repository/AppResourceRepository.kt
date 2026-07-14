package com.fluxion.admin.repository

import com.fluxion.admin.entity.AppResource
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/**
 * Spring Data JPA repository for [AppResource] infrastructure-resource configs.
 */
interface AppResourceRepository : JpaRepository<AppResource, Long> {

    fun findByResourceName(resourceName: String): Optional<AppResource>

    fun findByResourceType(resourceType: String): List<AppResource>

    fun findByResourceTypeIn(types: Collection<String>): List<AppResource>
}
