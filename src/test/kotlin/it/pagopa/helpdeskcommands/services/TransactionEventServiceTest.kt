package it.pagopa.helpdeskcommands.services

import com.azure.core.http.rest.Response as AzureResponse
import com.azure.storage.queue.models.SendMessageResult
import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundRequestDto as RedirectRefundRequestDto
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundResponseDto as RedirectRefundResponseDto
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.helpdeskcommands.client.NodeForwarderClient
import it.pagopa.helpdeskcommands.client.NpgClient
import it.pagopa.helpdeskcommands.config.WebClientConfig
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import it.pagopa.helpdeskcommands.exceptions.NpgClientException
import java.time.Duration
import java.util.stream.Stream
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
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

    @BeforeEach
    fun setup() {
        reset(npgClient, nodeForwarderRedirectApiClient, refundQueueClient, notificationQueueClient)

        transactionEventService =
            TransactionEventService(
                refundQueueClient = refundQueueClient,
                notificationQueueClient = notificationQueueClient,
                transientQueueTTLSeconds = transientQueueTTLSeconds
            )
    }

    companion object {
        lateinit var mockWebServer: MockWebServer

        @JvmStatic
        @BeforeAll
        fun setupMockServer() {
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

        verify(refundQueueClient)
            .sendMessageWithResponse(
                queueEventCaptor.capture(),
                eq(Duration.ZERO),
                eq(Duration.ofSeconds(transientQueueTTLSeconds))
            )

        val capturedQueueEvent = queueEventCaptor.firstValue
        assertEquals(refundEvent, capturedQueueEvent.event)
        assertEquals(
            null,
            capturedQueueEvent.tracingInfo,
            "TracingInfo should be null in QueueEvent"
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

        verify(notificationQueueClient)
            .sendMessageWithResponse(
                queueEventCaptor.capture(),
                eq(Duration.ZERO),
                eq(Duration.ofSeconds(transientQueueTTLSeconds))
            )

        val capturedQueueEvent = queueEventCaptor.firstValue
        assertEquals(notificationEvent, capturedQueueEvent.event)
        assertEquals(
            null,
            capturedQueueEvent.tracingInfo,
            "TracingInfo should be null in QueueEvent"
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
