package serde.annotation

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class SubTypes(
    val propertyName: String = "type",
    val types: Array<Type>,
    val fallbackType: KClass<*> = Any::class
) {
    annotation class Type(val type: KClass<*>, val name: String)
}