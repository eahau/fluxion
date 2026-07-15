package com.fluxion.schema.registry

import com.fluxion.schema.api.ApicurioApiStrategy
import com.fluxion.schema.api.RestSchemaRegistry

class ApicurioSchemaRegistry(
    url: String,
    username: String? = null,
    password: String? = null
) : RestSchemaRegistry(url, username, password, ApicurioApiStrategy)
