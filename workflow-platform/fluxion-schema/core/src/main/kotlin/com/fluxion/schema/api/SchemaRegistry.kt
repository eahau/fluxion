package com.fluxion.schema.api

import com.fluxion.schema.model.Schema

/**
 * Schema 注册表，用于按名称查找已注册的 Schema。
 *
 * 支持多版本并存：同一名称的 Schema 可以有多个版本，
 * 通过 [get] 方法的 `version` 参数指定具体版本，
 * 不传则返回最新版本。
 */
interface SchemaRegistry {

    /**
     * 根据名称获取 Schema。
     *
     * @param name    Schema 名称
     * @param version 版本号，null 表示最新版本
     * @return Schema 对象，未找到时返回 null
     */
    fun get(name: String, version: Long? = null): Schema?

    /**
     * 列出所有已注册 Schema（每个名称只返回最新版本）。
     */
    fun list(): List<Schema>

    /**
     * 列出指定 Schema 的所有版本号（从旧到新排序）。
     *
     * @param name Schema 名称
     * @return 版本号列表，不存在时返回空列表
     */
    fun listVersions(name: String): List<Long> = emptyList()
}
