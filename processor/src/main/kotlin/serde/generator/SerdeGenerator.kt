package serde.generator

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import serde.ObjectSerde
import serde.generator.fn.ReadFnGenerator
import serde.generator.fn.WriteFnGenerator

class SerdeGenerator(
    private val readFnGenerator: ReadFnGenerator,
    private val writeFnGenerator: WriteFnGenerator
) {
    fun generate(cls: KSClassDeclaration, serdeClassName: ClassName): TypeSpec {
        return TypeSpec.objectBuilder(serdeClassName.simpleName)
            .addSuperinterface(
                ObjectSerde::class.asClassName().parameterizedBy(cls.asStarProjectedType().toTypeName())
            )
            .apply {
                runCatching {
                    addFunction(readFnGenerator.generate(cls))
                }.onFailure { cause ->
                    error("Failed to generate read function for ${cls.qualifiedName?.asString()} $cause")
                }
            }
            .apply {
                runCatching {
                    addFunction(writeFnGenerator.generate(cls))
                }.onFailure { cause ->
                    error("Failed to generate write function for ${cls.qualifiedName?.asString()} $cause")
                }
            }
            .build()
    }
}