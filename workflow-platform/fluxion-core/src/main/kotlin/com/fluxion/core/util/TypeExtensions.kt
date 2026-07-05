package com.fluxion.core.util

/**
 * 通用类型安全转换扩展。
 *
 * 使用 inline + reified 将具体类型的 cast 收敛到工具函数内部；
 * 对嵌套泛型类型（如 Map<String, Any>）使用 [uncheckedCast]，
 * 在工具函数单点抑制 unchecked cast 警告，调用方无需再写 @Suppress。
 */

/**
 * 将 Any? 安全转换为指定（非嵌套泛型）类型，转换失败返回 null。
 *
 * 适用于 String、Number 等具体类型；对于 Map/List 等嵌套泛型，
 * 请使用 [uncheckedCast] 或 [JsonUtil.convertValueOrNull]。
 */
inline fun <reified T> Any?.castOrNull(): T? = this as? T

/**
 * 将 Any? 安全转换为指定（非嵌套泛型）类型，转换失败返回默认值。
 */
inline fun <reified T> Any?.castOrDefault(default: T): T = (this as? T) ?: default

/**
 * 将 Any? 按目标类型做强转，失败返回 null。
 *
 * 用于 Map/List 等嵌套泛型场景：运行时只能检查到裸类，
 * 因此将 @Suppress("UNCHECKED_CAST") 收敛在本函数内，避免散落各处。
 *
 * 内联版本供调用点已知具体类型时使用，消除一次函数调用开销。
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> Any?.uncheckedCast(): T? = this as? T

/**
 * 将 Any? 按目标类型做强转，失败返回 null。
 *
 * 非内联版本供泛型方法内部使用（类型参数 T 无法传给 reified）。
 */
@Suppress("UNCHECKED_CAST")
fun <T> Any?.uncheckedCastGeneric(): T? = this as? T
