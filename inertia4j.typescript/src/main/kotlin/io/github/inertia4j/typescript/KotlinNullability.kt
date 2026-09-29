package io.github.inertia4j.typescript

import kotlin.metadata.isNullable
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import kotlin.metadata.jvm.getterSignature

/**
 * Reads property nullability of Kotlin classes from their `kotlin.Metadata` annotation.
 * The annotation is read reflectively because user classes live in another classloader.
 */
internal object KotlinNullability {
    private const val MetadataAnnotation = "kotlin.Metadata"

    /**
     * @return getter names of nullable properties, or `null` if [type] is not a Kotlin class.
     */
    @JvmStatic
    fun nullableAccessors(type: Class<*>): Set<String>? {
        val annotation = type.annotations.firstOrNull { it.annotationInterface().name == MetadataAnnotation } ?: return null

        val metadata = Metadata(
            kind = annotation.read("k"),
            metadataVersion = annotation.read("mv"),
            data1 = annotation.read("d1"),
            data2 = annotation.read("d2"),
            extraString = annotation.read("xs"),
            packageName = annotation.read("pn"),
            extraInt = annotation.read("xi"),
        )

        val classMetadata = KotlinClassMetadata.readLenient(metadata) as? KotlinClassMetadata.Class ?: return emptySet()

        return classMetadata.kmClass.properties
            .filter { it.returnType.isNullable }
            .mapNotNull { it.getterSignature?.name }
            .toSet()
    }

    private fun Annotation.annotationInterface(): Class<*> = javaClass.interfaces.first()

    @Suppress("UNCHECKED_CAST")
    private fun <T> Annotation.read(method: String): T = annotationInterface().getMethod(method).invoke(this) as T
}
