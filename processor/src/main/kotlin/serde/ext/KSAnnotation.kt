package serde.ext

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import kotlin.reflect.KClass
import serde.annotation.Serde

fun Sequence<KSAnnotation>.findAnnotation(type: KClass<*>) =
    find { annotation ->
        annotation.annotationType.resolve().takeUnless { it.isError }?.toClassName() == type.asClassName()
    }

fun Sequence<KSAnnotation>.findSerdeAnnotation() = findAnnotation(Serde::class)

inline fun <reified T> KSAnnotation.getArgument(name: String) = arguments
    .find { it.name?.getShortName() == name }
    ?.let { it.value as T }

fun KSAnnotation.getSerdeWith() = getArgument<KSType>("with")
    ?.takeUnless { it.isAny() }

fun KSAnnotation.getSerdeWithClassName() = getSerdeWith()
    ?.toClassName()