package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.queues.QueueEvent
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class TransactionEventService(
    @Qualifier("transactionRefundQueueAsyncClient")
    private val refundQueueClient: Mono<QueueAsyncClient>,
    @Qualifier("transactionNotificationQueueAsyncClient")
    private val notificationQueueClient: Mono<QueueAsyncClient>,
    @Value("\${azurestorage.queues.ttlSeconds}") private val transientQueueTTLSeconds: Long,
) : TransactionEventServiceInterface {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun sendRefundRequestedEvent(event: TransactionRefundRequestedEvent): Mono<Void> {
        logger.info("Sending refund message event for transaction: {}", event.transactionId)

        return refundQueueClient
            .flatMap { client ->
                try {
                    val queueEvent = QueueEvent(event, null)
                    client.sendMessageWithResponse(
                        queueEvent,
                        Duration.ZERO,
                        Duration.ofSeconds(transientQueueTTLSeconds)
                    )
                } catch (e: Exception) {
                    logger.error("Error during refund message creation: {}", e.message, e)
                    Mono.error<Any>(e)
                }
            }
            .doOnSuccess { logger.info("Refund message event sent successfully") }
            .doOnError { e ->
                logger.error("Failed to send refund message event: {}", e.message, e)
            }
            .then()
    }

    override fun sendNotificationRequestedEvent(
        event: TransactionUserReceiptRequestedEvent
    ): Mono<Void> {
        logger.info("Sending notification message event for transaction: {}", event.transactionId)

        return notificationQueueClient
            .flatMap { client ->
                try {
                    val queueEvent = QueueEvent(event, null)
                    client.sendMessageWithResponse(
                        queueEvent,
                        Duration.ZERO,
                        Duration.ofSeconds(transientQueueTTLSeconds)
                    )
                } catch (e: Exception) {
                    logger.error("Error during notification message creation: {}", e.message, e)
                    Mono.error<Any>(e)
                }
            }
            .doOnSuccess { logger.info("Notification message event sent successfully") }
            .doOnError { e ->
                logger.error("Failed to send notification message event: {}", e.message, e)
            }
            .then()
    }
}
