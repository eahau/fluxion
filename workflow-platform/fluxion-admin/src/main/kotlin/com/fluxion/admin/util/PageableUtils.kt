package com.fluxion.admin.util

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

/**
 * 对内存中的完整列表执行手动分页，返回 [Page] 包装。
 *
 * 适用于 Repository 不支持 Spring Data 分页（如先全量加载再过滤/合并）的场景。
 */
fun <T> List<T>.toPage(pageable: Pageable): Page<T> {
    val start = pageable.offset.toInt()
    val end = (start + pageable.pageSize).coerceAtMost(size)
    val content = if (start < size) subList(start, end) else emptyList()
    return PageImpl(content, pageable, size.toLong())
}
