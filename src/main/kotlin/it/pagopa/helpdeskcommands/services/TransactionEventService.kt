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
import reactor.core.publisher.Mono

class TransactionEventService(
    @Qualifier("transactionRefundQueueAsyncClient")
    private val refundQueueClient: Mono<QueueAsyncClient>,
    @Qualifier("transactionNotificationQueueAsyncClient")
    private val notificationQueueClient: Mono<QueueAsyncClient>,
    @Value("\${azurestorage.queues.ttlSeconds}") private val transientQueueTTLSeconds: Long,
    private val tracingUtils: TracingUtils
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Sends a refund request event to the transaction refund queue for processing by the dedicated
     * service.
     *
     * @param event The refund request event with transaction details and manual trigger
     * @return Mono that completes when the message is queued successfully
     */
    @Suppress("kotlin:S6508")
    fun sendRefundRequestedEvent(event: TransactionRefundRequestedEvent): Mono<Void> {
        return tracingUtils.traceMono(this.javaClass.simpleName) { tracingInfo ->
            refundQueueClient
                .flatMap { client ->
                    client.sendMessageWithResponse(
                        QueueEvent(event, tracingInfo),
                        Duration.ZERO,
                        Duration.ofSeconds(transientQueueTTLSeconds)
                    )
                }
                .doOnSuccess {
                    logger.info(
                        "Generated refund request event {} for transactionId {}",
                        event.eventCode,
                        event.transactionId
                    )
                }
                .then()
        }
    }

    /**
     * Sends a notification request event to the transaction notification queue for processing by
     * the dedicated service.
     *
     * @param event The notification request event with transaction details and manual trigger
     * @return Mono that completes when the message is queued successfully
     */
    @Suppress("kotlin:S6508")
    fun sendNotificationRequestedEvent(event: TransactionUserReceiptRequestedEvent): Mono<Void> {
        return tracingUtils.traceMono(this.javaClass.simpleName) { tracingInfo ->
            notificationQueueClient
                .flatMap { client ->
                    client.sendMessageWithResponse(
                        QueueEvent(event, tracingInfo),
                        Duration.ZERO,
                        Duration.ofSeconds(transientQueueTTLSeconds)
                    )
                }
                .doOnSuccess {
                    logger.info(
                        "Generated send notification event {} for transactionId {}",
                        event.eventCode,
                        event.transactionId
                    )
                }
                .then()
        }
    }
}
