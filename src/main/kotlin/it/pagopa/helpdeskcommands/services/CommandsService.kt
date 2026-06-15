package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.utils.RedirectUrlMappingConf
import it.pagopa.ecommerce.commons.utils.bean.redirect.configuration.RedirectUrlMappingCriteria
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
import it.pagopa.helpdeskcommands.utils.TransactionId
import java.math.BigDecimal
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class CommandsService(
    @Autowired private val npgClient: NpgClient,
    @Autowired private val npgApiKeyConfiguration: NpgApiKeyConfiguration,
    @Autowired private val redirectUrlMappingConf: RedirectUrlMappingConf,
    @Autowired
    private val nodeForwarderClient:
        NodeForwarderClient<RedirectRefundRequestDto, RedirectRefundResponseDto>
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun requestRedirectRefund(
        transactionId: TransactionId,
        touchpoint: String,
        pspTransactionId: String,
        paymentTypeCode: String,
        pspId: String,
        pspChannelCode: String?
    ): Mono<RefundRedirectResponseDto> {
        val matchingCriteria =
            linkedMapOf(
                    RedirectUrlMappingCriteria.TOUCHPOINT to touchpoint,
                    RedirectUrlMappingCriteria.PSP_ID to pspId,
                    RedirectUrlMappingCriteria.PAYMENT_TYPE_CODE to paymentTypeCode
                )
                .apply {
                    pspChannelCode
                        ?.takeIf { it.isNotBlank() }
                        ?.let { put(RedirectUrlMappingCriteria.PSP_CHANNEL_ID, it) }
                }

        return redirectUrlMappingConf
            .getRedirectUrlForCriteria(matchingCriteria)
            .fold(
                { ex -> Mono.error(ex) },
                { entry ->
                    logger.info(
                        "Processing Redirect transaction refund for pspChannelCode: [{}]",
                        pspChannelCode
                    )
                    nodeForwarderClient
                        .proxyRequest(
                            request =
                                RedirectRefundRequestDto()
                                    .action("refund")
                                    .idPSPTransaction(pspTransactionId)
                                    .idTransaction(transactionId.value()),
                            proxyTo = entry.url,
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
                    "Performing NPG refund for transaction with id: [{}] and paymentMethod: [{}]. OperationId: [{}], amount: [{}], pspId: [{}], correlationId: [{}]",
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
