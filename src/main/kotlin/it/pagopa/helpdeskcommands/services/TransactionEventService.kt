package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.BaseTransactionEvent
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
import it.pagopa.helpdeskcommands.mdcutilities.RequestTracingUtils
import it.pagopa.helpdeskcommands.repositories.ecommerce.TransactionsEventStoreRepository
import it.pagopa.helpdeskcommands.repositories.ecommerce.TransactionsViewRepository
import it.pagopa.helpdeskcommands.repositories.ecommercehistory.TransactionsEventStoreHistoryRepository
import it.pagopa.helpdeskcommands.repositories.ecommercehistory.TransactionsViewHistoryRepository
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
        TransactionsEventStoreRepository<TransactionUserReceiptData>,
    @Autowired
    private val transactionsEventStoreHistoryRepository:
        TransactionsEventStoreHistoryRepository<Any>,
    @Autowired
    private val transactionsRefundedEventStoreHistoryRepository:
        TransactionsEventStoreHistoryRepository<BaseTransactionRefundedData>,
    @Autowired private val transactionsViewHistoryRepository: TransactionsViewHistoryRepository,
    @Autowired
    private val userReceiptEventStoreHistoryRepository:
        TransactionsEventStoreHistoryRepository<TransactionUserReceiptData>
) : TransactionEventServiceInterface {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Suppress("kotlin:S6508") // Interface contract requires Mono<Void>
    override fun sendRefundRequestedEvent(event: TransactionRefundRequestedEvent): Mono<Void> {
        return sendMessageToQueue(refundQueueClient, QueueEvent(event, null))
    }

    @Suppress("kotlin:S6508") // Interface contract requires Mono<Void>
    override fun sendNotificationRequestedEvent(
        event: TransactionUserReceiptRequestedEvent
    ): Mono<Void> {
        return sendMessageToQueue(notificationQueueClient, QueueEvent(event, null))
    }

    private fun <T : BaseTransactionEvent<*>> sendMessageToQueue(
        queueClient: QueueAsyncClient,
        queueEvent: QueueEvent<T>
    ): Mono<Void> {
        return queueClient
            .sendMessageWithResponse(
                queueEvent,
                Duration.ZERO,
                Duration.ofSeconds(transientQueueTTLSeconds)
            )
            .doOnSuccess {
                RequestTracingUtils.withContextDetailsMdc(
                    mapOf(
                        RequestTracingUtils.TracingEntry.DEPENDENCY.key to
                            RequestTracingUtils.STORAGE_QUEUE_DEPENDENCY_KEY,
                        "queue_name" to queueClient.queueName
                    ),
                    mapOf(
                        RequestTracingUtils.TracingEntry.TRANSACTION_ID.key to
                            queueEvent.event.transactionId,
                        RequestTracingUtils.TracingEntry.QUEUE_EVENT_ID.key to
                            queueEvent.event.id.toString()
                    )
                ) {
                    logger.info("Message event sent successfully")
                }
            }
            .doOnError { e ->
                RequestTracingUtils.withErrorMdc(e) { logger.error("Failed to send message event") }
            }
            .then()
    }

    /**
     * Retrieves events from both runtime and history repositories
     *
     * @param transactionId The transaction ID to search for
     * @return Flux of transaction events from runtime and history repositories, sorted by creation
     *   date
     */
    private fun getEventsFromBothRepositories(transactionId: String): Flux<TransactionEvent<Any>> {
        val runtimeEvents =
            transactionsEventStoreRepository
                .findByTransactionIdOrderByCreationDateAsc(transactionId)
                .cast(TransactionEvent::class.java)
                .map { it as TransactionEvent<Any> }

        val historyEvents =
            transactionsEventStoreHistoryRepository
                .findByTransactionIdOrderByCreationDateAsc(transactionId)
                .cast(TransactionEvent::class.java)
                .map { it as TransactionEvent<Any> }

        return Flux.merge(runtimeEvents, historyEvents)
            .sort(compareBy { it.creationDate })
            .doOnNext {
                RequestTracingUtils.withContextDetailsMdc(
                    mapOf(
                        RequestTracingUtils.TracingEntry.DEPENDENCY.key to
                            RequestTracingUtils.MONGO_DEPENDENCY_KEY
                    ),
                    mapOf(
                        RequestTracingUtils.TracingEntry.TRANSACTION_ID.key to transactionId,
                    )
                ) {
                    logger.info("Retrieved events from repositories")
                }
            }
    }

    /**
     * Retrieves user receipt events from both runtime and history repositories
     *
     * @param transactionId The transaction ID to search for
     * @return Flux of user receipt events from both repositories
     */
    private fun getUserReceiptEventsFromBothRepositories(
        transactionId: String
    ): Flux<BaseTransactionEvent<TransactionUserReceiptData>> {
        val runtimeEvents =
            userReceiptEventStoreRepository.findByTransactionIdOrderByCreationDateAsc(transactionId)

        val historyEvents =
            userReceiptEventStoreHistoryRepository.findByTransactionIdOrderByCreationDateAsc(
                transactionId
            )

        return Flux.merge(runtimeEvents, historyEvents).sort(compareBy { it.creationDate })
    }

    /**
     * Retrieves a transaction by its ID from both runtime and history repositories and reduces the
     * events to build the transaction object
     */
    fun getTransaction(transactionId: String): Mono<BaseTransaction> {
        val events = getEventsFromBothRepositories(transactionId)

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
        return getTransaction(transactionId).flatMap { transaction ->
            if (transaction is BaseTransactionWithRefundRequested) {
                RequestTracingUtils.withContextDetailsMdc(
                    null,
                    mapOf(
                        RequestTracingUtils.TracingEntry.TRANSACTION_ID.key to
                            transaction.transactionId.value(),
                    )
                ) {
                    logger.warn("Transaction already has a refund requested")
                }
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
            .insert(refundRequestedEvent as TransactionEvent<BaseTransactionRefundedData>)
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
                RequestTracingUtils.withContextDetailsMdc(
                    mapOf(
                        RequestTracingUtils.TracingEntry.DEPENDENCY.key to
                            RequestTracingUtils.MONGO_DEPENDENCY_KEY
                    ),
                    mapOf(
                        RequestTracingUtils.TracingEntry.TRANSACTION_ID.key to
                            transaction.transactionId.value(),
                        RequestTracingUtils.TracingEntry.TRANSACTION_STATUS.key to
                            TransactionStatusDto.REFUND_REQUESTED
                    )
                ) {
                    logger.info("Updated transaction status")
                }
            }
            .thenReturn(transaction)
    }

    /**
     * Resends a notification for a transaction that is already in USER_RECEIPT_REQUESTED state
     * Searches in both runtime and history repositories
     *
     * @param transactionId ID of the transaction
     * @return Mono containing the existing TransactionUserReceiptRequestedEvent
     */
    fun resendUserReceiptNotification(
        transactionId: String
    ): Mono<TransactionUserReceiptRequestedEvent> {
        // Define the set of valid states for resending notifications
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
                // Get user receipt events from both repositories
                getUserReceiptEventsFromBothRepositories(transactionId).collectList().flatMap {
                    events ->
                    // Find the latest event that is of type
                    // TransactionUserReceiptRequestedEvent
                    val latestRequestedEvent =
                        events
                            .filterIsInstance<TransactionUserReceiptRequestedEvent>()
                            .maxByOrNull { it.creationDate }

                    if (latestRequestedEvent != null) {
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
                            .insert(newEvent)
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
                                RequestTracingUtils.withContextDetailsMdc(
                                    mapOf(
                                        RequestTracingUtils.TracingEntry.DEPENDENCY.key to
                                            RequestTracingUtils.MONGO_DEPENDENCY_KEY
                                    ),
                                    mapOf(
                                        RequestTracingUtils.TracingEntry.TRANSACTION_ID.key to
                                            transactionId,
                                        RequestTracingUtils.TracingEntry.TRANSACTION_STATUS.key to
                                            TransactionStatusDto.NOTIFICATION_REQUESTED,
                                        RequestTracingUtils.TracingEntry.QUEUE_EVENT_ID.key to
                                            newEvent.id.toString()
                                    )
                                ) {
                                    logger.info("Successfully created new user receipt event")
                                }
                            }
                            .doOnError { e ->
                                RequestTracingUtils.withErrorMdc(
                                    e,
                                    mapOf(
                                        RequestTracingUtils.TracingEntry.TRANSACTION_ID.key to
                                            transactionId,
                                    )
                                ) {
                                    logger.error("Error saving new user receipt event")
                                }
                            }
                            .thenReturn(newEvent)
                    } else {
                        RequestTracingUtils.withContextDetailsMdc(
                            null,
                            mapOf(
                                RequestTracingUtils.TracingEntry.TRANSACTION_ID.key to
                                    transactionId,
                            )
                        ) {
                            logger.error(
                                "No TransactionUserReceiptRequestedEvent found for transaction in runtime and history repositories",
                            )
                        }
                        Mono.error(
                            IllegalStateException(
                                "No TransactionUserReceiptRequestedEvent found for transaction ID: $transactionId"
                            )
                        )
                    }
                }
            } else {
                // Transaction is not in the correct state
                RequestTracingUtils.withContextDetailsMdc(
                    null,
                    mapOf(
                        RequestTracingUtils.TracingEntry.TRANSACTION_ID.key to transactionId,
                        RequestTracingUtils.TracingEntry.TRANSACTION_STATUS.key to
                            transaction.status,
                    )
                ) {
                    logger.error("Transaction is not in a valid state for resending notification")
                }
                Mono.error(
                    InvalidTransactionStatusException(
                        "Cannot resend user receipt notification for transaction in state: ${transaction.status}. Transaction must be one of ${admissibleStates.joinToString(",")}"
                    )
                )
            }
        }
    }
}
