package serde.ext

import serde.ext.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import serde.std.kotlin.EnumSerde

fun KSType.getSerdeClassName() =
    when {
        isEnum() -> EnumSerde::class.asClassName()
        else -> declaration.annotations.findSerdeAnnotation()?.getSerdeWithClassName()
            ?: getSerdeClassName(
                this,
                { (it.declaration as? KSClassDeclaration)?.parentDeclaration as? KSClassDeclaration },
                { it.declaration.simpleName.asString() },
                { it.declaration.packageName.asString() },
                { it.toClassName().simpleName },
                { it.parentDeclaration as? KSClassDeclaration }
            ).let { (packageName, className) -> ClassName(packageName, className) }
    }

fun KSType.isAny() = toTypeName() == Any::class.asClassName()
fun KSType.isEnum() = (declaration as? KSClassDeclaration)?.let { it.classKind == ClassKind.ENUM_CLASS } == true
fun KSType.isMap(resolver: Resolver): Boolean {
    val mapDeclaration = resolver.getClassDeclarationByName(Map::class.qualifiedName!!)
        ?: error("Map interface not found in resolver")
    return mapDeclaration.asStarProjectedType().makeNullable().isAssignableFrom(this)
}


fun KSType.toNotNullAssertion() = if (isMarkedNullable) "!!" else ""