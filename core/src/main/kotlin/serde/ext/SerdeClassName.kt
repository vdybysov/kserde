package serde.ext

import serde.VarNames
import serde.annotation.Serde
import serde.annotation.SubTypes
import serde.std.kotlin.EnumSerde

fun <C, P> getSerdeClassName(
    type: C,
    parentGetter: (C) -> P?,
    simpleNameGetter: (C) -> String,
    packageNameGetter: (C) -> String,
    parentSimpleNameGetter: (P) -> String,
    parentParentGetter: (P) -> P?,
): Pair<String, String> {
    var parent: P? = parentGetter(type)
    var name = "${simpleNameGetter(type)}${VarNames.SERDE}"
    while (parent != null) {
        name = "${parentSimpleNameGetter(parent)}${name}"
        parent = parentParentGetter(parent)
    }
    return packageNameGetter(type) to name
}

fun Class<*>.findParentWithAnnotation(annotationClass: Class<out Annotation>): Class<*>? {
    var parent = superclass
    while (parent != null && parent != Any::class.java) {
        if (parent.getAnnotation(annotationClass) != null) return parent
        parent = parent.superclass
    }
    return null
}

fun Class<*>.getSerdeClassName(): Pair<String, String> =
    getAnnotation(Serde::class.java)?.with
        ?.takeIf { it != Any::class }
        ?.let { it.java.packageName to it.simpleName!! }
        ?: findParentWithAnnotation(SubTypes::class.java)?.getSerdeClassName()
        ?: when {
            isEnum -> EnumSerde.javaClass.packageName to EnumSerde.javaClass.simpleName
            else -> getSerdeClassName(
                this,
                { it.enclosingClass },
                { it.simpleName },
                { it.packageName },
                { it.simpleName },
                { it.enclosingClass }
            )
        }