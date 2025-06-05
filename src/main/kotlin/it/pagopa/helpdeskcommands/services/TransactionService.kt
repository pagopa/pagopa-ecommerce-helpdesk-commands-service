package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.documents.BaseTransactionEvent
import it.pagopa.ecommerce.commons.documents.v2.BaseTransactionRefundedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.authorization.TransactionGatewayAuthorizationData
import it.pagopa.ecommerce.commons.domain.v2.EmptyTransaction
import it.pagopa.ecommerce.commons.domain.v2.Transaction
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransaction
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransactionWithRefundRequested
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotRefundableException
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
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /** Retrieves a transaction by its ID and reduces the events to build the transaction object */
    fun getTransaction(transactionId: String): Mono<BaseTransaction> {
        logger.info("Retrieving transaction with ID: [{}]", transactionId)

        val events =
            transactionsEventStoreRepository
                .findByTransactionIdOrderByCreationDateAsc(transactionId)
                .map { it as BaseTransactionEvent<Any> }

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
            if (!isTransactionRefundable(transaction)) {
                Mono.error(
                    TransactionNotRefundableException(
                        "Transaction not in a refundable state: $transactionId"
                    )
                )
            } else if (transaction is BaseTransactionWithRefundRequested) {
                // Transaction already has a refund requested, return null
                logger.info(
                    "Transaction [{}] already has a refund requested, returning null",
                    transaction.transactionId.value()
                )
                Mono.empty() // This will result in null in the Mono
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
    private fun <T> reduceEvents(events: Flux<BaseTransactionEvent<T>>): Mono<BaseTransaction> {
        return events
            .reduce(EmptyTransaction(), Transaction::applyEvent)
            .cast(BaseTransaction::class.java)
    }

    /** Checks if a transaction is in a refundable state */
    private fun isTransactionRefundable(tx: BaseTransaction): Boolean {
        // TODO
        return true
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
    ): Mono<Pair<BaseTransactionWithRefundRequested?, TransactionRefundRequestedEvent?>> {
        return transactionsEventStoreRepository
            .save(refundRequestedEvent as TransactionEvent<BaseTransactionRefundedData>)
            .then(
                transactionsViewRepository
                    .findByTransactionId(transaction.transactionId.value())
                    .cast(it.pagopa.ecommerce.commons.documents.v2.Transaction::class.java)
                    .flatMap { tx ->
                        tx.status = TransactionStatusDto.REFUND_REQUESTED
                        transactionsViewRepository.save(tx)
                    }
            )
            .thenReturn(
                Pair<BaseTransactionWithRefundRequested?, TransactionRefundRequestedEvent?>(
                    (transaction as Transaction).applyEvent(refundRequestedEvent)
                        as BaseTransactionWithRefundRequested,
                    refundRequestedEvent
                )
            )
    }
}
