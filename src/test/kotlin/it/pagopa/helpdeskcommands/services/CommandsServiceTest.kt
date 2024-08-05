package it.pagopa.helpdeskcommands.services

import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.helpdeskcommands.client.NpgClient
import it.pagopa.helpdeskcommands.config.WebClientConfig
import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyConfigurationException
import it.pagopa.helpdeskcommands.exceptions.NpgClientException
import it.pagopa.helpdeskcommands.utils.NpgApiKeyConfiguration
import it.pagopa.helpdeskcommands.utils.NpgPspApiKeysConfig
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import java.math.BigDecimal
import java.util.*
import java.util.stream.Stream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@SpringBootTest
@TestPropertySource(locations = ["classpath:application.test.properties"])
class CommandsServiceTest {

    final val PSP_ID = "pspId1"
    final val PSP_KEY = "pspId1-paypal-api-key"

    private val npgWebClient: PaymentServicesApi =
        WebClientConfig()
            .npgWebClient(
                baseUrl = "http://localhost:8080",
                readTimeout = 10000,
                connectionTimeout = 10000,
                tcpKeepAliveEnabled = true,
                tcpKeepAliveIdle = 300,
                tcpKeepAliveIntvl = 60,
                tcpKeepAliveCnt = 8
            )
    private val npgClient: NpgClient = spy(NpgClient(npgWebClient, ObjectMapper()))

    private val npgApiKeyConfiguration =
        NpgApiKeyConfiguration.Builder()
            .withMethodPspMapping(
                PaymentMethod.CARDS,
                NpgPspApiKeysConfig(mapOf(PSP_ID to PSP_KEY))
            )
            .build()

    private val commandsService: CommandsService =
        CommandsService(npgClient = npgClient, npgApiKeyConfiguration = npgApiKeyConfiguration)

