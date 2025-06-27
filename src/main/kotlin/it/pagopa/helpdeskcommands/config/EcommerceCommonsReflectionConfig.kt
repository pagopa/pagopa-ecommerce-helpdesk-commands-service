package it.pagopa.helpdeskcommands.config

import it.pagopa.ecommerce.commons.documents.BaseTransactionEvent
import it.pagopa.ecommerce.commons.documents.BaseTransactionView
import it.pagopa.ecommerce.commons.documents.PaymentNotice
import it.pagopa.ecommerce.commons.documents.PaymentTransferInformation
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference.listOf
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import org.springframework.util.ClassUtils

@Configuration
@ImportRuntimeHints(EcommerceDocumentsRuntimeHintsRegistrar::class)
class EcommerceCommonsReflectionConfig

class EcommerceDocumentsRuntimeHintsRegistrar : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {

        val classesToRegister =
            listOf(
                it.pagopa.ecommerce.commons.domain.Confidential::class.java,
                BaseTransactionEvent::class.java,
                BaseTransactionView::class.java,
                PaymentNotice::class.java,
                PaymentTransferInformation::class.java,
            )

        val packagesToRegister =
            listOf(
                "it.pagopa.ecommerce.commons.documents.v2",
                "it.pagopa.ecommerce.commons.domain.v2",
            )

        classesToRegister.forEach { classToRegister ->
            hints
                .reflection()
                .registerType(
                    classToRegister,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS,
                )
        }

        packagesToRegister.forEach { packagesToRegister ->
            registerClassesFromPackage(
                packagesToRegister,
                hints,
                classLoader ?: ClassLoader.getSystemClassLoader(),
            )
        }
    }

    private fun registerClassesFromPackage(
        packageName: String,
        hints: RuntimeHints,
        classLoader: ClassLoader,
    ) {
        val resolver = PathMatchingResourcePatternResolver(classLoader)
        val metadataReaderFactory = CachingMetadataReaderFactory(resolver)

        val packagePath = packageName.replace('.', '/')
        val pattern = "classpath*:$packagePath/**/*.class"

        try {
            val resources = resolver.getResources(pattern)
            for (resource in resources) {
                try {
                    val metadataReader = metadataReaderFactory.getMetadataReader(resource)
                    val className = metadataReader.classMetadata.className

                    // Skip interfaces and abstract classes
                    if (
                        !metadataReader.classMetadata.isInterface &&
                            !metadataReader.classMetadata.isAbstract
                    ) {
                        val clazz = ClassUtils.forName(className, classLoader)

                        hints
                            .reflection()
                            .registerType(
                                clazz,
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.DECLARED_FIELDS,
                            )
                    }
                } catch (e: Exception) {
                    // Log error but continue with other classes
                    println("Failed to register class from resource $resource: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Failed to scan package $packageName: ${e.message}")
        }
    }
}
