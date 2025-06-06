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
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.helpdeskcommands.client.NodeForwarderClient
import it.pagopa.helpdeskcommands.client.NpgClient
import it.pagopa.helpdeskcommands.config.WebClientConfig
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import it.pagopa.helpdeskcommands.exceptions.NpgClientException
import java.time.Duration
import java.util.*
import java.util.stream.Stream
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@SpringBootTest
@TestPropertySource(locations = ["classpath:application.test.properties"])
class TransactionEventServiceTest {

    final val transactionIdString = "93cce28d3b7c4cb9975e6d856ecee89f"

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

    private val nodeForwarderRedirectApiClient:
        NodeForwarderClient<RedirectRefundRequestDto, RedirectRefundResponseDto> =
        mock()

    private val refundQueueClient: QueueAsyncClient = mock()
    private val notificationQueueClient: QueueAsyncClient = mock()
    private val transientQueueTTLSeconds: Long = 2700 // 45 minutes
    private lateinit var transactionEventService: TransactionEventService
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

        transactionEventService =
            TransactionEventService(
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
    fun `should send refund requested event successfully`() {
        val transactionId = transactionIdString
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

        StepVerifier.create(transactionEventService.sendRefundRequestedEvent(refundEvent))
            .verifyComplete()

        verify(spiedTracingUtils)
            .traceMono(
                eq(TransactionEventService::class.java.simpleName),
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
        val transactionId = transactionIdString
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

        StepVerifier.create(transactionEventService.sendRefundRequestedEvent(event))
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `should send notification requested event successfully`() {
        val transactionId = transactionIdString

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

        StepVerifier.create(
                transactionEventService.sendNotificationRequestedEvent(notificationEvent)
            )
            .verifyComplete()

        verify(spiedTracingUtils)
            .traceMono(
                eq(TransactionEventService::class.java.simpleName),
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
        val transactionId = transactionIdString

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

        StepVerifier.create(transactionEventService.sendNotificationRequestedEvent(event))
            .expectError(RuntimeException::class.java)
            .verify()
    }
}
