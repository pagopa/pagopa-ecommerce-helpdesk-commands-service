package it.pagopa.helpdeskcommands.controller

import it.pagopa.generated.helpdeskcommands.model.RefundOutcomeDto
import it.pagopa.generated.helpdeskcommands.model.RefundRedirectResponseDto
import it.pagopa.generated.helpdeskcommands.model.RefundTransactionResponseDto
import it.pagopa.generated.npg.model.RefundResponseDto
import it.pagopa.helpdeskcommands.HelpDeskCommandsTestUtils
import it.pagopa.helpdeskcommands.controllers.CommandsController
import it.pagopa.helpdeskcommands.exceptions.InvalidTransactionStatusException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import it.pagopa.helpdeskcommands.services.CommandsService
import it.pagopa.helpdeskcommands.services.TransactionService
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.aot.DisabledInAotMode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@WebFluxTest(CommandsController::class)
@DisabledInAotMode
@TestPropertySource(locations = ["classpath:application.test.properties"])
class CommandsControllerTest {

    private lateinit var commandsController: CommandsController

    @MockBean private lateinit var commandsService: CommandsService
    @MockBean private lateinit var transactionService: TransactionService

    @Autowired private lateinit var webClient: WebTestClient

    @BeforeEach
    fun beforeTest() {
        commandsController = CommandsController(commandsService, transactionService)
    }

    companion object {
        private const val VALID_TRANSACTION_ID = "3fa85f6457174562b3fc2c963f66afa6"
        private const val SOURCE_IP = "127.0.0.1"
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

    @Test
    fun commandsRefundRedirectPost() {
        val userId = UUID.randomUUID().toString()
        val sourceIP = "127.0.0.1"
        val refundRedirectResponseDto =
            RefundRedirectResponseDto()
                .idTransaction(HelpDeskCommandsTestUtils.TRANSACTION_ID)
                .outcome(RefundOutcomeDto.OK)
        given { commandsService.requestRedirectRefund(any(), any(), any(), any(), any()) }
            .willReturn(refundRedirectResponseDto.toMono())
        webClient
            .post()
            .uri("/commands/refund/redirect")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-user-id", userId)
            .header("X-Forwarded-For", sourceIP)
            .bodyValue(HelpDeskCommandsTestUtils.CREATE_REFUND_REDIRECT_REQUEST)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<RefundRedirectResponseDto>()
            .consumeWith { assertEquals(refundRedirectResponseDto, it.responseBody) }
    }

    @Test
    fun `requestTransactionRefund returns 500 when not implemented`() {
        val userId = UUID.randomUUID().toString()

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/refund")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .is5xxServerError
    }

    @Test
    fun `resendTransactionEmail returns 500 when not implemented`() {
        val userId = UUID.randomUUID().toString()

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/resend-email")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .is5xxServerError
    }
    @Test
    fun `requestTransactionRefund returns 202 when refund is successfully requested`() {
        val userId = UUID.randomUUID().toString()
        val refundEvent = HelpDeskCommandsTestUtils.createMockRefundEvent()

        given { transactionService.createRefundRequestEvent(VALID_TRANSACTION_ID) }
            .willReturn(Mono.just(refundEvent))

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/refund")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isAccepted
    }

    @Test
    fun `requestTransactionRefund returns 202 when refund was already requested`() {
        val userId = UUID.randomUUID().toString()
        val refundEvent = HelpDeskCommandsTestUtils.createMockRefundEvent()

        // Since the service now returns an event even when refund was already requested,
        // we should mock it to return an event
        given { transactionService.createRefundRequestEvent(VALID_TRANSACTION_ID) }
            .willReturn(Mono.just(refundEvent))

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/refund")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isAccepted
    }

    @Test
    fun `requestTransactionRefund returns 404 when transaction is not found`() {
        val userId = UUID.randomUUID().toString()

        given { transactionService.createRefundRequestEvent(VALID_TRANSACTION_ID) }
            .willReturn(Mono.error(TransactionNotFoundException("Transaction not found")))

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/refund")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `requestTransactionRefund returns 422 when transaction is not in a refundable state`() {
        val userId = UUID.randomUUID().toString()

        // Mock the service to throw the exception
        given { transactionService.createRefundRequestEvent(VALID_TRANSACTION_ID) }
            .willReturn(
                Mono.error(InvalidTransactionStatusException("Transaction not in refundable state"))
            )

        // When testing with WebTestClient, we're testing the full request/response cycle
        // including exception handlers, so we should use WebTestClient instead of testing
        // the controller method directly
        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/refund")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `requestTransactionRefund returns 400 when transaction ID is invalid`() {
        val userId = UUID.randomUUID().toString()

        webClient
            .post()
            .uri("/commands/transactions/ /refund") // Invalid transaction ID (space)
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `requestTransactionRefund returns 500 on unexpected error`() {
        val userId = UUID.randomUUID().toString()

        given { transactionService.createRefundRequestEvent(VALID_TRANSACTION_ID) }
            .willReturn(Mono.error(RuntimeException("Unexpected error")))

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/refund")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `resendTransactionEmail returns 202 when email is successfully resent`() {
        val userId = UUID.randomUUID().toString()
        val emailEvent = HelpDeskCommandsTestUtils.createMockEmailEvent()

        given { transactionService.resendUserReceiptNotification(VALID_TRANSACTION_ID) }
            .willReturn(Mono.just(emailEvent))

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/resend-email")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isAccepted
    }

    @Test
    fun `resendTransactionEmail returns 404 when transaction is not found`() {
        val userId = UUID.randomUUID().toString()

        given { transactionService.resendUserReceiptNotification(VALID_TRANSACTION_ID) }
            .willReturn(Mono.error(TransactionNotFoundException("Transaction not found")))

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/resend-email")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `resendTransactionEmail returns 422 when transaction status is invalid`() {
        val userId = UUID.randomUUID().toString()

        given { transactionService.resendUserReceiptNotification(VALID_TRANSACTION_ID) }
            .willReturn(Mono.error(InvalidTransactionStatusException("Invalid transaction status")))

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/resend-email")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `resendTransactionEmail returns 400 when transaction ID is invalid`() {
        val userId = UUID.randomUUID().toString()

        webClient
            .post()
            .uri("/commands/transactions/ /resend-email") // Invalid transaction ID (space)
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `resendTransactionEmail returns 500 on unexpected error`() {
        val userId = UUID.randomUUID().toString()

        given { transactionService.resendUserReceiptNotification(VALID_TRANSACTION_ID) }
            .willReturn(Mono.error(RuntimeException("Unexpected error")))

        webClient
            .post()
            .uri("/commands/transactions/$VALID_TRANSACTION_ID/resend-email")
            .header("x-user-id", userId)
            .header("X-Forwarded-For", SOURCE_IP)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
