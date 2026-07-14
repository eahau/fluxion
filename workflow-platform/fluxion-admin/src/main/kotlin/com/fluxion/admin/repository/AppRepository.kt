package com.fluxion.admin.repository

import com.fluxion.admin.entity.App
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/**
 * Spring Data JPA repository for [App] application entities.
 */
interface AppRepository : JpaRepository<App, Long> {

    /** Lookup by stable immutable business key. */
    fun findByAppKey(appKey: String): Optional<App>

    /** Case-insensitive fuzzy search (admin app-list filter box). */
    fun findByAppNameContainingIgnoreCase(appNameFragment: String): List<App>

    /** Filter by lifecycle status. */
    fun findByStatus(status: String): List<App>

    /** Ownership list — shown on the "My Apps" landing page. */
    fun findByOwner(owner: String): List<App>
}
