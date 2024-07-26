package it.pagopa.ecommerce.helpdesk.controllers.v2

import it.pagopa.generated.helpdeskcommands.api.CommandsApi
import it.pagopa.generated.helpdeskcommands.model.*
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.math.BigDecimal

@RestController("CommandsController")
class CommandsController() : CommandsApi {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    override fun refundOperation(
        refundTransactionRequestDto: Mono<RefundTransactionRequestDto>?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<RefundTransactionResponseDto>> {
        val mockResponse = RefundTransactionResponseDto().refundOperationId(BigDecimal(1231231322));
        return Mono.just(ResponseEntity.ok(mockResponse))
    }
}
