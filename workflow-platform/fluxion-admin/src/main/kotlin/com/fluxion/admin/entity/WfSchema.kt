package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 工作流 Schema 实体。
 *
 * 源表：`wf_schema`
 */
@Entity
@Table(name = "wf_schema")
class WfSchema : BaseEntity() {

    /** Schema 唯一名称（列：`schema_name`） */
    @Column(name = "schema_name", nullable = false, unique = true, length = 128)
    var schemaName: String = ""

    /** Schema 类型标签（逗号分隔，如 INPUT,OUTPUT），支持多类型复用（列：`schema_type`） */
    @Column(name = "schema_type", nullable = false, length = 64)
    var schemaType: String = "INPUT"

    /** Schema 格式标识：json-schema / protobuf / avro（列：`schema_format`） */
    @Column(name = "schema_format", nullable = false, length = 32)
    var schemaFormat: String = "json-schema"

    /** Schema 内容（列：`schema_json`） */
    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    var schemaJson: String = ""

    /** 描述（列：`description`） */
    @Column(name = "description", length = 512)
    var description: String? = null

    /** 是否已冻结（列：`frozen`） */
    @Column(name = "frozen", nullable = false)
    var frozen: Boolean = false

    /** 作用域：PLATFORM/PRIVATE（列：`scope`） */
    @Column(name = "scope", nullable = false, length = 16)
    var scope: String = "PLATFORM"

    /** 所属应用分组（scope=PRIVATE 时必填）（列：`app_group`） */
    @Column(name = "app_group", length = 128)
    var appGroup: String? = null
}
