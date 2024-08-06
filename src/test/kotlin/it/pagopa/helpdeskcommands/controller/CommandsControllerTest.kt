package it.pagopa.helpdeskcommands.controller

import it.pagopa.generated.helpdeskcommands.model.RefundTransactionResponseDto
import it.pagopa.generated.npg.model.RefundResponseDto
import it.pagopa.helpdeskcommands.HelpDeskCommandsTestUtils
import it.pagopa.helpdeskcommands.controllers.CommandsController
import it.pagopa.helpdeskcommands.services.CommandsService
import java.net.InetSocketAddress
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.aot.DisabledInAotMode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebExchangeDecorator
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

class SetRemoteAddressWebFilter(private val host: String) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return chain.filter(decorate(exchange))
    }

    private fun decorate(exchange: ServerWebExchange): ServerWebExchange {
        val decorated: ServerHttpRequest =
            object : ServerHttpRequestDecorator(exchange.request) {
                override fun getRemoteAddress(): InetSocketAddress? {
                    return InetSocketAddress(host, 80)
                }
            }

        return object : ServerWebExchangeDecorator(exchange) {
            override fun getRequest(): ServerHttpRequest {
                return decorated
            }
        }
    }
}

@WebFluxTest(CommandsController::class)
@DisabledInAotMode
@TestPropertySource(locations = ["classpath:application.test.properties"])
class CommandsControllerTest {

    private lateinit var commandsController: CommandsController

    @MockBean private lateinit var commandsService: CommandsService

    @Autowired private lateinit var webClient: WebTestClient

    @BeforeEach
    fun beforeTest() {
        commandsController = CommandsController(commandsService)
    }

    @Test
    fun testRefundPaymentMethod() {

        val operationId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        val sourceIP = "127.0.0.1"
        val refundResponseDto = RefundResponseDto().operationId(operationId)
        val refundTransactionResponseDto =
            RefundTransactionResponseDto().refundOperationId(operationId)
        given { commandsService.requestNpgRefund(any(), any(), any(), any(), any(), any()) }
            .willReturn(refundResponseDto.toMono())
        webClient
            .post()
            .uri("/commands/refund")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", userId)
            .header("X-Forwarded-For", sourceIP)
            .bodyValue(HelpDeskCommandsTestUtils.CREATE_REFUND_TRANSACTION_REQUEST)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<RefundTransactionResponseDto>()
            .consumeWith { assertEquals(refundTransactionResponseDto, it.responseBody) }
    }
}
