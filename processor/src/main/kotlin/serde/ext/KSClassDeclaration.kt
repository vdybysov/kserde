package serde.ext

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import serde.FnNames
import serde.annotation.Mutable
import serde.annotation.PropertyIgnore
import serde.annotation.PropertyName
import serde.annotation.ReadOnly
import serde.annotation.SubTypes
import serde.annotation.WriteOnly

fun List<KSValueParameter>.collect(cls: KSClassDeclaration, resolver: Resolver) = map {
    val paramName = it.name?.getShortName() ?: error("Constructor parameter has no name")
    val paramAnnotations = it.annotations.toList()
    val accessorAnnotations = cls.getAllFunctions()
        .find { fn -> fn.simpleName.getShortName() == paramName }
        ?.annotations?.toList().orEmpty()
    val propertyAnnotations = cls.getAllProperties()
        .find { p -> p.simpleName.getShortName() == paramName }
        ?.annotations?.toList().orEmpty()
    // For Java records, @Serde may appear on record component (property) but not on constructor param.
    // Merge all sources so we don't miss the annotation.
    val allAnnotations = (paramAnnotations + accessorAnnotations + propertyAnnotations).distinct()
    mapProperty(
        resolver,
        it.type,
        paramName,
        annotations = allAnnotations.asSequence()
    )
}

fun Sequence<KSPropertyDeclaration>.collect(cls: KSClassDeclaration, resolver: Resolver) = map {
    val propName = it.simpleName.getShortName()
    val propertyAnnotations = it.annotations.toList()
    val accessorAnnotations = cls.getAllFunctions()
        .find { fn -> fn.simpleName.getShortName() == propName }
        ?.annotations?.toList().orEmpty()
    // For Java records with explicit constructor, @Serde may be on record component (constructor param)
    // but not on property/accessor. Merge param annotations from matching constructor.
    val paramAnnotations = cls.findConstructors()
        .flatMap { it.parameters.asSequence() }
        .find { it.name?.getShortName() == propName }
        ?.annotations?.toList().orEmpty()
    val allAnnotations = (propertyAnnotations + accessorAnnotations + paramAnnotations).distinct()
    mapProperty(
        resolver,
        it.type,
        propName,
        annotations = allAnnotations.asSequence()
    )
}

fun mapProperty(
    resolver: Resolver,
    type: KSTypeReference,
    name: String,
    annotations: Sequence<KSAnnotation>,
): Property =
    type.resolve().let { resolved ->
        Property(
            type = resolved,

            name = annotations.findAnnotation(PropertyName::class).let { propNameAnnotation ->
                Property.Name(
                    original = name,
                    json = propNameAnnotation?.getArgument<String>("json")?.takeIf { it.isNotEmpty() } ?: name,
                    bson = propNameAnnotation?.getArgument<String>("bson")?.takeIf { it.isNotEmpty() } ?: name,
                )
            },

            nullable = resolved.isMarkedNullable || resolved.nullability != Nullability.NOT_NULL,

            serdeWith = annotations.findSerdeAnnotation()?.getSerdeWithClassName()
                ?: (resolved.declaration.qualifiedName
                    ?: error("Could not get qualifiedName for ${resolved.toTypeName()}"))
                    .let { resolver.getClassDeclarationByName(it) }
                    ?.annotations?.findSerdeAnnotation()?.getSerdeWithClassName(),

            ignore = annotations.findAnnotation(PropertyIgnore::class).let {
                Property.Ignore(
                    json = it?.getArgument<Boolean>("json") == true,
                    bson = it?.getArgument<Boolean>("bson") == true
                )
            },

            readOnly = annotations.findAnnotation(ReadOnly::class).let {
                Property.ReadOnly(
                    json = it?.getArgument<Boolean>("json") == true,
                    bson = it?.getArgument<Boolean>("bson") == true
                )
            },

            writeOnly = annotations.findAnnotation(WriteOnly::class).let {
                Property.WriteOnly(
                    json = it?.getArgument<Boolean>("json") == true,
                    bson = it?.getArgument<Boolean>("bson") == true
                )
            }
        )
    }

