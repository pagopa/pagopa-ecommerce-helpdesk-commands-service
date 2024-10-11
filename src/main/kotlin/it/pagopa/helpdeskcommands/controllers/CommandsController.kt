package it.pagopa.helpdeskcommands.controllers

import it.pagopa.generated.helpdeskcommands.api.CommandsApi
import it.pagopa.generated.helpdeskcommands.model.*
import it.pagopa.helpdeskcommands.services.CommandsService
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import it.pagopa.helpdeskcommands.utils.TransactionId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController("CommandsController")
class CommandsController(@Autowired private val commandsService: CommandsService) : CommandsApi {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun commandsRefundRedirectPost(
        refundRedirectRequestDto: Mono<RefundRedirectRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<RefundRedirectResponseDto>> {
        // mocked implementation
        return refundRedirectRequestDto.flatMap { requestDto ->
            logger.info(
                "Received refund redirect request for transactionId: [{}]",
                requestDto.idTransaction
            )
            val responseDto =
                RefundRedirectResponseDto().apply {
                    idTransaction = requestDto.idTransaction
                    outcome = RefundOutcomeDto.OK
                }
            Mono.just(ResponseEntity.ok(responseDto))
        }
    }

    override fun refundOperation(
        xUserId: String,
        xForwardedFor: String,
        refundTransactionRequestDto: Mono<RefundTransactionRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<RefundTransactionResponseDto>> {
        return refundTransactionRequestDto.flatMap {
            logger.info(
                "Refund transaction for userId: [{}], transactionId: [{}] from IP: [{}]",
                xUserId,
                it.transactionId,
                xForwardedFor
            )
            commandsService
                .requestNpgRefund(
                    operationId = it.operationId,
                    transactionId = TransactionId(it.transactionId),
                    correlationId = it.correlationId,
                    paymentMethod = PaymentMethod.fromServiceName(it.paymentMethodName),
                    pspId = it.pspId,
                    amount = it.amount.toBigDecimal()
                )
                .map {
                    ResponseEntity.ok(
                        RefundTransactionResponseDto().refundOperationId(it.operationId)
                    )
                }
        }
    }
}
