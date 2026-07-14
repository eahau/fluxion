package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Marketplace installation record — a per-app-group adoption of a listing.
 *
 * One row is created each time a user *installs* a WfMarketplaceListing into their
 * app group. The pair `(listingId, appGroup)` is unique to avoid duplicate installs.
 * Installing a listing materialises a copy of the original workflow/function into the
 * tenant's PRIVATE namespace (with `sourceRef` back to the listing).
 *
 * Source table: `wf_marketplace_install`.
 *
 * Collaborates with: WfMarketplaceInstallRepository, MarketplaceService,
 * WfMarketplaceListing (install_count increment source).
 */
@Entity
@Table(
    name = "wf_marketplace_install",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_listing_app", columnNames = ["listing_id", "app_group"])
    ]
)
class WfMarketplaceInstall : BaseEntity() {

    /** Foreign key to WfMarketplaceListing.listingId, maps to column `listing_id`. */
    @Column(name = "listing_id", nullable = false, length = 128)
    var listingId: String = ""

    /** App group that performed the install, maps to column `app_group`. */
    @Column(name = "app_group", nullable = false, length = 128)
    var appGroup: String = ""

    /** Username that clicked install (for audit), maps to column `installed_by`. */
    @Column(name = "installed_by", length = 64)
    var installedBy: String? = null
}