data class Property(
    val type: KSType,
    val name: Name,
    val nullable: Boolean,
    val serdeWith: ClassName? = null,
    val ignore: Ignore,
    val readOnly: ReadOnly,
    val writeOnly: WriteOnly
) {
    data class Name(val original: String, val json: String, val bson: String)
    data class Ignore(val json: Boolean, val bson: Boolean) {
        val full = json && bson
    }

    data class ReadOnly(val json: Boolean, val bson: Boolean)
    data class WriteOnly(val json: Boolean, val bson: Boolean)
}

/**
 * When property has a custom serde, use its read() return type for variable declaration.
 * Fixes Java List -> MutableList mismatch: custom serdes typically return kotlin.collections.List.
 */
fun Property.getEffectiveVarType(resolver: Resolver): KSType =
    serdeWith?.let { serdeClass ->
        resolver.getClassDeclarationByName(serdeClass.canonicalName)
            ?.getAllFunctions()
            ?.find { it.simpleName.getShortName() == FnNames.READ }
            ?.returnType
            ?.resolve()
    } ?: type

fun KSClassDeclaration.findConstructors() = getAllFunctions().filter { it.simpleName.getShortName() == "<init>" }

/**
 * Reorder properties to match constructor parameter order.
 * For Java classes, getAllProperties() order may differ from constructor (e.g. child props before parent).
 */
private fun KSClassDeclaration.reorderByConstructor(allProps: List<Property>): List<Property> {
    val constructor = findConstructors().maxByOrNull { it.parameters.size } ?: return allProps
    val paramNames = constructor.parameters.mapNotNull { it.name?.getShortName() }
    val propNames = allProps.map { it.name.original }.toSet()
    if (paramNames.toSet() != propNames) return allProps
    val byName = allProps.associateBy { it.name.original }
    return paramNames.mapNotNull { byName[it] }
}

fun KSClassDeclaration.collectProperties(resolver: Resolver): List<Property> =
    getAllProperties()
        .filter { !it.modifiers.contains(Modifier.JAVA_STATIC) }
        .collect(this, resolver)
        .toList()
        .let { allProps ->
            when {
                annotations.findAnnotation(Mutable::class) != null -> allProps
                primaryConstructor != null -> {
                    val params = primaryConstructor!!.parameters
                    val paramNames = params.mapNotNull { it.name?.getShortName() }.toSet()
                    val propNames = allProps.map { it.name.original }.toSet()
                    // Java record with explicit constructor: KSP may put @Serde on record component (property)
                    // but not on constructor param. Use property-based collect when params match.
                    if (allProps.isNotEmpty() && paramNames == propNames) reorderByConstructor(allProps)
                    else params.collect(this, resolver)
                }
                // Java record: KSP may put @Serde on record component (property) but not on constructor param.
                allProps.isNotEmpty() -> reorderByConstructor(allProps)

                else -> findConstructors()
                    .maxByOrNull { it.parameters.size }
                    ?.parameters
                    ?.collect(this, resolver)
                    ?: allProps
            }
        }

fun KSClassDeclaration.findFunction(fnName: String): KSFunctionDeclaration? = getAllFunctions()
    .find { fn -> fn.simpleName.getShortName() == fnName }

data class SubTypesInfo(
    val propertyName: String,
    val propertyType: KSType?,
    val types: Map<String, KSType>,
    val fallbackType: KSType?,
)

fun KSClassDeclaration.findSubTypesInfo(): SubTypesInfo? =
    annotations.findAnnotation(SubTypes::class)
        ?.also {
            if (!isAbstract() && classKind != ClassKind.INTERFACE) {
                error("Found @${SubTypes::class.simpleName} at non-abstract class ${toClassName().canonicalName}")
            }
        }
        ?.let { annotation ->
            val propertyName = annotation.getArgument<String>("propertyName")
                ?: error("@SubTypes requires propertyName")
            val typesArg = annotation.getArgument<List<KSAnnotation>>("types")
                ?: error("@SubTypes requires types")
            val propertyType = getAllProperties()
                .find { it.simpleName.getShortName() == propertyName }
                ?.type?.resolve()
            SubTypesInfo(
                propertyName = propertyName,
                propertyType = propertyType,
                types = typesArg.associate {
                    val name = it.getArgument<String>("name")
                        ?: error("@SubTypes.Type requires name")
                    val type = it.getArgument<KSType>("type")
                        ?: error("@SubTypes.Type requires type")
                    name to type
                },
                fallbackType = annotation.getArgument<KSType>("fallbackType")?.takeUnless(KSType::isAny)
            )
        }