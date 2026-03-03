package serde.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import org.bson.BsonReader
import org.bson.BsonWriter
import serde.PackageNames
import serde.annotation.Serde
import serde.ext.findSerdeAnnotation
import serde.ext.getSerdeClassName
import serde.ext.getSerdeWith
import serde.ext.isBson
import serde.ext.isJson
import serde.ext.readDocument
import serde.ext.writeDocument
import serde.generator.SerdeGenerator
import serde.generator.call.ReadCallGenerator
import serde.generator.call.WriteCallGenerator
import serde.generator.fn.ReadFnGenerator
import serde.generator.fn.WriteFnGenerator

class SerdeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val serdeGenerator = SerdeGenerator(
            ReadFnGenerator(logger, resolver, ReadCallGenerator(logger, resolver)),
            WriteFnGenerator(resolver, WriteCallGenerator(logger, resolver))
        )
        resolver.getSymbolsWithAnnotation(Serde::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .forEach {
                genFile(serdeGenerator, it)?.writeTo(codeGenerator, Dependencies(false))
            }
        return emptyList()
    }

    private fun genFile(
        serdeGenerator: SerdeGenerator,
        cls: KSClassDeclaration,
    ): FileSpec? {
        if (cls.annotations.findSerdeAnnotation()?.getSerdeWith() != null) {
            return null
        }
        val serdeClassName = cls.asStarProjectedType().getSerdeClassName()
        return FileSpec.builder(serdeClassName)
            .addImport(PackageNames.EXT, BsonReader::readDocument.name)
            .addImport(PackageNames.EXT, "readDocumentFieldAndReset")
            .addImport(PackageNames.EXT, BsonWriter::writeDocument.name)
            .addImport(PackageNames.EXT, BsonReader::isJson.name)
            .addImport(PackageNames.EXT, BsonReader::isBson.name)
            .addType(serdeGenerator.generate(cls, serdeClassName))
            .build()
    }
}