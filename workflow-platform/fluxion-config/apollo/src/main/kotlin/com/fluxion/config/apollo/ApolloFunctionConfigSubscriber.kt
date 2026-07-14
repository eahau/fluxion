/**
 * Apollo-backed function-config subscriber (Worker side).
 *
 * Kept as a thin subclass instead of instantiating [ApolloKeyedConfigSubscriber]
 * directly because the [mapKeyToSnapshot] override needs access to the
 * subclass logger; the parent `log` field is not initialised yet during the
 * super constructor call, so passing it as a lambda at construction time
 * would reference an uninitialised value.
 *
 * The namespace `workflow-functions` is hard-coded to match the convention
 * used by [ApolloFunctionConfigPublisher] on the Admin side.
 */
package com.fluxion.config.apollo

import com.fluxion.config.core.FunctionConfigSnapshot
import com.fluxion.config.core.SnapshotParser
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Indexed keyed subscriber that converts raw Apollo JSON payloads to
 * [FunctionConfigSnapshot] records via [SnapshotParser].
 */
class ApolloFunctionConfigSubscriber :
    ApolloKeyedConfigSubscriber<FunctionConfigSnapshot>(
        namespace = "workflow-functions",
        removedMapper = { key -> FunctionConfigSnapshot.removed(key) }
    ) {

    private val fnLog = LoggerFactory.getLogger(javaClass)

    override fun mapKeyToSnapshot(key: String, content: String): FunctionConfigSnapshot? =
        SnapshotParser.parseFunctionSnapshot(content, key, fnLog)
}
