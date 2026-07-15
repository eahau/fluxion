package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat

interface ExternalSchemaRegistry : SchemaRegistry {

    fun register(name: String, format: SchemaFormat, raw: String): Schema

    fun register(name: String, format: SchemaFormat, raw: String, version: Long): Schema

    fun update(name: String, format: SchemaFormat, raw: String): Schema

    fun delete(name: String): Boolean

    fun getSubjectName(name: String, format: SchemaFormat): String
}
