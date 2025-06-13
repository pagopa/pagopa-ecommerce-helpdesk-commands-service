package it.pagopa.helpdeskcommands.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

@Configuration @ImportRuntimeHints(AzureStorageRuntimeHints::class) class AzureStorageAotConfig

class AzureStorageRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        registerAzureStorageProxies(hints)
        registerSerializationClasses(hints)
        registerTransactionEventTypeResolverInitialization(hints)
        registerStaticFieldAccess(hints)
    }

    private fun registerAzureStorageProxies(hints: RuntimeHints) {
        hints.proxies().apply {
            registerJdkProxy(
                com.azure.storage.queue.implementation.ServicesImpl.ServicesService::class.java
            )
            registerJdkProxy(
                com.azure.storage.queue.implementation.QueuesImpl.QueuesService::class.java
            )
            registerJdkProxy(
                com.azure.storage.queue.implementation.MessagesImpl.MessagesService::class.java
            )
            registerJdkProxy(
                com.azure.storage.queue.implementation.MessageIdsImpl.MessageIdsService::class.java
            )
        }
    }

    private fun registerSerializationClasses(hints: RuntimeHints) {
        hints.reflection().apply {
            registerType(
                it.pagopa.ecommerce.commons.queues.QueueEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )

            registerType(
                it.pagopa.ecommerce.commons.queues.TracingInfo::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )

            registerType(
                it.pagopa.ecommerce.commons.queues.mixin.serialization.v2
                        .QueueEventMixInClassFieldDiscriminator::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.queues.StrictJsonSerializerProvider::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.serialization
                        .TransactionEventTypeResolver::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            // all TransactionEvent subclasses (21 total) required for TransactionEventTypeResolver
            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionActivatedEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2
                        .TransactionAuthorizationCompletedEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2
                        .TransactionAuthorizationOutcomeWaitingEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2
                        .TransactionAuthorizationRequestedEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionClosedEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionClosureErrorEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionClosureFailedEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionClosureRequestedEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionClosureRetriedEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionExpiredEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionRefundedEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionRefundErrorEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRetriedEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionUserCanceledEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptAddedEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptAddErrorEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2
                        .TransactionUserReceiptAddRetriedEvent::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.BaseTransactionClosureEvent::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
                        .RefundTrigger::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData.Outcome::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )

            registerType(
                it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData
                        .NotificationTrigger::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )
        }
    }

    private fun registerTransactionEventTypeResolverInitialization(hints: RuntimeHints) {
        hints.reflection().apply {
            registerType(
                org.springframework.context.annotation
                        .ClassPathScanningCandidateComponentProvider::class
                    .java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            registerType(
                org.springframework.core.type.filter.AssignableTypeFilter::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )

            registerType(
                org.springframework.core.type.StandardAnnotationMetadata::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
            )

            registerType(
                org.springframework.core.type.classreading.CachingMetadataReaderFactory::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS
            )
        }

        hints.resources().apply {
            registerPattern("META-INF/spring.factories")
            registerPattern("**/it/pagopa/ecommerce/commons/documents/v2/*.class")
            registerPattern("**/*.class")
        }
    }

    private fun registerStaticFieldAccess(hints: RuntimeHints) {
        hints.reflection().apply {
            val resolverClass =
                it.pagopa.ecommerce.commons.documents.v2.serialization
                        .TransactionEventTypeResolver::class
                    .java

            // register TransactionEventTypeResolver with special field access permissions
            registerType(
                resolverClass,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )

            // register specific field access for static maps
            try {
                val classToPathField = resolverClass.getDeclaredField("CLASS_TO_PATH_MAP")
                val pathToClassField = resolverClass.getDeclaredField("PATH_TO_CLASS_MAP")

                registerField(classToPathField)
                registerField(pathToClassField)
            } catch (e: NoSuchFieldException) {
                // fields don't exist, skip registration
            }
        }
    }
}
