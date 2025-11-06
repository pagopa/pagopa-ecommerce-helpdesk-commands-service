package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.v2.BaseTransactionRefundedData
import it.pagopa.ecommerce.commons.documents.v2.Transaction
import it.pagopa.ecommerce.commons.documents.v2.TransactionEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.authorization.TransactionGatewayAuthorizationData
import it.pagopa.ecommerce.commons.domain.v2.EmptyTransaction
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransaction
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransactionWithRefundRequested
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.helpdeskcommands.exceptions.InvalidTransactionStatusException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import it.pagopa.helpdeskcommands.repositories.ecommerce.TransactionsEventStoreRepository
import it.pagopa.helpdeskcommands.repositories.ecommerce.TransactionsViewRepository
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class TransactionEventService(
    @Qualifier("transactionRefundQueueAsyncClient") private val refundQueueClient: QueueAsyncClient,
    @Qualifier("transactionNotificationQueueAsyncClient")
    private val notificationQueueClient: QueueAsyncClient,
    @Value("\${azurestorage.queues.ttlSeconds}") private val transientQueueTTLSeconds: Long,
    @Autowired private val transactionsEventStoreRepository: TransactionsEventStoreRepository<Any>,
    @Autowired
    private val transactionsRefundedEventStoreRepository:
        TransactionsEventStoreRepository<BaseTransactionRefundedData>,
    @Autowired private val transactionsViewRepository: TransactionsViewRepository,
    @Autowired
    private val userReceiptEventStoreRepository:
        TransactionsEventStoreRepository<TransactionUserReceiptData>
) : TransactionEventServiceInterface {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Suppress("kotlin:S6508") // Interface contract requires Mono<Void>
    override fun sendRefundRequestedEvent(event: TransactionRefundRequestedEvent): Mono<Void> {
        logger.info("Sending refund message event for transaction: {}", event.transactionId)

        val queueEvent = QueueEvent(event, null)
        return refundQueueClient
            .sendMessageWithResponse(
                queueEvent,
                Duration.ZERO,
                Duration.ofSeconds(transientQueueTTLSeconds)
            )
            .doOnSuccess { logger.info("Refund message event sent successfully") }
            .doOnError { e ->
                logger.error("Failed to send refund message event: {}", e.message, e)
            }
            .then()
    }

    @Suppress("kotlin:S6508") // Interface contract requires Mono<Void>
    override fun sendNotificationRequestedEvent(
        event: TransactionUserReceiptRequestedEvent
    ): Mono<Void> {
        logger.info("Sending notification message event for transaction: {}", event.transactionId)

        val queueEvent = QueueEvent(event, null)
        return notificationQueueClient
            .sendMessageWithResponse(
                queueEvent,
                Duration.ZERO,
                Duration.ofSeconds(transientQueueTTLSeconds)
            )
            .doOnSuccess { logger.info("Notification message event sent successfully") }
            .doOnError { e ->
                logger.error("Failed to send notification message event: {}", e.message, e)
            }
            .then()
    }

    /** Retrieves a transaction by its ID and reduces the events to build the transaction object */
    fun getTransaction(transactionId: String): Mono<BaseTransaction> {
        logger.info("Retrieving transaction with ID: [{}]", transactionId)

        val events =
            transactionsEventStoreRepository
                .findByTransactionIdOrderByCreationDateAsc(transactionId)
                .map { it as TransactionEvent<Any> }

        return reduceEvents(events)
            .switchIfEmpty(
                Mono.error(TransactionNotFoundException("Transaction not found: $transactionId"))
            )
    }

    /**
     * Creates a refund request for a transaction if one doesn't already exist
     *
     * @param transactionId ID of the transaction to refund
     * @return Mono containing the TransactionRefundRequestedEvent or null if already requested
     */
    fun createRefundRequestEvent(transactionId: String): Mono<TransactionRefundRequestedEvent> {
        logger.info("Creating refund request event for transaction with ID: [{}]", transactionId)

        return getTransaction(transactionId).flatMap { transaction ->
            if (transaction is BaseTransactionWithRefundRequested) {
                // Transaction already has a refund requested
                logger.warn(
                    "Transaction [{}] already has a refund requested",
                    transaction.transactionId.value()
                )
            }
            createAndPersistRefundRequestEvent(transaction)
        }
    }

    /**
     * Creates and persists a refund request event for a transaction
     *
     * @param transaction The transaction to create a refund request for
     * @return Mono containing the created TransactionRefundRequestedEvent
     */
    private fun createAndPersistRefundRequestEvent(
        transaction: BaseTransaction
    ): Mono<TransactionRefundRequestedEvent> {
        // Create new refund request event
        val refundRequestedEvent = createRefundRequestedEvent(transaction, null)

        // Save the event and update view
        return saveRefundRequestedEventAndUpdateTransactionView(
                transaction,
                refundRequestedEvent,
                transactionsRefundedEventStoreRepository,
                transactionsViewRepository
            )
            .map { refundRequestedEvent }
    }

    /** Reduces a flux of transaction events into a transaction object */
    fun <T> reduceEvents(events: Flux<TransactionEvent<T>>): Mono<BaseTransaction> =
        reduceEvents(events, EmptyTransaction())

    fun <T> reduceEvents(
        events: Flux<TransactionEvent<T>>,
        emptyTransaction: EmptyTransaction
    ): Mono<BaseTransaction> =
        events
            .reduce(emptyTransaction, it.pagopa.ecommerce.commons.domain.v2.Transaction::applyEvent)
            .cast(BaseTransaction::class.java)
            .onErrorResume { e ->
                if (e is ClassCastException && e.message?.contains("EmptyTransaction") == true) {
                    Mono.error(TransactionNotFoundException("Transaction not found"))
                } else {
                    Mono.error(e)
                }
            }

    /**
     * Create a refund event from the given transaction and authorization data explicitly using the
     * manual trigger as refundTrigger
     */
    private fun createRefundRequestedEvent(
        transaction: BaseTransaction,
        authorizationData: TransactionGatewayAuthorizationData?
    ): TransactionRefundRequestedEvent {
        return TransactionRefundRequestedEvent(
            transaction.transactionId.value(),
            TransactionRefundRequestedData(
                authorizationData,
                transaction.status,
                TransactionRefundRequestedData.RefundTrigger.MANUAL
            )
        )
    }

    private fun saveRefundRequestedEventAndUpdateTransactionView(
        transaction: BaseTransaction,
        refundRequestedEvent: TransactionRefundRequestedEvent,
        transactionsEventStoreRepository:
            TransactionsEventStoreRepository<BaseTransactionRefundedData>,
        transactionsViewRepository: TransactionsViewRepository
    ): Mono<BaseTransaction?> {
        return transactionsEventStoreRepository
            .save(refundRequestedEvent as TransactionEvent<BaseTransactionRefundedData>)
            .then(
                transactionsViewRepository
                    .findByTransactionId(transaction.transactionId.value())
                    .cast(Transaction::class.java)
                    .flatMap { tx ->
                        tx.status = TransactionStatusDto.REFUND_REQUESTED
                        transactionsViewRepository.save(tx)
                    }
            )
            .doOnSuccess {
                logger.info(
                    "Updated event for transaction with id ${transaction.transactionId.value()} to status refund"
                )
            }
            .thenReturn(transaction)
    }

    /**
     * Resends a notification for a transaction that is already in USER_RECEIPT_REQUESTED state
     *
     * @param transactionId ID of the transaction
     * @return Mono containing the existing TransactionUserReceiptRequestedEvent
     */
    fun resendUserReceiptNotification(
        transactionId: String
    ): Mono<TransactionUserReceiptRequestedEvent> {
        logger.info(
            "Attempting to resend user receipt notification for transaction ID: [{}]",
            transactionId
        )

        // Define the set of valid states for resending notifications
        // Right now we are just handling states that have the required information
        // to build the event.
        // A refinement could review the logic.
        val admissibleStates =
            listOf(
                TransactionStatusDto.NOTIFICATION_REQUESTED,
                TransactionStatusDto.EXPIRED,
                TransactionStatusDto.NOTIFICATION_ERROR,
                TransactionStatusDto.NOTIFIED_OK,
                TransactionStatusDto.NOTIFIED_KO,
            )

        return getTransaction(transactionId).flatMap { transaction ->
            if (transaction.status in admissibleStates) {
                // NOTE: Spring Boot 3.x has built-in support for AOT processing, which
                // pre-generates proxies at build time.
                // Until then, we use this "native-friendly" way to the Desc
                userReceiptEventStoreRepository
                    .findByTransactionIdOrderByCreationDateAsc(transactionId)
                    .collectList()
                    .flatMap { events ->
                        // Find the latest event that is of type
                        // TransactionUserReceiptRequestedEvent
                        val latestRequestedEvent =
                            events
                                .filterIsInstance<TransactionUserReceiptRequestedEvent>()
                                .maxByOrNull { it.creationDate }

                        if (latestRequestedEvent != null) {
                            logger.info(
                                "Found existing user receipt event for transaction ID: [{}], creating new event",
                                transactionId
                            )

                            val newEventData =
                                TransactionUserReceiptData(
                                    latestRequestedEvent.data.responseOutcome,
                                    latestRequestedEvent.data.language,
                                    latestRequestedEvent.data.paymentDate,
                                    TransactionUserReceiptData.NotificationTrigger.MANUAL
                                )

                            // Create a NEW event with the same data but a new ID and current
                            // timestamp
                            val newEvent =
                                TransactionUserReceiptRequestedEvent(transactionId, newEventData)

                            // Save the new event
                            userReceiptEventStoreRepository
                                .save(newEvent)
                                .then(
                                    transactionsViewRepository
                                        .findByTransactionId(transaction.transactionId.value())
                                        .cast(Transaction::class.java)
                                        .flatMap { tx ->
                                            tx.status = TransactionStatusDto.NOTIFICATION_REQUESTED
                                            transactionsViewRepository.save(tx)
                                        }
                                )
                                .doOnSuccess {
                                    logger.info(
                                        "Successfully created new user receipt event with ID [{}] for transaction ID: [{}]",
                                        newEvent.id,
                                        transactionId
                                    )
                                }
                                .doOnError { e ->
                                    logger.error(
                                        "Error saving new user receipt event for transaction ID: [{}]: {}",
                                        transactionId,
                                        e.message,
                                        e
                                    )
                                }
                                .thenReturn(newEvent)
                        } else {
                            logger.error(
                                "No TransactionUserReceiptRequestedEvent found for transaction ID: [{}]",
                                transactionId
                            )
                            Mono.error(
                                IllegalStateException(
                                    "No TransactionUserReceiptRequestedEvent found for transaction ID: $transactionId"
                                )
                            )
                        }
                    }
            } else {
                // Transaction is not in the correct state
                logger.error(
                    "Transaction [{}] is not in a valid state for resending notification, current state: {}",
                    transactionId,
                    transaction.status
                )
                Mono.error(
                    InvalidTransactionStatusException(
                        "Cannot resend user receipt notification for transaction in state: ${transaction.status}. Transaction must be one of ${admissibleStates.joinToString(",")}"
                    )
                )
            }
        }
    }
}
