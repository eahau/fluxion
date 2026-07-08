package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfMarketplaceListing
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/**
 * Spring Data JPA repository for [WfMarketplaceListing] marketplace entries.
 *
 * Keyword search is implemented via a derived-query OR-expression over title and description
 * with explicit `status1 == status2` duplication required by Spring Data JPA's derived
 * query grammar for OR-combined filters.
 */
interface WfMarketplaceListingRepository : JpaRepository<WfMarketplaceListing, Long> {

    /** Look up a listing by its stable listingId. */
    fun findByListingId(listingId: String): Optional<WfMarketplaceListing>

    /** Every listing published from a specific private source (workflow/function). */
    fun findBySourceTypeAndSourceId(sourceType: String, sourceId: String): List<WfMarketplaceListing>

    /** Public listing page: only ACTIVE results. */
    fun findByStatus(status: String): List<WfMarketplaceListing>

    /** Author dashboard: listings created by a specific user. */
    fun findByAuthor(author: String): List<WfMarketplaceListing>

    /**
     * Case-insensitive keyword search across title and description for a given status.
     *
     * The duplicated status parameter is an artifact of Spring Data derived-query syntax:
     * each LIKE branch must anchor the predicate with its own status equality check.
     * Callers always pass the same value for `status1` and `status2`.
     */
    fun findByStatusAndTitleContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
        status1: String, keyword1: String,
        status2: String, keyword2: String
    ): List<WfMarketplaceListing>
}
