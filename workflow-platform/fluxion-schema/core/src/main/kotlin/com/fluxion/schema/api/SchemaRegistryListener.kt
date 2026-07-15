package com.fluxion.schema.api

import com.fluxion.schema.model.Schema

interface SchemaRegistryListener {
    fun onSchemaRegistered(name: String, schema: Schema)
    fun onSchemaUpdated(name: String, schema: Schema)
    fun onSchemaDeleted(name: String)
}
