package it.pagopa.helpdeskcommands.services

import com.azure.core.http.rest.Response as AzureResponse
import com.azure.storage.queue.models.SendMessageResult
import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.BaseTransactionEvent
import it.pagopa.ecommerce.commons.documents.v2.BaseTransactionRefundedData
import it.pagopa.ecommerce.commons.documents.v2.Transaction
import it.pagopa.ecommerce.commons.documents.v2.TransactionActivatedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionActivatedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionAuthorizationRequestData
import it.pagopa.ecommerce.commons.documents.v2.TransactionAuthorizationRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.authorization.RedirectTransactionGatewayAuthorizationRequestedData
import it.pagopa.ecommerce.commons.domain.v2.TransactionId
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransaction
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransactionWithRefundRequested
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundRequestDto as RedirectRefundRequestDto
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundResponseDto as RedirectRefundResponseDto
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.helpdeskcommands.client.NodeForwarderClient
import it.pagopa.helpdeskcommands.client.NpgClient
import it.pagopa.helpdeskcommands.config.WebClientConfig
import it.pagopa.helpdeskcommands.exceptions.InvalidTransactionStatusException
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import it.pagopa.helpdeskcommands.exceptions.NpgClientException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import it.pagopa.helpdeskcommands.repositories.TransactionsEventStoreRepository
import it.pagopa.helpdeskcommands.repositories.TransactionsViewRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.stream.Stream
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@SpringBootTest
@TestPropertySource(locations = ["classpath:application.test.properties"])
class TransactionEventServiceTest {

    val transactionIdString = "93cce28d3b7c4cb9975e6d856ecee89f"

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

    @Mock
    private lateinit var transactionsEventStoreRepository: TransactionsEventStoreRepository<Any>

    @Mock
    private lateinit var transactionsRefundedEventStoreRepository:
        TransactionsEventStoreRepository<BaseTransactionRefundedData>

    @Mock private lateinit var transactionsViewRepository: TransactionsViewRepository

    @Mock
    private lateinit var userReceiptEventStoreRepository:
        TransactionsEventStoreRepository<TransactionUserReceiptData>

    @Captor
    private lateinit var userReceiptEventCaptor:
        ArgumentCaptor<TransactionUserReceiptRequestedEvent>

    @BeforeEach
    fun setup() {
        reset(npgClient, nodeForwarderRedirectApiClient, refundQueueClient, notificationQueueClient)

        transactionEventService =
            TransactionEventService(
                refundQueueClient = refundQueueClient,
                notificationQueueClient = notificationQueueClient,
                transientQueueTTLSeconds = transientQueueTTLSeconds,
                transactionsEventStoreRepository = transactionsEventStoreRepository,
                transactionsRefundedEventStoreRepository = transactionsRefundedEventStoreRepository,
                transactionsViewRepository = transactionsViewRepository,
                userReceiptEventStoreRepository = userReceiptEventStoreRepository
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

    @Test
    fun `resendUserReceiptNotification should create and save a new event for transaction in NOTIFICATION_REQUESTED state`() {
        // Given
        val mockTransaction = Mockito.mock(BaseTransaction::class.java)
        val mockTransactionId = Mockito.mock(TransactionId::class.java)

        doReturn(mockTransactionId).`when`(mockTransaction).transactionId
        doReturn(transactionIdString).`when`(mockTransactionId).value()
        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .getTransaction(transactionIdString)

        val existingUserReceiptEvent = createUserReceiptRequestedEvent(ZonedDateTime.now())
        val events = listOf(existingUserReceiptEvent)

        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        doAnswer { invocation ->
                Mono.just(invocation.getArgument(0) as TransactionUserReceiptRequestedEvent)
            }
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        val mockTx = Mockito.mock(Transaction::class.java)
        doReturn(Mono.just(mockTx))
            .`when`(transactionsViewRepository)
            .findByTransactionId(transactionIdString)
        doReturn(Mono.just(mockTx)).`when`(transactionsViewRepository).save(any())

        // When
        val result = transactionEventServiceSpy.resendUserReceiptNotification(transactionIdString)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionIdString, event.transactionId)
                assertEquals(
                    event.data.notificationTrigger,
                    TransactionUserReceiptData.NotificationTrigger.MANUAL
                )
                assertNotEquals(existingUserReceiptEvent.id, event.id)
            }
            .verifyComplete()

        verify(userReceiptEventStoreRepository).save(capture(userReceiptEventCaptor))
        val savedEvent = userReceiptEventCaptor.value
        assertEquals(transactionIdString, savedEvent.transactionId)
    }

