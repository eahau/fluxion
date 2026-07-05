package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfMarketplaceListing
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface WfMarketplaceListingRepository : JpaRepository<WfMarketplaceListing, Long> {

    fun findByListingId(listingId: String): Optional<WfMarketplaceListing>

    fun findBySourceTypeAndSourceId(sourceType: String, sourceId: String): List<WfMarketplaceListing>

    fun findByStatus(status: String): List<WfMarketplaceListing>

    fun findByAuthor(author: String): List<WfMarketplaceListing>

    /** 按标题或描述模糊搜索 */
    fun findByStatusAndTitleContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
        status1: String, keyword1: String,
        status2: String, keyword2: String
    ): List<WfMarketplaceListing>
}
