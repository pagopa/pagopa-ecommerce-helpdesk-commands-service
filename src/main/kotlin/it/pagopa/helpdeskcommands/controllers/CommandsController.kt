package it.pagopa.helpdeskcommands.controllers

import it.pagopa.generated.helpdeskcommands.api.CommandsApi
import it.pagopa.generated.helpdeskcommands.model.RefundTransactionRequestDto
import it.pagopa.generated.helpdeskcommands.model.RefundTransactionResponseDto
import it.pagopa.helpdeskcommands.services.CommandsService
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController("CommandsController")
class CommandsController(@Autowired private val commandsService: CommandsService) : CommandsApi {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun refundOperation(
        refundTransactionRequestDto: Mono<RefundTransactionRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<RefundTransactionResponseDto>> {
        return refundTransactionRequestDto.flatMap { request ->
            commandsService
                .requestNpgRefund(
                    operationId = request.operationId,
                    transactionId = request.transactionId,
                    correlationId = request.correlationId,
                    paymentMethod = PaymentMethod.fromServiceName(request.paymentMethodName),
                    pspId = request.pspId,
                    amount = request.amount.toBigDecimal()
                )
                .map {
                    ResponseEntity.ok(
                        RefundTransactionResponseDto().refundOperationId(it.operationId)
                    )
                }
        }
    }
}
