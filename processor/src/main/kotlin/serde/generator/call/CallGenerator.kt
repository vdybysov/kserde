package serde.generator.call

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import serde.ext.getSerdeClassName
import serde.generator.findStdSerdeDeclaration

interface CallGenerator {
    val logger: KSPLogger
    val resolver: Resolver

    fun generate(
        fnName: String,
        type: KSType,
        valueArg: CodeBlock,
        serdeClassName: ClassName? = null
    ): CodeBlock {
        val code = CodeBlock.builder()
        (serdeClassName ?: type.getSerdeClassName()
            .let { resolver.findStdSerdeDeclaration(it)?.toClassName() ?: it })
            .let { generate(code, fnName, it, type, valueArg) }
        return code.build()
    }

    fun generate(code: CodeBlock.Builder, fnName: String, serdeClassName: ClassName, type: KSType, valueArg: CodeBlock)
}