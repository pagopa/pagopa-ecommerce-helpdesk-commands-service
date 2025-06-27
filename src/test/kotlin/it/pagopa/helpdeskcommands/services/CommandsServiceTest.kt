package it.pagopa.helpdeskcommands.services

import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundRequestDto as RedirectRefundRequestDto
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundResponseDto as RedirectRefundResponseDto
import it.pagopa.generated.helpdeskcommands.model.RefundOutcomeDto
import it.pagopa.generated.helpdeskcommands.model.RefundRedirectResponseDto
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.helpdeskcommands.client.NodeForwarderClient
import it.pagopa.helpdeskcommands.client.NpgClient
import it.pagopa.helpdeskcommands.config.WebClientConfig
import it.pagopa.helpdeskcommands.exceptions.*
import it.pagopa.helpdeskcommands.utils.*
import java.math.BigDecimal
import java.net.URI
import java.util.*
import java.util.stream.Stream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    final val TRANSACTION_ID_STRING = "93cce28d3b7c4cb9975e6d856ecee89f"

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

    private val nodeForwarderRedirectApiClient:
        NodeForwarderClient<RedirectRefundRequestDto, RedirectRefundResponseDto> =
        mock()

    private val redirectBeApiCallUriMap: Map<String, URI> =
        mapOf("pspId-RPIC" to URI.create("http://redirect/RPIC"))
    private val redirectBeAoiCallUriSet: Set<String> = setOf("pspId-RPIC")
    private val redirectKeysConfiguration: RedirectKeysConfiguration =
        RedirectKeysConfiguration(
            mapOf("pspId-RPIC" to "http://redirect/RPIC"),
            redirectBeAoiCallUriSet
        )

    private val commandsService: CommandsService =
        CommandsService(
            npgClient = npgClient,
            npgApiKeyConfiguration = npgApiKeyConfiguration,
            redirectKeysConfiguration = redirectKeysConfiguration,
            nodeForwarderClient = nodeForwarderRedirectApiClient
        )

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

        @JvmStatic
        private fun touchpoints(): Stream<Arguments> =
            Stream.of(Arguments.of("CHECKOUT"), Arguments.of("CHECKOUT_CART"), Arguments.of("IO"))

        @JvmStatic
        private fun `Redirect refund errors method source`(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    HttpStatus.BAD_REQUEST,
                    NodeForwarderClientException::class.java,
                    "CHECKOUT"
                ),
                Arguments.of(
                    HttpStatus.UNAUTHORIZED,
                    NodeForwarderClientException::class.java,
                    "IO"
                ),
                Arguments.of(
                    HttpStatus.NOT_FOUND,
                    NodeForwarderClientException::class.java,
                    "CHECKOUT_CART"
                ),
                Arguments.of(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    NodeForwarderClientException::class.java,
                    "CHECKOUT"
                ),
                Arguments.of(
                    HttpStatus.GATEWAY_TIMEOUT,
                    NodeForwarderClientException::class.java,
                    "IO"
                ),
                Arguments.of(null, RuntimeException::class.java, "CHECKOUT_CART"),
            )
    }

    @Test
    fun requestRefund_200_npg() {
        val operationId = "operationID"
        val transactionId = TransactionId(TRANSACTION_ID_STRING)
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
                eq(transactionId.uuid),
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
        val transactionId = TransactionId(TRANSACTION_ID_STRING)
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
                eq(transactionId.uuid),
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
        val transactionId = TransactionId(TRANSACTION_ID_STRING)
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
                eq(transactionId.uuid),
                eq(amount),
                any()
            )
    }

    @Test
    fun `should handle npg generic error`() {
        val npgClient: NpgClient = mock()
        val refundService =
            CommandsService(
                npgClient = npgClient,
                npgApiKeyConfiguration = npgApiKeyConfiguration,
                redirectKeysConfiguration = redirectKeysConfiguration,
                nodeForwarderClient = nodeForwarderRedirectApiClient
            )
        val operationId = "operationID"
        val transactionId = TransactionId(TRANSACTION_ID_STRING)
        val correlationId = UUID.randomUUID().toString()
        val amount = BigDecimal.valueOf(1000)
        // Precondition
        given(npgClient.refundPayment(any(), any(), any(), any(), any(), any()))
            .willReturn(
                Mono.error(
                    NpgClientException(
                        "Invalid error response from NPG with status code 500",
                        HttpStatus.BAD_GATEWAY,
                        emptyList()
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
                eq(transactionId.uuid),
                eq(amount),
                any()
            )
    }

    @Test
    fun `should handle npg error without http response code info`() {
        val npgClient: NpgClient = mock()
        val refundService =
            CommandsService(
                npgClient = npgClient,
                npgApiKeyConfiguration = npgApiKeyConfiguration,
                redirectKeysConfiguration = redirectKeysConfiguration,
                nodeForwarderClient = nodeForwarderRedirectApiClient
            )
        val operationId = "operationID"
        val transactionId = TransactionId(TRANSACTION_ID_STRING)
        val correlationId = UUID.randomUUID().toString()
        val amount = BigDecimal.valueOf(1000)
        // Precondition
        given(npgClient.refundPayment(any(), any(), any(), any(), any(), any()))
            .willReturn(
                Mono.error(
                    NpgClientException(
                        "Invalid error response from NPG with status code 500",
                        HttpStatus.BAD_GATEWAY,
                        emptyList()
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
                eq(transactionId.uuid),
                eq(amount),
                any()
            )
    }

    @Test
    fun `should not call NPG and return error for not configured PSP key`() {
        val npgClient: NpgClient = mock()
        val refundService =
            CommandsService(
                npgClient = npgClient,
                npgApiKeyConfiguration = npgApiKeyConfiguration,
                redirectKeysConfiguration = redirectKeysConfiguration,
                nodeForwarderClient = nodeForwarderRedirectApiClient
            )
        val operationId = "operationID"
        val transactionId = TransactionId(TRANSACTION_ID_STRING)
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

    @ParameterizedTest()
    @MethodSource("touchpoints")
    fun `Should perform redirect refund successfully`(touchpoint: String) {
        // pre-requisites
        val transactionId = TRANSACTION_ID_STRING
        val pspTransactionId = "pspTransactionId"
        val paymentTypeCode = "RPIC"
        val pspId = "pspId"
        val redirectRefundResponse =
            RefundRedirectResponseDto().idTransaction(transactionId).outcome(RefundOutcomeDto.OK)
        val redirectRefundResponseDto =
            RedirectRefundResponseDto()
                .idTransaction(transactionId)
                .outcome(it.pagopa.generated.ecommerce.redirect.v1.dto.RefundOutcomeDto.OK)
        val expectedRequest =
            RedirectRefundRequestDto()
                .action("refund")
                .idPSPTransaction(pspTransactionId)
                .idTransaction(transactionId)
        given(nodeForwarderRedirectApiClient.proxyRequest(any(), any(), any(), any()))
            .willReturn(
                Mono.just(
                    NodeForwarderClient.NodeForwarderResponse(
                        redirectRefundResponseDto,
                        Optional.empty()
                    )
                )
            )
        // test
        StepVerifier.create(
                commandsService.requestRedirectRefund(
                    transactionId = TransactionId(transactionId),
                    touchpoint = touchpoint,
                    pspTransactionId = pspTransactionId,
                    paymentTypeCode = paymentTypeCode,
                    pspId = pspId
                )
            )
            .expectNext(redirectRefundResponse)
            .verifyComplete()
        verify(nodeForwarderRedirectApiClient, times(1))
            .proxyRequest(
                expectedRequest,
                redirectBeApiCallUriMap["pspId-$paymentTypeCode"]!!,
                transactionId,
                RedirectRefundResponseDto::class.java
            )
    }

    @ParameterizedTest()
    @MethodSource("touchpoints")
    fun `Should return error performing redirect refund for missing backend URL`(
        touchpoint: String
    ) {
        // pre-requisites
        val transactionId = TRANSACTION_ID_STRING
        val pspTransactionId = "pspTransactionId"
        val paymentTypeCode = "MISSING"
        // test
        StepVerifier.create(
                commandsService.requestRedirectRefund(
                    transactionId = TransactionId(transactionId),
                    touchpoint = touchpoint,
                    pspTransactionId = pspTransactionId,
                    paymentTypeCode = paymentTypeCode,
                    pspId = "pspId"
                )
            )
            .expectErrorMatches {
                assertTrue(it is RedirectConfigurationException)
                assertEquals(
                    "Error parsing Redirect PSP BACKEND_URLS configuration, cause: Missing key for redirect return url with following search parameters: touchpoint: [${touchpoint}] pspId: [pspId] paymentTypeCode: [MISSING]",
                    it.message
                )
                true
            }
            .verify()
        verify(nodeForwarderRedirectApiClient, times(0)).proxyRequest(any(), any(), any(), any())
    }

    @ParameterizedTest
    @MethodSource("Redirect refund errors method source")
    fun `Should handle returned error performing redirect refund call`(
        httpErrorCode: HttpStatus?,
        expectedErrorClass: Class<Exception>,
        touchpoint: String
    ) {
        // pre-requisites
        val transactionId = TRANSACTION_ID_STRING
        val pspTransactionId = "pspTransactionId"
        val paymentTypeCode = "RPIC"
        val pspId = "pspId"
        val expectedRequest =
            RedirectRefundRequestDto()
                .action("refund")
                .idPSPTransaction(pspTransactionId)
                .idTransaction(transactionId)
        given(nodeForwarderRedirectApiClient.proxyRequest(any(), any(), any(), any()))
            .willReturn(
                Mono.error(
                    if (httpErrorCode != null) {
                        NodeForwarderClientException(
                            description = "Error performing refund",
                            httpStatusCode = httpErrorCode,
                            errors = emptyList()
                        )
                    } else {
                        RuntimeException("Error performing request")
                    }
                )
            )
        // test
        StepVerifier.create(
                commandsService.requestRedirectRefund(
                    transactionId = TransactionId(transactionId),
                    touchpoint = touchpoint,
                    pspTransactionId = pspTransactionId,
                    paymentTypeCode = paymentTypeCode,
                    pspId = pspId
                )
            )
            .expectError(expectedErrorClass)
            .verify()
        verify(nodeForwarderRedirectApiClient, times(1))
            .proxyRequest(
                expectedRequest,
                redirectBeApiCallUriMap["pspId-$paymentTypeCode"]!!,
                transactionId,
                RedirectRefundResponseDto::class.java
            )
    }
}
