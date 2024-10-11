package it.pagopa.helpdeskcommands.services

import it.pagopa.generated.helpdeskcommands.model.RefundRedirectRequestDto
import it.pagopa.generated.helpdeskcommands.model.RefundRedirectResponseDto
import it.pagopa.generated.npg.model.RefundResponseDto
import it.pagopa.helpdeskcommands.client.NodeForwarderClient
import it.pagopa.helpdeskcommands.client.NpgClient
import it.pagopa.helpdeskcommands.exceptions.BadGatewayException
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import it.pagopa.helpdeskcommands.exceptions.NpgClientException
import it.pagopa.helpdeskcommands.exceptions.RefundNotAllowedException
import it.pagopa.helpdeskcommands.utils.NpgApiKeyConfiguration
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import it.pagopa.helpdeskcommands.utils.RedirectKeysConfiguration
import it.pagopa.helpdeskcommands.utils.TransactionId
import java.math.BigDecimal
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Service
class CommandsService(
    @Autowired private val npgClient: NpgClient,
    @Autowired private val npgApiKeyConfiguration: NpgApiKeyConfiguration,
    @Autowired private val redirectKeysConfiguration: RedirectKeysConfiguration,
    @Autowired
    private val nodeForwarderClient:
        NodeForwarderClient<RefundRedirectRequestDto, RefundRedirectResponseDto>
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
                                RefundRedirectRequestDto()
                                    .action("refund")
                                    .idPSPTransaction(pspTransactionId)
                                    .idTransaction(transactionId.value()),
                            proxyTo = uri,
                            requestId = transactionId.value(),
                            responseClass = RefundRedirectResponseDto::class.java
                        )
                        .onErrorMap(NodeForwarderClientException::class.java) { exception ->
                            val errorCause = exception.cause
                            val httpErrorCode: Optional<HttpStatus> =
                                Optional.ofNullable(errorCause).map {
                                    (if (it is WebClientResponseException) {
                                        it.statusCode
                                    } else {
                                        null
                                    })
                                        as HttpStatus?
                                }
                            logger.error(
                                "Error performing Redirect refund operation for transaction with id: [${transactionId.value()}]. psp id: [$pspId], pspTransactionId: [$pspTransactionId], paymentTypeCode: [$paymentTypeCode], received HTTP response error code: [${
                            httpErrorCode.map { it.toString() }.orElse("N/A")
                        }]",
                                exception
                            )
                            httpErrorCode
                                .map {
                                    val errorCodeReason =
                                        "Error performing refund for Redirect transaction with id: [${transactionId.value()}] and payment type code: [$paymentTypeCode], HTTP error code: [$it]"
                                    if (it.is5xxServerError) {
                                        BadGatewayException(errorCodeReason)
                                    } else {
                                        RefundNotAllowedException(
                                            transactionId,
                                            errorCodeReason,
                                            exception
                                        )
                                    }
                                }
                                .orElse(
                                    BadGatewayException(
                                        "Error performing refund for Redirect transaction with id: [${transactionId.value()}] and payment type code: [$paymentTypeCode]"
                                    )
                                )
                        }
                        .map { it.body }
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
}
