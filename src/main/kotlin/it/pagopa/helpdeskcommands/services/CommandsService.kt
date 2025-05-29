package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.ecommerce.commons.queues.TracingUtils
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundRequestDto as RedirectRefundRequestDto
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundResponseDto as RedirectRefundResponseDto
import it.pagopa.generated.helpdeskcommands.model.RefundOutcomeDto
import it.pagopa.generated.helpdeskcommands.model.RefundRedirectResponseDto
import it.pagopa.generated.npg.model.RefundResponseDto
import it.pagopa.helpdeskcommands.client.NodeForwarderClient
import it.pagopa.helpdeskcommands.client.NpgClient
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import it.pagopa.helpdeskcommands.exceptions.NpgClientException
import it.pagopa.helpdeskcommands.utils.NpgApiKeyConfiguration
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import it.pagopa.helpdeskcommands.utils.RedirectKeysConfiguration
import it.pagopa.helpdeskcommands.utils.TransactionId
import java.math.BigDecimal
import java.time.Duration
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class CommandsService(
    @Autowired private val npgClient: NpgClient,
    @Autowired private val npgApiKeyConfiguration: NpgApiKeyConfiguration,
    @Autowired private val redirectKeysConfiguration: RedirectKeysConfiguration,
    @Autowired
    private val nodeForwarderClient:
        NodeForwarderClient<RedirectRefundRequestDto, RedirectRefundResponseDto>,
    @Qualifier("transactionRefundQueueAsyncClient")
    private val refundQueueClient: Mono<QueueAsyncClient>,
    @Qualifier("transactionNotificationQueueAsyncClient")
    private val notificationQueueClient: Mono<QueueAsyncClient>,
    @Value("\${azurestorage.queues.ttlSeconds}") private val transientQueueTTLSeconds: Long,
    private val tracingUtils: TracingUtils
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun requestRedirectRefund(
        transactionId: TransactionId,
        touchpoint: String,
        pspTransactionId: String,
        paymentTypeCode: String,
        pspId: String
    ): Mono<RefundRedirectResponseDto> {
        return redirectKeysConfiguration
            .getRedirectUrlForPsp(touchpoint, pspId, paymentTypeCode)
            .fold(
                { ex -> Mono.error(ex) },
                { uri ->
                    logger.info("Processing Redirect transaction refund. ")
                    nodeForwarderClient
                        .proxyRequest(
                            request =
                                RedirectRefundRequestDto()
                                    .action("refund")
                                    .idPSPTransaction(pspTransactionId)
                                    .idTransaction(transactionId.value()),
                            proxyTo = uri,
                            requestId = transactionId.value(),
                            responseClass = RedirectRefundResponseDto::class.java
                        )
                        .map {
                            RefundRedirectResponseDto()
                                .idTransaction(it.body.idTransaction)
                                .outcome(RefundOutcomeDto.valueOf(it.body.outcome.name))
                        }
                        .doOnNext { response ->
                            logger.info(
                                "Redirect refund processed correctly for transaction with id: [{}]",
                                response.idTransaction
                            )
                        }
                        .doOnError(NodeForwarderClientException::class.java) { exception ->
                            logger.error(
                                "Error performing Redirect refund operation for transaction with id: [{}], psp id: [{}], pspTransactionId: [{}], paymentTypeCode: [{}]",
                                transactionId.value(),
                                pspId,
                                pspTransactionId,
                                paymentTypeCode,
                                exception
                            )
                        }
                }
            )
    }

    fun requestNpgRefund(
        operationId: String,
        transactionId: TransactionId,
        amount: BigDecimal,
        pspId: String,
        correlationId: String,
        paymentMethod: PaymentMethod
    ): Mono<RefundResponseDto> {
        return npgApiKeyConfiguration[paymentMethod, pspId].fold(
            { ex -> Mono.error(ex) },
            { apiKey ->
                logger.info(
                    "Performing NPG refund for transaction with id: [{}] and paymentMethod: [{}]. " +
                        "OperationId: [{}], amount: [{}], pspId: [{}], correlationId: [{}]",
                    transactionId.value(),
                    paymentMethod,
                    operationId,
                    amount,
                    pspId,
                    correlationId
                )
                npgClient
                    .refundPayment(
                        correlationId = UUID.fromString(correlationId),
                        operationId = operationId,
                        idempotenceKey = transactionId.uuid,
                        grandTotal = amount,
                        apikey = apiKey,
                        description =
                            "Refund request for transactionId ${transactionId.uuid} and operationId $operationId"
                    )
                    .doOnError(NpgClientException::class.java) { exception: NpgClientException ->
                        logger.error(
                            "Exception performing NPG refund for transactionId: [{}] and operationId: [{}]",
                            transactionId.value(),
                            operationId,
                            exception
                        )
                    }
            }
        )
    }

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
