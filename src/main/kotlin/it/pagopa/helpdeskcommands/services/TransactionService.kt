package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.documents.v2.*
import it.pagopa.ecommerce.commons.documents.v2.authorization.TransactionGatewayAuthorizationData
import it.pagopa.ecommerce.commons.domain.v2.EmptyTransaction
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransaction
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransactionWithRefundRequested
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.helpdeskcommands.exceptions.InvalidTransactionStatusException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import it.pagopa.helpdeskcommands.repositories.TransactionsEventStoreRepository
import it.pagopa.helpdeskcommands.repositories.TransactionsViewRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class TransactionService(
    @Autowired private val transactionsEventStoreRepository: TransactionsEventStoreRepository<Any>,
    @Autowired
    private val transactionsRefundedEventStoreRepository:
        TransactionsEventStoreRepository<BaseTransactionRefundedData>,
    @Autowired private val transactionsViewRepository: TransactionsViewRepository,
    @Autowired
    private val userReceiptEventStoreRepository:
        TransactionsEventStoreRepository<TransactionUserReceiptData>
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

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
    fun createRefundRequestEvent(transactionId: String): Mono<TransactionRefundRequestedEvent?> {
        logger.info("Creating refund request event for transaction with ID: [{}]", transactionId)

        return getTransaction(transactionId).flatMap { transaction ->
            if (transaction is BaseTransactionWithRefundRequested) {
                // Transaction already has a refund requested, return null
                logger.info(
                    "Transaction [{}] already has a refund requested, returning null",
                    transaction.transactionId.value()
                )
                Mono.empty()
            } else {
                // Create and persist the refund request event
                createAndPersistRefundRequestEvent(transaction)
            }
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
            .map {
                // Return the event itself
                refundRequestedEvent
            }
    }

    /** Reduces a flux of transaction events into a transaction object */
    fun reduceEvents(
        transactionId: Mono<String>,
        transactionsEventStoreRepository: TransactionsEventStoreRepository<Any>,
        emptyTransaction: EmptyTransaction
    ): Mono<BaseTransaction> =
        reduceEvents(
            transactionId.flatMapMany {
                transactionsEventStoreRepository.findByTransactionIdOrderByCreationDateAsc(it).map {
                    it as TransactionEvent<Any>
                }
            },
            emptyTransaction
        )

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

        return getTransaction(transactionId).flatMap { transaction ->
            if (transaction.status == TransactionStatusDto.NOTIFICATION_REQUESTED) {
                // NOTE: Spring Boot 3.x has built-in support for AOT processing, which
                // pre-generates proxies at build time.
                // Until then, we use this "native-friendly" way to the Desc
                userReceiptEventStoreRepository
                    .findByTransactionIdOrderByCreationDateAsc(transactionId)
                    .collectList()
                    .map { events -> events.maxByOrNull { it.creationDate } }
                    .cast(TransactionUserReceiptRequestedEvent::class.java)
                    .flatMap { existingEvent ->
                        logger.info(
                            "Found existing user receipt event for transaction ID: [{}], creating new event",
                            transactionId
                        )

                        val newEventData =
                            TransactionUserReceiptData(
                                existingEvent.data.responseOutcome,
                                existingEvent.data.language,
                                existingEvent.data.paymentDate,
                                TransactionUserReceiptData.NotificationTrigger.MANUAL
                            )

                        // Create a NEW event with the same data but a new ID and current timestamp
                        val newEvent =
                            TransactionUserReceiptRequestedEvent(transactionId, newEventData)

                        // Save the new event
                        userReceiptEventStoreRepository
                            .save(newEvent)
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
                    }
            } else {
                // Transaction is not in the correct state
                logger.error(
                    "Transaction [{}] is not in NOTIFICATION_REQUESTED state, current state: {}",
                    transactionId,
                    transaction.status
                )
                Mono.error(
                    InvalidTransactionStatusException(
                        "Cannot resend user receipt notification for transaction in state: ${transaction.status}"
                    )
                )
            }
        }
    }
}
