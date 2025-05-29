package it.pagopa.helpdeskcommands.services

import com.azure.core.http.rest.Response as AzureResponse
import com.azure.storage.queue.models.SendMessageResult
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter
import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.ecommerce.commons.queues.TracingInfo
import it.pagopa.ecommerce.commons.queues.TracingUtils
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundRequestDto as RedirectRefundRequestDto
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundResponseDto as RedirectRefundResponseDto
import it.pagopa.generated.helpdeskcommands.model.RefundOutcomeDto
import it.pagopa.generated.helpdeskcommands.model.RefundRedirectResponseDto
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.helpdeskcommands.client.NodeForwarderClient
import it.pagopa.helpdeskcommands.client.NpgClient
import it.pagopa.helpdeskcommands.config.WebClientConfig
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyConfigurationException
import it.pagopa.helpdeskcommands.exceptions.NpgClientException
import it.pagopa.helpdeskcommands.exceptions.RedirectConfigurationException
import it.pagopa.helpdeskcommands.utils.NpgApiKeyConfiguration
import it.pagopa.helpdeskcommands.utils.NpgPspApiKeysConfig
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import it.pagopa.helpdeskcommands.utils.RedirectKeysConfiguration
import it.pagopa.helpdeskcommands.utils.TransactionId
import java.math.BigDecimal
import java.net.URI
import java.time.Duration
import java.util.*
import java.util.stream.Stream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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

    private val refundQueueClient: QueueAsyncClient = mock()
    private val notificationQueueClient: QueueAsyncClient = mock()
    private val transientQueueTTLSeconds: Long = 2700 // 45 minutes
    private lateinit var commandsService: CommandsService
    private lateinit var spiedTracingUtils: TracingUtils

    @BeforeEach
    fun setupWithRealTracingUtils() {
        reset(npgClient, nodeForwarderRedirectApiClient, refundQueueClient, notificationQueueClient)

        val mockOpenTelemetry: OpenTelemetry = mock()
        val mockTracer: Tracer = mock()
        val mockSpanBuilder: SpanBuilder = mock()
        val mockSpan: Span = mock()
        val mockContextPropagators: ContextPropagators = mock()
        val mockTextMapPropagator: TextMapPropagator = mock()

        given(mockOpenTelemetry.getTracer(anyString())).willReturn(mockTracer)
        given(mockOpenTelemetry.propagators).willReturn(mockContextPropagators)
        given(mockContextPropagators.textMapPropagator).willReturn(mockTextMapPropagator)

        Mockito.doAnswer { invocation ->
                val carrier = invocation.getArgument<HashMap<String, String>>(1)
                @Suppress("UNCHECKED_CAST")
                val setter = invocation.getArgument<TextMapSetter<HashMap<String, String>>>(2)

                setter.set(carrier, TracingUtils.TRACEPARENT, "00-dummytraceid-dummyspanid-01")
                setter.set(carrier, TracingUtils.TRACESTATE, "statekey=statevalue")
                setter.set(carrier, TracingUtils.BAGGAGE, "baggagekey=baggagevalue")
                null
            }
            .`when`(mockTextMapPropagator)
            .inject(
                any<Context>(),
                any<HashMap<String, String>>(),
                any<TextMapSetter<HashMap<String, String>>>()
            )

        given(mockTextMapPropagator.fields())
            .willReturn(
                listOf(TracingUtils.TRACEPARENT, TracingUtils.TRACESTATE, TracingUtils.BAGGAGE)
            )

        given(mockTracer.spanBuilder(anyString())).willReturn(mockSpanBuilder)

        given(mockSpanBuilder.setSpanKind(any<SpanKind>())).willReturn(mockSpanBuilder)
        given(mockSpanBuilder.setParent(any<Context>())).willReturn(mockSpanBuilder)
        given(mockSpanBuilder.setNoParent()).willReturn(mockSpanBuilder)
        given(mockSpanBuilder.startSpan()).willReturn(mockSpan)

        Mockito.`when`(mockSpan.spanContext).thenReturn(SpanContext.getInvalid())
        given(mockSpan.isRecording).willReturn(true)

        val actualTracingUtilsInstance = TracingUtils(mockOpenTelemetry, mockTracer)
        this.spiedTracingUtils = spy(actualTracingUtilsInstance)

        commandsService =
            CommandsService(
                npgClient = npgClient,
                npgApiKeyConfiguration = npgApiKeyConfiguration,
                redirectKeysConfiguration = redirectKeysConfiguration,
                nodeForwarderClient = nodeForwarderRedirectApiClient,
                refundQueueClient = Mono.just(refundQueueClient),
                notificationQueueClient = Mono.just(notificationQueueClient),
                transientQueueTTLSeconds = transientQueueTTLSeconds,
                tracingUtils = this.spiedTracingUtils
            )
    }

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
                nodeForwarderClient = nodeForwarderRedirectApiClient,
                refundQueueClient = Mono.just(mock()),
                notificationQueueClient = Mono.just(mock()),
                transientQueueTTLSeconds = 2700,
                tracingUtils = mock()
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
                nodeForwarderClient = nodeForwarderRedirectApiClient,
                refundQueueClient = Mono.just(mock()),
                notificationQueueClient = Mono.just(mock()),
                transientQueueTTLSeconds = 2700,
                tracingUtils = mock()
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
                nodeForwarderClient = nodeForwarderRedirectApiClient,
                refundQueueClient = Mono.just(mock()),
                notificationQueueClient = Mono.just(mock()),
                transientQueueTTLSeconds = 2700,
                tracingUtils = mock()
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

    @Test
    fun `should send refund requested event successfully`() {
        val transactionId = TRANSACTION_ID_STRING
        val refundEvent =
            TransactionRefundRequestedEvent(
                transactionId,
                TransactionRefundRequestedData(
                    null,
                    TransactionStatusDto.CLOSED,
                    TransactionRefundRequestedData.RefundTrigger.MANUAL
                )
            )

        val mockAzureResponse: AzureResponse<SendMessageResult> = mock()
        val queueEventCaptor = argumentCaptor<QueueEvent<TransactionRefundRequestedEvent>>()

        given(
                refundQueueClient.sendMessageWithResponse(
                    any<QueueEvent<TransactionRefundRequestedEvent>>(),
                    eq(Duration.ZERO),
                    eq(Duration.ofSeconds(transientQueueTTLSeconds))
                )
            )
            .willReturn(Mono.just(mockAzureResponse))

        StepVerifier.create(commandsService.sendRefundRequestedEvent(refundEvent)).verifyComplete()

        verify(spiedTracingUtils)
            .traceMono(
                eq(CommandsService::class.java.simpleName),
                any<java.util.function.Function<TracingInfo, Mono<Void>>>()
            )

        verify(refundQueueClient)
            .sendMessageWithResponse(
                queueEventCaptor.capture(),
                eq(Duration.ZERO),
                eq(Duration.ofSeconds(transientQueueTTLSeconds))
            )

        val capturedQueueEvent = queueEventCaptor.firstValue
        assertEquals(refundEvent, capturedQueueEvent.event)
        assertNotNull(
            capturedQueueEvent.tracingInfo,
            "TracingInfo should be populated in QueueEvent"
        )
    }

    @Test
    fun `should handle queue error when sending refund event`() {
        val transactionId = TRANSACTION_ID_STRING
        val event =
            TransactionRefundRequestedEvent(
                transactionId,
                TransactionRefundRequestedData(
                    null,
                    TransactionStatusDto.CLOSED,
                    TransactionRefundRequestedData.RefundTrigger.MANUAL
                )
            )

        given(
                refundQueueClient.sendMessageWithResponse(
                    any<QueueEvent<TransactionRefundRequestedEvent>>(),
                    any<Duration>(),
                    any<Duration>()
                )
            )
            .willReturn(Mono.error(RuntimeException("Queue error")))

        StepVerifier.create(commandsService.sendRefundRequestedEvent(event))
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `should send notification requested event successfully`() {
        val transactionId = TRANSACTION_ID_STRING

        val userReceiptData =
            TransactionUserReceiptData(
                TransactionUserReceiptData.Outcome.OK,
                "IT",
                "2023-10-26T10:00:00",
                TransactionUserReceiptData.NotificationTrigger.MANUAL
            )

        val notificationEvent = TransactionUserReceiptRequestedEvent(transactionId, userReceiptData)

        val mockAzureResponse: AzureResponse<SendMessageResult> = mock()
        val queueEventCaptor = argumentCaptor<QueueEvent<TransactionUserReceiptRequestedEvent>>()

        given(
                notificationQueueClient.sendMessageWithResponse(
                    any<QueueEvent<TransactionUserReceiptRequestedEvent>>(),
                    eq(Duration.ZERO),
                    eq(Duration.ofSeconds(transientQueueTTLSeconds))
                )
            )
            .willReturn(Mono.just(mockAzureResponse))

        StepVerifier.create(commandsService.sendNotificationRequestedEvent(notificationEvent))
            .verifyComplete()

        verify(spiedTracingUtils)
            .traceMono(
                eq(CommandsService::class.java.simpleName),
                any<java.util.function.Function<TracingInfo, Mono<Void>>>()
            )

        verify(notificationQueueClient)
            .sendMessageWithResponse(
                queueEventCaptor.capture(),
                eq(Duration.ZERO),
                eq(Duration.ofSeconds(transientQueueTTLSeconds))
            )

        val capturedQueueEvent = queueEventCaptor.firstValue
        assertEquals(notificationEvent, capturedQueueEvent.event)
        assertNotNull(
            capturedQueueEvent.tracingInfo,
            "TracingInfo should be populated in QueueEvent"
        )
    }

    @Test
    fun `should handle queue error when sending notification event`() {
        val transactionId = TRANSACTION_ID_STRING

        val userReceiptDataForError =
            TransactionUserReceiptData(
                TransactionUserReceiptData.Outcome.OK,
                "IT",
                "2023-10-26T10:00:00",
                TransactionUserReceiptData.NotificationTrigger.MANUAL
            )
        val event = TransactionUserReceiptRequestedEvent(transactionId, userReceiptDataForError)

        given(
                notificationQueueClient.sendMessageWithResponse(
                    any<QueueEvent<TransactionUserReceiptRequestedEvent>>(),
                    any<Duration>(),
                    any<Duration>()
                )
            )
            .willReturn(Mono.error(RuntimeException("Queue error")))

        StepVerifier.create(commandsService.sendNotificationRequestedEvent(event))
            .expectError(RuntimeException::class.java)
            .verify()
    }
}
