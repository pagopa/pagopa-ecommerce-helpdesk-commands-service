package it.pagopa.helpdeskcommands.config

import it.pagopa.ecommerce.commons.documents.v2.*
import it.pagopa.ecommerce.commons.documents.v2.serialization.TransactionEventTypeResolver
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
class TransactionEventResolverNativeConfig {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val eventMappings =
        mapOf(
            TransactionActivatedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionActivatedEvent",
            TransactionAuthorizationCompletedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionAuthorizationCompletedEvent",
            TransactionAuthorizationOutcomeWaitingEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionAuthorizationOutcomeWaitingEvent",
            TransactionAuthorizationRequestedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionAuthorizationRequestedEvent",
            TransactionClosedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionClosedEvent",
            TransactionClosureErrorEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionClosureErrorEvent",
            TransactionClosureFailedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionClosureFailedEvent",
            TransactionClosureRequestedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionClosureRequestedEvent",
            TransactionClosureRetriedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionClosureRetriedEvent",
            TransactionExpiredEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionExpiredEvent",
            TransactionRefundedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionRefundedEvent",
            TransactionRefundErrorEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionRefundErrorEvent",
            TransactionRefundRequestedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent",
            TransactionRefundRetriedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRetriedEvent",
            TransactionUserCanceledEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionUserCanceledEvent",
            TransactionUserReceiptAddedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptAddedEvent",
            TransactionUserReceiptAddErrorEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptAddErrorEvent",
            TransactionUserReceiptAddRetriedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptAddRetriedEvent",
            TransactionUserReceiptRequestedEvent::class.java to
                "it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent"
        )

    @PostConstruct
    fun populateTransactionEventMaps() {
        try {
            logger.info(
                "Populating TransactionEventTypeResolver static maps for native compilation"
            )

            val classToPathField =
                TransactionEventTypeResolver::class.java.getDeclaredField("CLASS_TO_PATH_MAP")
            val pathToClassField =
                TransactionEventTypeResolver::class.java.getDeclaredField("PATH_TO_CLASS_MAP")

            classToPathField.isAccessible = true
            pathToClassField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val classToPathMap =
                classToPathField.get(null) as MutableMap<Class<out TransactionEvent<*>>, String>
            @Suppress("UNCHECKED_CAST")
            val pathToClassMap =
                pathToClassField.get(null) as MutableMap<String, Class<out TransactionEvent<*>>>

            classToPathMap.clear()
            pathToClassMap.clear()

            eventMappings.forEach { (clazz, path) ->
                @Suppress("UNCHECKED_CAST") val eventClass = clazz as Class<out TransactionEvent<*>>
                classToPathMap[eventClass] = path
                pathToClassMap[path] = eventClass
            }

            logger.info("Successfully populated ${classToPathMap.size} event mappings")

            val testEvent = TransactionRefundRequestedEvent()
            val resolver = TransactionEventTypeResolver()
            val eventCode = resolver.idFromValue(testEvent)
            logger.info(
                "Verification successful - TransactionRefundRequestedEvent resolves to: $eventCode"
            )
        } catch (e: Exception) {
            logger.error("Failed to populate TransactionEventTypeResolver maps: ${e.message}", e)
            throw e
        }
    }
}
