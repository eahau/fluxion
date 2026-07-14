package com.fluxion.core.spring.boot

import com.fluxion.core.engine.CachedExecution
import com.fluxion.core.engine.DeadLetterEntry
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.core.value.FunctionResult
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.core.value.SideEffect
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference

/**
 * Registers GraalVM Native Image runtime hints for the Fluxion engine.
 *
 * AOT/native-image cannot discover reflectively-instantiated classes at
 * build time, so this registrar enumerates the categories of types
 * that Jackson, Kotlin Reflect, AviatorScript, and Groovy actually
 * touch at runtime:
 *  1. Fluxion core value / model classes (Jackson serialisation).
 *  2. Kotlin collection utilities used by Jackson to materialise
 *     `Map<String, Any>` payloads.
 *  3. Jackson internal deserializer / type factories.
 *  4. Groovy runtime types for script nodes.
 *  5. Kotlin Reflect internal types used by the Kotlin Jackson module.
 *  6. AviatorScript runtime types for the expression evaluator.
 *  7. Class-path resources (service loaders, config files, logging).
 */
class FluxionNativeImageHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {

        // 1. Core engine models — every member category so Jackson can
        //    read/write fields, constructors, and accessor methods.
        val coreModels = listOf(
            EngineResult::class.java,
            ExecutionMeta::class.java,
            NodeExecutionRecord::class.java,
            FunctionResult::class.java,
            SideEffect::class.java,
            WorkflowDefinition::class.java,
            WorkflowNode::class.java,
            CachedExecution::class.java,
            DeadLetterEntry::class.java
        )
        coreModels.forEach { clazz ->
            hints.reflection()
                .registerType(clazz, *MemberCategory.entries.toTypedArray())
        }

        // 2. Kotlin collection helpers — needed by Jackson to materialise
        //    `Map<String, Any>` from JSON into Kotlin-flavoured collections.
        val kotlinCollections = listOf(
            "kotlin.collections.MapsKt",
            "kotlin.collections.CollectionsKt",
            "kotlin.collections.LinkedHashMap",
            "kotlin.collections.MutableMap",
            "kotlin.collections.MutableList"
        )
        kotlinCollections.forEach { typeName ->
            hints.reflection().registerType(
                TypeReference.of(typeName),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
            )
        }

        // 3. Jackson internal types — the build-time agent occasionally
        //    misses these when only `@JsonIgnore`-style metadata is used.
        val jacksonTypes = listOf(
            "com.fasterxml.jackson.databind.deser.std.MapDeserializer",
            "com.fasterxml.jackson.databind.deser.std.CollectionDeserializer",
            "com.fasterxml.jackson.databind.type.MapType",
            "com.fasterxml.jackson.databind.type.CollectionType",
            "com.fasterxml.jackson.databind.ObjectMapper"
        )
        jacksonTypes.forEach { typeName ->
            hints.reflection().registerType(
                TypeReference.of(typeName),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
            )
        }

        // 4. Groovy runtime — for SCRIPT-typed function nodes that run
        //    inline Groovy via `GroovyShell`.
        val groovyTypes = listOf(
            "groovy.lang.GroovyShell",
            "groovy.lang.GroovyClassLoader",
            "groovy.lang.Script",
            "groovy.lang.Binding",
            "org.codehaus.groovy.runtime.callsite.CallSite",
            "org.codehaus.groovy.runtime.callsite.CallSiteArray",
            "org.codehaus.groovy.control.CompilerConfiguration"
        )
        groovyTypes.forEach { typeName ->
            hints.reflection().registerType(
                TypeReference.of(typeName),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
            )
        }

        // 5. Kotlin Reflect internals — used by the Jackson KotlinModule
        //    when it needs to reflect on `data class` constructors.
        val kotlinReflectTypes = listOf(
            "kotlin.reflect.jvm.internal.KotlinReflectionInternalError",
            "kotlin.reflect.jvm.internal.KClassImpl",
            "kotlin.reflect.jvm.internal.KClassifierImpl",
            "kotlin.reflect.jvm.ReflectJvmMapping"
        )
        kotlinReflectTypes.forEach { typeName ->
            hints.reflection().registerType(
                TypeReference.of(typeName),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS
            )
        }

        // 6. AviatorScript runtime — used by `ExpressionEvaluator` for
        //    `#{...}` string templates and conditional next evaluators.
        val aviatorTypes = listOf(
            "com.googlecode.aviator.AviatorEvaluatorInstance",
            "com.googlecode.aviator.runtime.function.AbstractVariadicFunction",
            "com.googlecode.aviator.runtime.type.AviatorObject",
            "com.googlecode.aviator.runtime.type.AviatorJavaType",
            "com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType",
            "com.googlecode.aviator.runtime.type.AviatorNil",
            "com.googlecode.aviator.runtime.LambdaFunctionBootstrap",
            "com.googlecode.aviator.Options"
        )
        aviatorTypes.forEach { typeName ->
            hints.reflection().registerType(
                TypeReference.of(typeName),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
            )
        }

        // 7. Resource patterns — service-loaders and the usual config
        //    file names are not bundled by default unless explicitly listed.
        hints.resources()
            .registerPattern("META-INF/services/*")
            .registerPattern("log4j2*.yaml")
            .registerPattern("log4j2*.xml")
            .registerPattern("application*.yaml")
            .registerPattern("application*.yml")
            .registerPattern("application*.properties")
    }
}
