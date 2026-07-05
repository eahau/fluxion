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
 * GraalVM Native Image 反射提示注册器。
 *
 * Native Image 默认不支持反射访问，需要通过 [RuntimeHints] 显式声明：
 *   1. Jackson 动态序列化/反序列化的类型（Map<String, Any>、多态类型）
 *   2. Kotlin data class（Kotlin Reflect 需要访问构造函数和属性）
 *   3. Groovy 脚本引擎运行时类
 *   4. Spring AOP / CGLIB 代理所需类型
 *
 * 传统 JDK 模式下此注册器无副作用（hints 仅被 Native Image 编译器消费）。
 */
class FluxionNativeImageHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {

        // ─── 1. 核心模型类（Jackson 序列化 + Kotlin Reflect）───────────────
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

        // ─── 2. Kotlin 集合类型（Jackson 反序列化 Map<String, Any> 时需要）───
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

        // ─── 3. Jackson 内部类型（动态类型解析需要）─────────────────────
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

        // ─── 4. Groovy 脚本引擎（动态加载类需要反射）──────────────────
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

        // ─── 5. Kotlin Reflect 核心类 ──────────────────────────────────
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

        // ─── 6. AviatorScript 表达式引擎（fn.* 抽象函数注册 + 字节码运行时） ─
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

        // ─── 7. 资源提示 ───────────────────────────────────────────────
        hints.resources()
            .registerPattern("META-INF/services/*")
            .registerPattern("log4j2*.yaml")
            .registerPattern("log4j2*.xml")
            .registerPattern("application*.yaml")
            .registerPattern("application*.yml")
            .registerPattern("application*.properties")
    }
}
