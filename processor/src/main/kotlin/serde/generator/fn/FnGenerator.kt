package serde.generator.fn

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FunSpec

interface FnGenerator {
    fun generate(cls: KSClassDeclaration): FunSpec
}