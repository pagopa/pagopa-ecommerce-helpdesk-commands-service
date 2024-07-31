package it.pagopa.helpdeskcommands.controllers

import it.pagopa.generated.helpdeskcommands.api.CommandsApi
import it.pagopa.generated.helpdeskcommands.model.*
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController("CommandsController")
class CommandsController() : CommandsApi {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun refundOperation(
        xUserId: String,
        refundTransactionRequestDto: Mono<RefundTransactionRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<RefundTransactionResponseDto>> {
        val mockResponse = RefundTransactionResponseDto().refundOperationId("1231231322")
        return refundTransactionRequestDto.flatMap {
            logger.info(
                "Refund transaction for userId: {}, transactionId: {}",
                xUserId,
                it.transactionId
            )
            Mono.just(ResponseEntity.ok(mockResponse))
        }
    }
}
