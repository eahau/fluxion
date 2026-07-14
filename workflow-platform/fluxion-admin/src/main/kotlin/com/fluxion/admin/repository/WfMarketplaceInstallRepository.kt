package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfMarketplaceInstall
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [WfMarketplaceInstall] installation records.
 *
 * Uniqueness constraint (listingId, appGroup) on the entity guarantees SQL-side idempotency
 * for double-click installs; the `existsByListingIdAndAppGroup` check provides an explicit
 * precondition guard so the service can return a friendlier error before hitting the DB.
 */
interface WfMarketplaceInstallRepository : JpaRepository<WfMarketplaceInstall, Long> {

    /** List of every tenant that has installed a specific listing (used for popularity metrics). */
    fun findByListingId(listingId: String): List<WfMarketplaceInstall>

    /** List of every listing installed inside one tenant app group. */
    fun findByAppGroup(appGroup: String): List<WfMarketplaceInstall>

    /** Load one installation row for uninstall flow. */
    fun findByListingIdAndAppGroup(listingId: String, appGroup: String): WfMarketplaceInstall?

    /** Idempotency pre-check before creating a new install row. */
    fun existsByListingIdAndAppGroup(listingId: String, appGroup: String): Boolean

    /** Remove one install row (uninstall flow). */
    fun deleteByListingIdAndAppGroup(listingId: String, appGroup: String)
}
