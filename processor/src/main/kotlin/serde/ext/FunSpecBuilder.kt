package serde.ext

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toTypeName

fun FunSpec.Builder.addVar(name: String, type: KSType, defaultValue: String = "null") = addStatement(
    "var %L: %L = %L",
    name,
    type.makeNullable().toTypeName(),
    defaultValue
)