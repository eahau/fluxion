package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfMarketplaceInstall
import org.springframework.data.jpa.repository.JpaRepository

interface WfMarketplaceInstallRepository : JpaRepository<WfMarketplaceInstall, Long> {

    fun findByListingId(listingId: String): List<WfMarketplaceInstall>

    fun findByAppGroup(appGroup: String): List<WfMarketplaceInstall>

    fun findByListingIdAndAppGroup(listingId: String, appGroup: String): WfMarketplaceInstall?

    fun existsByListingIdAndAppGroup(listingId: String, appGroup: String): Boolean

    fun deleteByListingIdAndAppGroup(listingId: String, appGroup: String)
}
