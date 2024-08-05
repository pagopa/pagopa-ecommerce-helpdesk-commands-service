package it.pagopa.helpdeskcommands.controller

import it.pagopa.generated.helpdeskcommands.model.RefundTransactionResponseDto
import it.pagopa.generated.npg.model.RefundResponseDto
import it.pagopa.helpdeskcommands.HelpDeskCommandsTestUtils
import it.pagopa.helpdeskcommands.controllers.CommandsController
import it.pagopa.helpdeskcommands.services.CommandsService
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.aot.DisabledInAotMode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import reactor.kotlin.core.publisher.toMono

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
            .bodyValue(HelpDeskCommandsTestUtils.CREATE_REFUND_TRANSACTION_REQUEST)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<RefundTransactionResponseDto>()
            .consumeWith { assertEquals(refundTransactionResponseDto, it.responseBody) }
    }
}
