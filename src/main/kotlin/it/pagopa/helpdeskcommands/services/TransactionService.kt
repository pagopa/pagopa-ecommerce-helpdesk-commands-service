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
import it.pagopa.helpdeskcommands.utils.RefundRequestResponse
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
     * Requests a refund for a transaction
     *
     * @param transactionId ID of the transaction to refund
     * @return Mono containing refund request details or null if already requested
     */
    fun requestRefund(transactionId: String): Mono<RefundRequestResponse?> {
        logger.info("Requesting refund for transaction with ID: [{}]", transactionId)

        return getTransaction(transactionId).flatMap { transaction ->
            if (isTransactionRefundable(transaction)) {
                logger.info(
                    "Transaction [{}] is refundable, creating refund request event",
                    transactionId
                )

                // Create the refund request event
                appendRefundRequestedEventIfNeeded(
                        transaction,
                        transactionsRefundedEventStoreRepository,
                        transactionsViewRepository
                    )
                    .map { (tx, event) ->
                        // Only return a response if a new event was created
                        event?.let {
                            RefundRequestResponse(
                                transactionId = tx!!.transactionId.value(),
                                status = TransactionStatusDto.REFUND_REQUESTED,
                                refundRequestedAt = event.creationDate,
                            )
                        }
                    }
            } else {
                logger.warn("Transaction [{}] is not in a refundable state", transactionId)
                Mono.error(
                    TransactionNotRefundableException(
                        "Transaction not in a refundable state: $transactionId"
                    )
                )
            }
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

    private fun appendRefundRequestedEventIfNeeded(
        transaction: BaseTransaction,
        transactionsEventStoreRepository:
            TransactionsEventStoreRepository<BaseTransactionRefundedData>,
        transactionsViewRepository: TransactionsViewRepository,
        authorizationData: TransactionGatewayAuthorizationData? = null
    ): Mono<Pair<BaseTransactionWithRefundRequested?, TransactionRefundRequestedEvent?>> {
        if (transaction is BaseTransactionWithRefundRequested) {
            logger.warn(
                "Transaction ${transaction.transactionId.value()} already has a refund requested."
            )
            return Mono.just(Pair(transaction, null))
        }

        val refundRequestedEvent = createRefundRequestedEvent(transaction, authorizationData)

        return saveRefundRequestedEventAndUpdateTransactionView(
            transaction,
            refundRequestedEvent,
            transactionsEventStoreRepository,
            transactionsViewRepository
        )
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
