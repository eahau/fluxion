package com.fluxion.schema.registry

import com.fluxion.schema.api.ConfluentApiStrategy
import com.fluxion.schema.api.RestSchemaRegistry

class ConfluentSchemaRegistry(
    url: String,
    username: String? = null,
    password: String? = null
) : RestSchemaRegistry(url, username, password, ConfluentApiStrategy)