    @Test
    fun `resendUserReceiptNotification should create and save a new event for transaction in EXPIRED state`() {
        // Given
        val mockTransaction = Mockito.mock(BaseTransaction::class.java)
        val mockTransactionId = Mockito.mock(TransactionId::class.java)

        doReturn(mockTransactionId).`when`(mockTransaction).transactionId
        doReturn(transactionIdString).`when`(mockTransactionId).value()
        doReturn(TransactionStatusDto.EXPIRED).`when`(mockTransaction).status

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .getTransaction(transactionIdString)

        val existingUserReceiptEvent = createUserReceiptRequestedEvent(ZonedDateTime.now())
        val events = listOf(existingUserReceiptEvent)

        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        doAnswer { invocation ->
                Mono.just(invocation.getArgument(0) as TransactionUserReceiptRequestedEvent)
            }
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        val mockTx = Mockito.mock(Transaction::class.java)
        doReturn(Mono.just(mockTx))
            .`when`(transactionsViewRepository)
            .findByTransactionId(transactionIdString)
        doReturn(Mono.just(mockTx)).`when`(transactionsViewRepository).save(any())

        // When
        val result = transactionEventServiceSpy.resendUserReceiptNotification(transactionIdString)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionIdString, event.transactionId)
                assertEquals(
                    event.data.notificationTrigger,
                    TransactionUserReceiptData.NotificationTrigger.MANUAL
                )
                assertNotEquals(existingUserReceiptEvent.id, event.id)
            }
            .verifyComplete()

        verify(userReceiptEventStoreRepository).save(capture(userReceiptEventCaptor))
        val savedEvent = userReceiptEventCaptor.value
        assertEquals(transactionIdString, savedEvent.transactionId)
    }

    @Test
    fun `resendUserReceiptNotification should handle error when saving new event`() {
        // Given
        val mockTransaction = Mockito.mock(BaseTransaction::class.java)
        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .getTransaction(transactionIdString)

        val existingUserReceiptEvent = createUserReceiptRequestedEvent(ZonedDateTime.now())
        val events = listOf(existingUserReceiptEvent)

        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        doReturn(
                Mono.error<TransactionUserReceiptRequestedEvent>(RuntimeException("Database error"))
            )
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        // When
        val result = transactionEventServiceSpy.resendUserReceiptNotification(transactionIdString)

        // Then
        StepVerifier.create(result).expectError(RuntimeException::class.java).verify()

        verify(userReceiptEventStoreRepository).save(any())
    }

    @Test
    fun `resendUserReceiptNotification should handle multiple existing events and use the latest one`() {
        // Given
        val mockTransaction = Mockito.mock(BaseTransaction::class.java)
        val mockTransactionId = Mockito.mock(TransactionId::class.java)

        doReturn(mockTransactionId).`when`(mockTransaction).transactionId
        doReturn(transactionIdString).`when`(mockTransactionId).value()
        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .getTransaction(transactionIdString)

        // Create multiple events with different timestamps
        val oldEvent = createUserReceiptRequestedEvent(ZonedDateTime.now().minusDays(2))
        val latestEvent = createUserReceiptRequestedEvent(ZonedDateTime.now())
        val events = listOf(oldEvent, latestEvent)

        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        doAnswer { invocation ->
                Mono.just(invocation.getArgument(0) as TransactionUserReceiptRequestedEvent)
            }
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        val mockTx = Mockito.mock(Transaction::class.java)
        doReturn(Mono.just(mockTx))
            .`when`(transactionsViewRepository)
            .findByTransactionId(transactionIdString)
        doReturn(Mono.just(mockTx)).`when`(transactionsViewRepository).save(any())

        // When
        val result = transactionEventServiceSpy.resendUserReceiptNotification(transactionIdString)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionIdString, event.transactionId)
                assertEquals(latestEvent.data.paymentDate, event.data.paymentDate)
                assertEquals(latestEvent.data.language, event.data.language)
                assertEquals(
                    TransactionUserReceiptData.NotificationTrigger.MANUAL,
                    event.data.notificationTrigger
                )
            }
            .verifyComplete()

        verify(userReceiptEventStoreRepository).save(capture(userReceiptEventCaptor))
        val savedEvent = userReceiptEventCaptor.value
        assertEquals(transactionIdString, savedEvent.transactionId)
    }

    private fun createUserReceiptRequestedEvent(
        timestamp: ZonedDateTime = ZonedDateTime.now()
    ): TransactionUserReceiptRequestedEvent {
        val mockData = Mockito.mock(TransactionUserReceiptData::class.java)

        // spy of TransactionUserReceiptRequestedEvent to control the creation date
        val event = TransactionUserReceiptRequestedEvent(transactionIdString, mockData)
        event.creationDate = timestamp.toString()
        val spyEvent = spy(event)
        return spyEvent
    }

    @Test
    fun `getTransaction should return transaction when events exist`() {
        val mockTransaction: BaseTransaction = Mockito.mock(BaseTransaction::class.java)
        val transactionEvent =
            TransactionActivatedEvent(transactionIdString, TransactionActivatedData())
        val events = listOf(transactionEvent)

        `when`(
                transactionsEventStoreRepository.findByTransactionIdOrderByCreationDateAsc(
                    transactionIdString
                )
            )
            .thenReturn(Flux.fromIterable(events) as Flux<BaseTransactionEvent<Any>>?)

        // Mock the reduction process
        val spyService = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(spyService)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        val result = spyService.getTransaction(transactionIdString)

        StepVerifier.create(result).expectNext(mockTransaction).verifyComplete()

        verify(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)
    }

    @Test
    fun `getTransaction should throw TransactionNotFoundException when transaction not found`() {
        // Given
        whenever(
                transactionsEventStoreRepository.findByTransactionIdOrderByCreationDateAsc(
                    transactionIdString
                )
            )
            .thenReturn(Flux.empty())

        // spy of the service
        val transactionEventServiceSpy = spy(transactionEventService)

        // Mock the reduceEvents method to return an empty Mono
        // This avoids the ClassCastException
        doReturn(Mono.empty<BaseTransaction>())
            .whenever(transactionEventServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // When
        val result = transactionEventServiceSpy.getTransaction(transactionIdString)

        // Then
        StepVerifier.create(result).expectError(TransactionNotFoundException::class.java).verify()

        // Verify that the repository was called with the correct transaction ID
        verify(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)
    }

    @Test
    fun `reduceEvents should throw TransactionNotFoundException when no events are provided`() {
        // Given
        val emptyFlux = Flux.empty<TransactionEvent<Any>>()

        // When
        val result = transactionEventService.reduceEvents(emptyFlux)

        // Then
        StepVerifier.create(result).expectError(TransactionNotFoundException::class.java).verify()
    }

    @Test
    fun `resendUserReceiptNotification should throw TransactionNotFoundException when transaction not found`() {
        // Given
        val transactionEventServiceSpy = spy(transactionEventService)

        doReturn(
                Mono.error<BaseTransaction>(
                    TransactionNotFoundException("Transaction not found: $transactionIdString")
                )
            )
            .whenever(transactionEventServiceSpy)
            .getTransaction(transactionIdString)

        // When
        val result = transactionEventServiceSpy.resendUserReceiptNotification(transactionIdString)

        // Then
        StepVerifier.create(result).expectError(TransactionNotFoundException::class.java).verify()

        verify(transactionEventServiceSpy).getTransaction(transactionIdString)
        verify(userReceiptEventStoreRepository, never()).save(any())
    }

    @Test
    fun `createRefundRequestEvent should create a valid refund request event for transaction`() {
        // Given
        val mockTransaction = Mockito.mock(BaseTransaction::class.java)
        val mockTransactionId = Mockito.mock(TransactionId::class.java)

        doReturn(mockTransactionId).`when`(mockTransaction).transactionId
        doReturn(transactionIdString).`when`(mockTransactionId).value()
        doReturn(TransactionStatusDto.CLOSED).`when`(mockTransaction).status

        val transactionEvent =
            TransactionActivatedEvent(transactionIdString, TransactionActivatedData())

        doReturn(Flux.just(transactionEvent))
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        @Suppress("UNCHECKED_CAST")
        doReturn(Mono.just(transactionEvent as TransactionEvent<BaseTransactionRefundedData>))
            .`when`(transactionsRefundedEventStoreRepository)
            .save(any())

        val mockTx = Mockito.mock(Transaction::class.java)
        doReturn(Mono.just(mockTx))
            .`when`(transactionsViewRepository)
            .findByTransactionId(transactionIdString)

        doReturn(Mono.just(mockTx)).`when`(transactionsViewRepository).save(any())

        // When
        val result = transactionEventServiceSpy.createRefundRequestEvent(transactionIdString)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionIdString, event?.transactionId)
                assertTrue(event?.data is TransactionRefundRequestedData)
                val data = event?.data as TransactionRefundRequestedData
                assertEquals(TransactionStatusDto.CLOSED, data.statusBeforeRefunded)
                assertEquals(
                    TransactionRefundRequestedData.RefundTrigger.MANUAL,
                    data.refundTrigger
                )
            }
            .verifyComplete()

        // Verify repository calls
        verify(transactionsRefundedEventStoreRepository).save(any())
        verify(transactionsViewRepository).findByTransactionId(transactionIdString)
        verify(transactionsViewRepository).save(any())
    }

    @Test
    fun `createRefundRequestEvent should create a valid refund request also for transaction already has refund requested`() {
        // Given
        val mockTransaction = Mockito.mock(BaseTransactionWithRefundRequested::class.java)
        val mockTransactionId = Mockito.mock(TransactionId::class.java)

        doReturn(mockTransactionId).`when`(mockTransaction).transactionId
        doReturn(transactionIdString).`when`(mockTransactionId).value()

        val transactionEvent =
            TransactionActivatedEvent(transactionIdString, TransactionActivatedData())

        doReturn(Flux.just(transactionEvent))
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        doReturn(Mono.just(mockTransaction))
            .`when`(transactionsRefundedEventStoreRepository)
            .save(any<TransactionEvent<BaseTransactionRefundedData>>())

        val mockTx = Mockito.mock(Transaction::class.java)
        doReturn(Mono.just(mockTx))
            .`when`(transactionsViewRepository)
            .findByTransactionId(transactionIdString)

        doReturn(Mono.just(mockTx)).`when`(transactionsViewRepository).save(mockTx)

        // When
        val result = transactionEventServiceSpy.createRefundRequestEvent(transactionIdString)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { event ->
                event is TransactionRefundRequestedEvent &&
                    event.transactionId == transactionIdString
            }
            .verifyComplete()

        verify(transactionsRefundedEventStoreRepository)
            .save(any<TransactionEvent<BaseTransactionRefundedData>>())
        verify(transactionsViewRepository).findByTransactionId(transactionIdString)
        verify(transactionsViewRepository).save(any())
    }

    @Test
    fun `getTransaction should return error when transaction not found`() {
        // Given
        doReturn(Flux.empty<TransactionEvent<Any>>())
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        // When
        val result = transactionEventService.getTransaction(transactionIdString)

        // Then
        StepVerifier.create(result).expectError(TransactionNotFoundException::class.java).verify()

        // Verify repository calls
        verify(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)
    }

    @Test
    fun `getTransaction should retrieve and reduce transaction events`() {
        // Given
        val transactionEvent1 =
            TransactionActivatedEvent(transactionIdString, TransactionActivatedData())

        val transactionData =
            RedirectTransactionGatewayAuthorizationRequestedData() // Initialize as needed

        val authRequestData =
            TransactionAuthorizationRequestData(
                1000, // amount
                50, // fee
                "paymentInstrumentId123", // paymentInstrumentId
                "pspId123", // pspId
                "paymentTypeCode123", // paymentTypeCode
                "brokerName123", // brokerName
                "pspChannelCode123", // pspChannelCode
                "paymentMethodName123", // paymentMethodName
                "pspBusinessName123", // pspBusinessName
                true, // isPspOnUs
                "authRequestId123", // authorizationRequestId
                TransactionAuthorizationRequestData.PaymentGateway.VPOS, // paymentGateway
                "paymentMethodDescription123", // paymentMethodDescription
                transactionData, // transactionGatewayAuthorizationRequestedData
                null // idBundle (optional, can be null)
            )

        // Create the event with the mocked data
        val transactionEvent2 =
            TransactionAuthorizationRequestedEvent(transactionIdString, authRequestData)

        doReturn(Flux.just(transactionEvent1, transactionEvent2))
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        val mockTransaction = Mockito.mock(BaseTransaction::class.java)

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // When
        val result = transactionEventServiceSpy.getTransaction(transactionIdString)

        // Then
        StepVerifier.create(result)
            .assertNext { transaction ->
                assertNotNull(transaction)
                assertSame(mockTransaction, transaction)
            }
            .verifyComplete()

        // Verify repository calls
        verify(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)
    }

    @Test
    fun `resendUserReceiptNotification should resend notification for transaction in correct state`() {
        // Given
        val mockTransaction = Mockito.mock(BaseTransaction::class.java)
        val mockTransactionId = Mockito.mock(TransactionId::class.java)

        doReturn(mockTransactionId).`when`(mockTransaction).transactionId
        doReturn(transactionIdString).`when`(mockTransactionId).value()
        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .getTransaction(transactionIdString)

        val existingReceiptData = TransactionUserReceiptData()
        val existingReceiptEvent =
            TransactionUserReceiptRequestedEvent(transactionIdString, existingReceiptData)

        doReturn(Flux.just(existingReceiptEvent))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)

        doReturn(Mono.just(existingReceiptEvent))
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        val mockTx = Mockito.mock(Transaction::class.java)
        doReturn(Mono.just(mockTx))
            .`when`(transactionsViewRepository)
            .findByTransactionId(transactionIdString)
        doReturn(Mono.just(mockTx)).`when`(transactionsViewRepository).save(any())

        // When
        val result = transactionEventServiceSpy.resendUserReceiptNotification(transactionIdString)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionIdString, event?.transactionId)
                assertEquals(
                    TransactionUserReceiptData.NotificationTrigger.MANUAL,
                    event?.data?.notificationTrigger
                )
                assertEquals(existingReceiptData.responseOutcome, event?.data?.responseOutcome)
                assertEquals(existingReceiptData.language, event?.data?.language)
                assertEquals(existingReceiptData.paymentDate, event?.data?.paymentDate)
            }
            .verifyComplete()

        // Verify repository calls
        verify(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionIdString)
        verify(userReceiptEventStoreRepository).save(any())
    }

    @Test
    fun `resendUserReceiptNotification should throw InvalidTransactionStatusException for transaction not in a valid state`() {
        // Given
        val mockTransaction = Mockito.mock<BaseTransaction>()
        whenever(mockTransaction.status).thenReturn(TransactionStatusDto.CLOSED)

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .whenever(transactionEventServiceSpy)
            .getTransaction(transactionIdString)

        // When
        val result = transactionEventServiceSpy.resendUserReceiptNotification(transactionIdString)

        // Then
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is InvalidTransactionStatusException &&
                    error.message?.contains(
                        "Cannot resend user receipt notification for transaction in state: CLOSED"
                    ) == true
            }
            .verify()

        verify(userReceiptEventStoreRepository, never()).save(any())
        verify(transactionsViewRepository, never()).save(any())
    }

    @Test
    fun `resendUserReceiptNotification should return error when transaction in wrong state`() {
        // Given
        val mockTransaction = Mockito.mock(BaseTransaction::class.java)

        doReturn(TransactionStatusDto.CLOSED).`when`(mockTransaction).status // Wrong state

        val transactionEventServiceSpy = spy(transactionEventService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionEventServiceSpy)
            .getTransaction(transactionIdString)

        // When
        val result = transactionEventServiceSpy.resendUserReceiptNotification(transactionIdString)

        // Then
        StepVerifier.create(result)
            .expectError(InvalidTransactionStatusException::class.java)
            .verify()

        verify(userReceiptEventStoreRepository, never())
            .findByTransactionIdOrderByCreationDateAsc(any())
        verify(userReceiptEventStoreRepository, never()).save(any())
    }
}
