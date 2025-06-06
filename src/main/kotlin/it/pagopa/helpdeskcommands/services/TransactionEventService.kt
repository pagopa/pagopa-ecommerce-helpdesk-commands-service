package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.ecommerce.commons.queues.TracingUtils
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
    private val tracingUtils: TracingUtils
) : TransactionEventServiceInterface {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun sendRefundRequestedEvent(event: TransactionRefundRequestedEvent): Mono<Void> {
        logger.info("📤 Sending refund for transaction: {}", event.transactionId)

        return refundQueueClient
            .doOnNext { logger.info("📤 Queue client obtained") }
            .flatMap { client ->
                logger.info("📤 Creating message...")
                try {
                    val queueEvent = QueueEvent(event, null)
                    logger.info("📤 Sending to Azure...")

                    client.sendMessageWithResponse(
                        queueEvent,
                        Duration.ZERO,
                        Duration.ofSeconds(transientQueueTTLSeconds)
                    )
                } catch (e: Exception) {
                    logger.error("📤 Error: {}", e.message, e)
                    Mono.error<Any>(e)
                }
            }
            .doOnSuccess { logger.info("📤 ✅ Success!") }
            .doOnError { e -> logger.error("📤 ❌ Failed: {}", e.message, e) }
            .then()
    }

    override fun sendNotificationRequestedEvent(
        event: TransactionUserReceiptRequestedEvent
    ): Mono<Void> {
        logger.info("📧 Sending notification for transaction: {}", event.transactionId)

        return notificationQueueClient
            .doOnNext { logger.info("📧 Queue client obtained") }
            .flatMap { client ->
                try {
                    val queueEvent = QueueEvent(event, null)
                    client.sendMessageWithResponse(
                        queueEvent,
                        Duration.ZERO,
                        Duration.ofSeconds(transientQueueTTLSeconds)
                    )
                } catch (e: Exception) {
                    logger.error("📧 Error: {}", e.message, e)
                    Mono.error<Any>(e)
                }
            }
            .doOnSuccess { logger.info("📧 ✅ Success!") }
            .doOnError { e -> logger.error("📧 ❌ Failed: {}", e.message, e) }
            .then()
    }
}
