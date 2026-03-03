package serde.generator

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import serde.PackageNames

@OptIn(KspExperimental::class)
fun Resolver.findStdSerdeDeclaration(target: ClassName) =
    getDeclarationsFromPackage("${PackageNames.STD}.${target.packageName}")
        .filterIsInstance<KSClassDeclaration>()
        .find { it.simpleName.asString() == target.simpleName }