package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Marketplace publish listing entity — a shareable workflow/function/template.
 *
 * A listing is created when a user *publishes* a private workflow/function to the
 * shared Marketplace. Installations create WfMarketplaceInstall rows. The source
 * object at publish time is snapshotted via `sourceVersion` so that subsequent
 * private mutations do not affect marketplace consumers.
 *
 * Source table: `wf_marketplace_listing`.
 *
 * Collaborates with: WfMarketplaceListingRepository, MarketplaceService,
 * WfMarketplaceInstall (installation trace).
 */
@Entity
@Table(name = "wf_marketplace_listing")
class WfMarketplaceListing : BaseEntity() {

    /** Stable marketplace listing identifier, maps to column `listing_id`. */
    @Column(name = "listing_id", nullable = false, unique = true, length = 128)
    var listingId: String = ""

    /** Kind of resource: WORKFLOW / FUNCTION / WORKFLOW_TEMPLATE, maps to column `source_type`. */
    @Column(name = "source_type", nullable = false, length = 16)
    var sourceType: String = "WORKFLOW"

    /** Business ID of the original private workflow/function, maps to column `source_id`. */
    @Column(name = "source_id", nullable = false, length = 128)
    var sourceId: String = ""

    /** Version snapshot of the source at publish time, maps to column `source_version`. */
    @Column(name = "source_version", nullable = false)
    var sourceVersion: Int = 1

    /** Marketplace display title, maps to column `title`. */
    @Column(name = "title", nullable = false, length = 256)
    var title: String = ""

    /** Long-form description / documentation, maps to column `description`. */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    /** JSON string array of tag strings for categorisation/search, maps to column `tags`. */
    @Column(name = "tags", columnDefinition = "JSON")
    var tags: String? = null

    /** Publishing username, maps to column `author`. */
    @Column(name = "author", nullable = false, length = 64)
    var author: String = ""

    /** Listing lifecycle: ACTIVE / DEPRECATED, maps to column `status`. */
    @Column(name = "status", nullable = false, length = 16)
    var status: String = "ACTIVE"

    /** Denormalised counter of installations, maps to column `install_count`. */
    @Column(name = "install_count", nullable = false)
    var installCount: Int = 0

    /** Template-specific configuration JSON (WORKFLOW_TEMPLATE only), maps to column `template_config`. */
    @Column(name = "template_config", columnDefinition = "TEXT")
    var templateConfig: String? = null
}
