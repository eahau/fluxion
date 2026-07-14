package com.fluxion.admin.util

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

/**
 * Slices a full in-memory `List<T>` into a Spring Data `Page<T>` matching the supplied
 * `Pageable` (offset + pageSize).
 *
 * Used whenever a Repository does not support native Spring-Data paging, e.g. when the
 * result set must first be fully loaded, then filtered or merged in application code.
 */
fun <T> List<T>.toPage(pageable: Pageable): Page<T> {
    val start = pageable.offset.toInt()
    val end = (start + pageable.pageSize).coerceAtMost(size)
    val content = if (start < size) subList(start, end) else emptyList()
    return PageImpl(content, pageable, size.toLong())
}
