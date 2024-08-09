package it.pagopa.helpdeskcommands.services

import it.pagopa.generated.npg.model.RefundResponseDto
import it.pagopa.helpdeskcommands.client.NpgClient
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
    @Autowired private val npgApiKeyConfiguration: NpgApiKeyConfiguration
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

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
                    transactionId.uuid,
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
                            transactionId,
                            operationId,
                            exception
                        )
                    }
            }
        )
    }
}