    companion object {
        lateinit var mockWebServer: MockWebServer

        @JvmStatic
        @BeforeAll
        fun setup() {
            mockWebServer = MockWebServer()
            mockWebServer.start(8080)
            println("Mock web server started on ${mockWebServer.hostName}:${mockWebServer.port}")
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            mockWebServer.shutdown()
            println("Mock web stopped")
        }

        @JvmStatic
        private fun npgErrorsExpectedResponses(): Stream<Arguments> =
            Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST, NpgClientException::class.java),
                Arguments.of(HttpStatus.UNAUTHORIZED, NpgClientException::class.java),
                Arguments.of(HttpStatus.NOT_FOUND, NpgClientException::class.java),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, NpgClientException::class.java),
                Arguments.of(HttpStatus.GATEWAY_TIMEOUT, NpgClientException::class.java),
            )
    }

    @Test
    fun requestRefund_200_npg() {
        val operationId = "operationID"
        val transactionId = UUID.randomUUID().toString()
        val correlationId = UUID.randomUUID().toString()
        val amount = BigDecimal.valueOf(1000)
        // Precondition
        mockWebServer.enqueue(
            MockResponse()
                .setBody(
                    """
              {
                  "operationId": "%s"
              }
          """
                        .format(operationId)
                )
                .setHeader("Content-type", "application/json")
                .setResponseCode(200)
        )
        // Test
        StepVerifier.create(
                commandsService.requestNpgRefund(
                    operationId = operationId,
                    transactionId = transactionId,
                    amount = amount,
                    pspId = PSP_ID,
                    correlationId = correlationId,
                    paymentMethod = PaymentMethod.CARDS
                )
            )
            .assertNext { assertEquals(operationId, it.operationId) }
            .verifyComplete()
        verify(npgClient, times(1))
            .refundPayment(
                eq(PSP_KEY),
                eq(UUID.fromString(correlationId)),
                eq(operationId),
                eq(transactionId),
                eq(amount),
                any()
            )
    }

    @ParameterizedTest
    @MethodSource("npgErrorsExpectedResponses")
    fun `should handle npg error response`(
        errorHttpStatusCode: HttpStatus,
        expectedException: Class<out Throwable>
    ) {
        val operationId = "operationID"
        val transactionId = UUID.randomUUID().toString()
        val correlationId = UUID.randomUUID().toString()
        val amount = BigDecimal.valueOf(1000)
        // Precondition
        mockWebServer.enqueue(
            MockResponse()
                .setBody(
                    """
              {
                  "errors": []
              }
            """
                )
                .setResponseCode(errorHttpStatusCode.value())
        )

        // Test
        StepVerifier.create(
                commandsService.requestNpgRefund(
                    operationId = operationId,
                    transactionId = transactionId,
                    amount = amount,
                    pspId = PSP_ID,
                    correlationId = correlationId,
                    paymentMethod = PaymentMethod.CARDS
                )
            )
            .expectError(expectedException)
            .verify()
        verify(npgClient, times(1))
            .refundPayment(
                eq(PSP_KEY),
                eq(UUID.fromString(correlationId)),
                eq(operationId),
                eq(transactionId),
                eq(amount),
                any()
            )
    }

    @ParameterizedTest
    @MethodSource("npgErrorsExpectedResponses")
    fun `should handle npg error response without error body`(
        errorHttpStatusCode: HttpStatus,
        expectedException: Class<out Throwable>
    ) {
        val operationId = "operationID"
        val transactionId = UUID.randomUUID().toString()
        val correlationId = UUID.randomUUID().toString()
        val amount = BigDecimal.valueOf(1000)
        // Precondition
        mockWebServer.enqueue(MockResponse().setResponseCode(errorHttpStatusCode.value()))

        // Test
        StepVerifier.create(
                commandsService.requestNpgRefund(
                    operationId = operationId,
                    transactionId = transactionId,
                    amount = amount,
                    pspId = PSP_ID,
                    correlationId = correlationId,
                    paymentMethod = PaymentMethod.CARDS
                )
            )
            .expectError(expectedException)
            .verify()
        verify(npgClient, times(1))
            .refundPayment(
                eq(PSP_KEY),
                eq(UUID.fromString(correlationId)),
                eq(operationId),
                eq(transactionId),
                eq(amount),
                any()
            )
    }

    @Test
    fun `should handle npg error without http response code info`() {
        val npgClient: NpgClient = mock()
        val refundService =
            CommandsService(npgClient = npgClient, npgApiKeyConfiguration = npgApiKeyConfiguration)
        val operationId = "operationID"
        val transactionId = UUID.randomUUID().toString()
        val correlationId = UUID.randomUUID().toString()
        val amount = BigDecimal.valueOf(1000)
        // Precondition
        given(npgClient.refundPayment(any(), any(), any(), any(), any(), any()))
            .willReturn(
                Mono.error(
                    NpgClientException(
                        "Invalid error response from NPG with status code 500",
                        HttpStatus.BAD_GATEWAY
                    )
                )
            )

        // Test
        StepVerifier.create(
                refundService.requestNpgRefund(
                    operationId = operationId,
                    transactionId = transactionId,
                    amount = amount,
                    pspId = PSP_ID,
                    correlationId = correlationId,
                    paymentMethod = PaymentMethod.CARDS
                )
            )
            .expectError(NpgClientException::class.java)
            .verify()
        verify(npgClient, times(1))
            .refundPayment(
                eq(PSP_KEY),
                eq(UUID.fromString(correlationId)),
                eq(operationId),
                eq(transactionId),
                eq(amount),
                any()
            )
    }

    @Test
    fun `should not call NPG and return error for not configured PSP key`() {
        val npgClient: NpgClient = mock()
        val refundService =
            CommandsService(npgClient = npgClient, npgApiKeyConfiguration = npgApiKeyConfiguration)
        val operationId = "operationID"
        val transactionId = UUID.randomUUID().toString()
        val correlationId = UUID.randomUUID().toString()
        val amount = BigDecimal.valueOf(1000)
        val pspId = "unknown"
        // Precondition

        // Test
        StepVerifier.create(
                refundService.requestNpgRefund(
                    operationId = operationId,
                    transactionId = transactionId,
                    amount = amount,
                    pspId = pspId,
                    correlationId = correlationId,
                    paymentMethod = PaymentMethod.CARDS
                )
            )
            .expectError(NpgApiKeyConfigurationException::class.java)
            .verify()
        verify(npgClient, times(0)).refundPayment(any(), any(), any(), any(), any(), any())
    }
}
