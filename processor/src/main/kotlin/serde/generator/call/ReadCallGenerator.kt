package serde.generator.call

import serde.ext.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import serde.FnNames
import serde.VarNames
import serde.ext.findFunction
import serde.ext.isEnum
import serde.ext.isMap

class ReadCallGenerator(
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
        val readFn = resolver.getClassDeclarationByName(serdeClassName.canonicalName)?.findFunction(FnNames.READ)
        val typeArgs = when {
            type.isMap(resolver) -> listOf(
                type.arguments.firstOrNull()?.type?.resolve()?.let { keyType ->
                    when {
                        keyType.isEnum() -> "{ ${keyType.toClassName().canonicalName}.valueOf(it) }"
                        keyType.toTypeName() == Long::class.asTypeName() -> "{ it.toLong() }"
                        keyType.toTypeName() == Int::class.asTypeName() -> "{ it.toInt() }"
                        else -> "{ it.toString() }"
                    }
                } ?: "{ it }"
            )

            else -> emptyList()
        } + type.arguments
            .let { if (type.isMap(resolver)) it.drop(1) else it }
            .map { "{ ${generate(FnNames.READ, it.type!!.resolve(), CodeBlock.of("it"))} }" }
        val args = listOf(VarNames.READER) + typeArgs
            .let { args ->
                readFn?.parameters?.size
                    ?.let { args.take(it - 1) }
                    ?: args
            }
        val isEnumParametrized = type.isEnum() && readFn?.typeParameters?.size == 1
        code.add(
            "%M%L(${args.joinToString(", ")})%L",
            MemberName(serdeClassName, FnNames.READ),
            takeIf { isEnumParametrized }?.let { "<${type.makeNotNullable().toClassName()}>" } ?: "",
            takeIf { isEnumParametrized }?.let { " as ${type.toClassName()}" } ?: ""
        )
    }

}