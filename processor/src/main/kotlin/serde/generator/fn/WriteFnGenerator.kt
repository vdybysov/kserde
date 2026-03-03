package serde.generator.fn

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.bson.BsonWriter
import serde.FnNames
import serde.Types
import serde.VarNames
import serde.annotation.ReadOnly
import serde.ext.SubTypesInfo
import serde.ext.collectProperties
import serde.ext.findAnnotation
import serde.ext.findSubTypesInfo
import serde.ext.isBson
import serde.ext.isJson
import serde.generator.call.WriteCallGenerator

class WriteFnGenerator(
    private val resolver: Resolver,
    private val writeCallGenerator: WriteCallGenerator
) : FnGenerator {

    override fun generate(cls: KSClassDeclaration): FunSpec {
        val fn = FunSpec.builder(FnNames.WRITE_FIELDS)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(VarNames.WRITER, Types.WRITER)
            .addParameter(VarNames.VALUE, cls.asStarProjectedType().toTypeName())
        return if (cls.annotations.findAnnotation(ReadOnly::class) != null) {
            generateNotSupported(fn)
        } else {
            cls.findSubTypesInfo()
                ?.let { generateSubTypes(it, fn, cls) }
                ?: generateRegular(cls, fn)
        }
    }

    private fun generateRegular(
        cls: KSClassDeclaration,
        fn: FunSpec.Builder
    ): FunSpec {
        cls.collectProperties(resolver).forEach {
            if (it.ignore.full || (it.readOnly.json && it.readOnly.bson)) return@forEach
            val propName = "${VarNames.VALUE}.${it.name.original}"
            val writeCall = writeCallGenerator.generate(FnNames.WRITE, it.type, CodeBlock.of(propName), it.serdeWith)
            if (it.nullable) {
                fn.beginControlFlow("if (%L != null)", propName)
            }
            if (it.name.bson != it.name.original ||
                it.name.json != it.name.original ||
                it.ignore.bson != it.ignore.json ||
                it.readOnly.bson != it.readOnly.json
            ) {
                if (!it.ignore.bson && !it.readOnly.bson) {
                    fn.beginControlFlow("if (%L.%L())", VarNames.WRITER, BsonWriter::isBson.name)
                        .addStatement("%L.%L(%S)", VarNames.WRITER, BsonWriter::writeName.name, it.name.bson)
                        .addCode(writeCall)
                        .endControlFlow()
                }
                if (!it.ignore.json && !it.readOnly.json) {
                    fn.beginControlFlow("if (%L.%L())", VarNames.WRITER, BsonWriter::isJson.name)
                        .addStatement("%L.%L(%S)", VarNames.WRITER, BsonWriter::writeName.name, it.name.json)
                        .addCode(writeCall)
                        .endControlFlow()
                }
            } else {
                fn.addStatement("%L.%L(%S)", VarNames.WRITER, BsonWriter::writeName.name, it.name.original)
                    .addCode(writeCall)
            }
            if (it.nullable) {
                fn.endControlFlow()
            }
        }
        return fn.build()
    }

    private fun generateSubTypes(
        subTypesInfo: SubTypesInfo,
        fn: FunSpec.Builder,
        cls: KSClassDeclaration
    ): FunSpec {
        val (typePropName, types, fallbackType) = subTypesInfo
        fn.beginControlFlow("%L(%N.%N?.toString())", "when", VarNames.VALUE, typePropName)
        types.forEach { (name, type) ->
            fn.beginControlFlow("%S ->", name)
                .addStatement("%L.%L(%S)", VarNames.WRITER, "writeName", typePropName)
                .addStatement(
                    "%L.%L(%S)",
                    VarNames.WRITER,
                    "writeString",
                    name.takeIf { it.isNotEmpty() } ?: type.toClassName()
                )
                .addCode(
                    writeCallGenerator.generate(
                        "writeFields",
                        type,
                        CodeBlock.of("%L as %L", VarNames.VALUE, type.toClassName())
                    )
                )
                .endControlFlow()
        }
        when {
            fallbackType == null -> fn.addStatement(
                "else -> error(%P)",
                "Unknown subtype \$${VarNames.VALUE}::class for ${cls.toClassName()}"
            )

            else -> fn.beginControlFlow("else ->")
                .addCode(
                    writeCallGenerator.generate(
                        "writeFields",
                        fallbackType,
                        CodeBlock.of("%L as %L", VarNames.VALUE, fallbackType.toClassName())
                    )
                )
                .endControlFlow()
        }
        fn.endControlFlow()
        return fn.build()
    }

    private fun generateNotSupported(
        fn: FunSpec.Builder
    ): FunSpec =
        fn.addStatement("error(%P)", "Write not supported.").build()
}