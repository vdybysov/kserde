package serde.ext

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Overload for KSP 2.3+ where getClassDeclarationByName(KSName) replaced the String variant.
 */
fun Resolver.getClassDeclarationByName(name: String): KSClassDeclaration? =
    getClassDeclarationByName(getKSNameFromString(name))
