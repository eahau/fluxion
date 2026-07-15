package com.fluxion.schema.registry

import com.fluxion.schema.api.AzureApiStrategy
import com.fluxion.schema.api.RestSchemaRegistry

class AzureSchemaRegistry(
    url: String,
    apiKey: String,
    apiSecret: String
) : RestSchemaRegistry(url, apiKey, apiSecret, AzureApiStrategy)
