package com.fluxion.schema.api

import com.fluxion.schema.model.Schema

/**
 * Schema 解析器，将原始 Schema 文本编译为统一的 [Schema] 对象。
 *
 * 格式路由由 [ParserBinding] 在 Spring 装配层声明，SPI 本身不感知格式。
 */
interface SchemaParser {

    /**
     * 从字符串解析 Schema。
     *
     * @param name 注册名，内联 schema 可为 null
     * @param raw 原始 Schema 文本
     * @return 编译后的 Schema 对象
     */
    fun parse(name: String?, raw: String): Schema

    /**
     * 从对象解析 Schema（兼容 JSON Map、POJO 等）。
     *
     * @param name 注册名，内联 schema 可为 null
     * @param raw 原始 Schema 对象
     * @return 编译后的 Schema 对象
     */
    fun parse(name: String?, raw: Any): Schema
}
