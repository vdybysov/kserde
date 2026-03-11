package serde.generator.fn

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.bson.BsonReader
import serde.BsonReaderExtNames
import serde.FnNames
import serde.Types
import serde.VarNames
import serde.annotation.WriteOnly
import serde.ext.Property
import serde.ext.SubTypesInfo
import serde.ext.addVar
import serde.ext.collect
import serde.ext.collectProperties
import serde.ext.findAnnotation
import serde.ext.findSerdeAnnotation
import serde.ext.findSubTypesInfo
import serde.annotation.Mutable
import serde.ext.isBson
import serde.ext.isJson
import serde.ext.readDocument
import serde.generator.call.ReadCallGenerator

class ReadFnGenerator(
    private val logger: KSPLogger,
    private val resolver: Resolver,
    private val readCallGenerator: ReadCallGenerator
) : FnGenerator {

    private val skipValueStmt = CodeBlock.Builder()
        .addStatement("%L.%L()", VarNames.READER, BsonReader::skipValue.name)
        .build()

    override fun generate(cls: KSClassDeclaration): FunSpec {
        val fn = FunSpec.builder(FnNames.READ)
            .addModifiers(KModifier.OVERRIDE)
            .returns(cls.asStarProjectedType().toTypeName())
            .addParameter(VarNames.READER, Types.READER)
        return if (cls.annotations.findAnnotation(WriteOnly::class) != null) {
            generateNotSupported(fn)
        } else {
            cls.findSubTypesInfo()
                ?.let { generateSubTypes(it, fn, cls) }
                ?: (cls.annotations.findAnnotation(Mutable::class) != null).let { if (it) generateMutable(cls, fn) else null }
                ?: generateRegular(cls, fn)
        }
    }

    private fun generateMutable(
        cls: KSClassDeclaration,
        fn: FunSpec.Builder
    ): FunSpec {
        fn.beginControlFlow("return %L().apply", cls.toClassName())
        fn
            .beginControlFlow("${VarNames.READER}.${BsonReader::readDocument.name}")
            .addCode("field ->")
            .beginControlFlow("when(field)")
        cls.getAllProperties().collect(cls, resolver).forEach {
            val assignment = CodeBlock.builder()
                .addStatement(
                    "%N(${readCallGenerator.generate(FnNames.READ, it.type, CodeBlock.of(""), it.serdeWith)})",
                    "set${it.name.original.replaceFirstChar { c -> c.uppercase() }}"
                )
                .build()
            generateCase(fn, it, assignment)
        }
        fn.addStatement("else -> ").addCode(skipValueStmt)
        fn.endControlFlow()
        fn.endControlFlow()
        fn.endControlFlow()
        return fn.build()
    }

    private fun generateCase(fn: FunSpec.Builder, prop: Property, assignment: CodeBlock) = with(prop) {
        if (name.bson != name.original ||
            name.json != name.original ||
            ignore.json != ignore.bson ||
            writeOnly.json != writeOnly.bson
        ) {
            fn.beginControlFlow("%S -> ", name.json)
                .beginControlFlow("if (%L.%L())", VarNames.READER, BsonReader::isJson.name)
                .addCode(if (ignore.json || writeOnly.json) skipValueStmt else assignment)
                .endControlFlow()
            if (name.bson != name.json) {
                fn.beginControlFlow("else")
                    .addCode(skipValueStmt)
                    .endControlFlow()
                    .endControlFlow()
                    .beginControlFlow("%S -> ", name.bson)
            }
            fn.beginControlFlow("if (%L.%L())", VarNames.READER, BsonReader::isBson.name)
                .addCode(if (ignore.bson || writeOnly.bson) skipValueStmt else assignment)
                .endControlFlow()
            if (name.bson != name.json) {
                fn.beginControlFlow("else")
                    .addCode(skipValueStmt)
                    .endControlFlow()
            }
            fn.endControlFlow()
        } else {
            fn.addStatement("%S ->", name.original)
                .addCode(if (ignore.full) skipValueStmt else assignment)
        }
    }

    private fun generateRegular(cls: KSClassDeclaration, fn: FunSpec.Builder): FunSpec {
        val props = cls.collectProperties(resolver)
        props.forEach { (type, name) -> fn.addVar(name.original, type) }
        fn
            .beginControlFlow("${VarNames.READER}.${BsonReader::readDocument.name}")
            .beginControlFlow("when(it)")
        props.forEach {
            val assignment = CodeBlock.builder()
                .addStatement(
                    "%L = %L",
                    it.name.original,
                    readCallGenerator.generate(FnNames.READ, it.type, CodeBlock.of(it.name.original), it.serdeWith)
                )
                .build()
            generateCase(fn, it, assignment)
        }
        fn
            .addStatement("else -> ${VarNames.READER}.${BsonReader::skipValue.name}()")
            .endControlFlow()
            .endControlFlow()
        props.filterNot { it.nullable }.forEach { (_, name) ->
            fn.beginControlFlow("require(%L != null)", name.original)
                .addStatement("%S", "Parameter '${name.original}' is required to construct ${cls.toClassName()}.")
                .endControlFlow()
        }
        return fn
            .addStatement(
                "return %L(%L)",
                cls.toClassName(),
                props.joinToString(", ") {
                    if (it.nullable) it.name.original else "requireNotNull(${it.name.original})"
                }
            )
            .build()
    }

    private fun generateSubTypes(
        subTypesInfo: SubTypesInfo,
        fn: FunSpec.Builder,
        cls: KSClassDeclaration
    ): FunSpec {
        val (typePropName, _, types, _) = subTypesInfo
        fn.beginControlFlow(
            "val %N = %L.%L(%S)",
            typePropName, VarNames.READER, BsonReaderExtNames.READ_DOCUMENT_FIELD_AND_RESET, typePropName
        )
            .addStatement("%L.%L()", VarNames.READER, BsonReaderExtNames.READ_STRING)
            .endControlFlow()
        fn.beginControlFlow("return when(%N)", typePropName)
        types.forEach { (name, type) ->
            fn.addStatement(
                "%S -> %L",
                name.takeIf { it.isNotEmpty() } ?: type.toClassName(),
                readCallGenerator.generate(FnNames.READ, type, CodeBlock.of(""))
            )
        }
        fn.addStatement("else -> error(%P)", "Unknown subtype \$${typePropName} for ${cls.toClassName()}")
        fn.endControlFlow()
        return fn.build()
    }

    private fun generateNotSupported(
        fn: FunSpec.Builder
    ): FunSpec =
        fn.addStatement("error(%P)", "Read not supported.").build()

}