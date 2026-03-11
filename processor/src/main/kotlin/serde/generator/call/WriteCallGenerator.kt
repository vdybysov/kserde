package serde.generator.call

import serde.ext.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import serde.FnNames
import serde.VarNames
import serde.ext.findFunction
import serde.ext.isMap
import serde.ext.toNotNullAssertion

class WriteCallGenerator(
    override val logger: KSPLogger,
    override val resolver: Resolver
) : CallGenerator {

    override fun generate(
        code: CodeBlock.Builder,
        fnName: String,
        serdeClassName: ClassName,
        type: KSType,
        valueArg: CodeBlock,
        valueAlreadyNullChecked: Boolean
    ) {
        val typeArgs = when {
            type.isMap(resolver) -> listOf("{ it.toString() }")

            else -> emptyList()
        } + type.arguments
            .let { if (type.isMap(resolver)) it.drop(1) else it }
            .map { "{ ${generate(FnNames.WRITE, it.type!!.resolve(), CodeBlock.of("it"))} }" }
        // When valueAlreadyNullChecked: local var inside null check — compiler smart-casts, no assertion needed
        val valueExpr = if (valueAlreadyNullChecked) "$valueArg" else "${valueArg}${type.toNotNullAssertion()}"
        val args = listOf(valueExpr) + typeArgs
            .let { args ->
                resolver.getClassDeclarationByName(serdeClassName.canonicalName)
                    ?.findFunction(FnNames.WRITE)
                    ?.parameters?.size
                    ?.let { args.take(it - 2) }
                    ?: args
            }
        code.addStatement(
            "%L.%L(%L, %L)",
            serdeClassName,
            fnName,
            VarNames.WRITER,
            args.joinToString(", ")
        )
    }

}