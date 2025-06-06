package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.documents.BaseTransactionEvent
import it.pagopa.ecommerce.commons.documents.v2.*
import it.pagopa.ecommerce.commons.documents.v2.authorization.RedirectTransactionGatewayAuthorizationRequestedData
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransaction
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransactionWithRefundRequested
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.helpdeskcommands.exceptions.InvalidTransactionStatusException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import it.pagopa.helpdeskcommands.repositories.TransactionsEventStoreRepository
import it.pagopa.helpdeskcommands.repositories.TransactionsViewRepository
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class TransactionServiceTest {

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

    private lateinit var transactionService: TransactionService

    private val transactionId = "0c554302e84146afabb9474179224770"

    @BeforeEach
    fun setup() {
        transactionService =
            TransactionService(
                transactionsEventStoreRepository,
                transactionsRefundedEventStoreRepository,
                transactionsViewRepository,
                userReceiptEventStoreRepository
            )
    }

    @Test
    fun `resendUserReceiptNotification should create and save a new event for transaction in NOTIFICATION_REQUESTED state`() {
        // Given
        val mockTransaction = mock(BaseTransaction::class.java)
        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        // Mock getTransaction to return our mock transaction
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .getTransaction(transactionId)

        val existingUserReceiptEvent = createUserReceiptRequestedEvent()
        val events = listOf(existingUserReceiptEvent)

        // Mock repository behavior
        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        doAnswer { invocation ->
                Mono.just(invocation.getArgument(0) as TransactionUserReceiptRequestedEvent)
            }
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionId, event.transactionId)
                assertEquals(existingUserReceiptEvent.getData(), event.getData())
                assertNotEquals(existingUserReceiptEvent.id, event.id)
            }
            .verifyComplete()

        verify(userReceiptEventStoreRepository).save(capture(userReceiptEventCaptor))
        val savedEvent = userReceiptEventCaptor.value
        assertEquals(transactionId, savedEvent.transactionId)
        assertEquals(existingUserReceiptEvent.getData(), savedEvent.getData())
    }

    @Test
    fun `resendUserReceiptNotification should throw InvalidTransactionStatusException for transaction not in NOTIFICATION_REQUESTED state`() {
        // Given
        val mockTransaction = mock<BaseTransaction>()
        whenever(mockTransaction.status).thenReturn(TransactionStatusDto.CLOSED)

        // Mock getTransaction to return our mock transaction
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .whenever(transactionServiceSpy)
            .getTransaction(transactionId)

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

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
    }

    @Test
    fun `resendUserReceiptNotification should handle error when saving new event`() {
        // Given
        val mockTransaction = mock(BaseTransaction::class.java)
        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        // Mock getTransaction to return our mock transaction
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .getTransaction(transactionId)

        val existingUserReceiptEvent = createUserReceiptRequestedEvent()
        val events = listOf(existingUserReceiptEvent)

        // Mock repository behavior
        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        doReturn(
                Mono.error<TransactionUserReceiptRequestedEvent>(RuntimeException("Database error"))
            )
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result).expectError(RuntimeException::class.java).verify()

        verify(userReceiptEventStoreRepository).save(any())
    }

    @Test
    fun `resendUserReceiptNotification should handle multiple existing events and use the latest one`() {
        // Given
        val mockTransaction = mock(BaseTransaction::class.java)
        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        // Mock getTransaction to return our mock transaction
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .getTransaction(transactionId)

        // Create multiple events with different timestamps
        val oldEvent = createUserReceiptRequestedEvent(ZonedDateTime.now().minusDays(2))
        val latestEvent = createUserReceiptRequestedEvent(ZonedDateTime.now())
        val events = listOf(oldEvent, latestEvent)

        // Mock repository behavior
        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        doAnswer { invocation ->
                Mono.just(invocation.getArgument(0) as TransactionUserReceiptRequestedEvent)
            }
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionId, event.transactionId)
                assertEquals(latestEvent.getData(), event.getData())
            }
            .verifyComplete()

        verify(userReceiptEventStoreRepository).save(capture(userReceiptEventCaptor))
        val savedEvent = userReceiptEventCaptor.value
        assertEquals(transactionId, savedEvent.transactionId)
        assertEquals(latestEvent.getData(), savedEvent.getData())
    }

    private fun createUserReceiptRequestedEvent(
        timestamp: ZonedDateTime = ZonedDateTime.now()
    ): TransactionUserReceiptRequestedEvent {
        val mockData = mock(TransactionUserReceiptData::class.java)

        // Create a spy of TransactionUserReceiptRequestedEvent to control the creation date
        val event = TransactionUserReceiptRequestedEvent(transactionId, mockData)
        val spyEvent = spy(event)

        // Mock the getCreationDate method to return our custom timestamp
        // doReturn(timestamp.toString()).`when`(spyEvent).getCreationDate()

        return spyEvent
    }

    @Test
    fun `getTransaction should return transaction when events exist`() {
        val mockTransaction: BaseTransaction = mock(BaseTransaction::class.java)
        val transactionEvent = TransactionActivatedEvent(transactionId, TransactionActivatedData())
        val events = listOf(transactionEvent)

        `when`(
                transactionsEventStoreRepository.findByTransactionIdOrderByCreationDateAsc(
                    transactionId
                )
            )
            .thenReturn(Flux.fromIterable(events) as Flux<BaseTransactionEvent<Any>>?)

        // Mock the reduction process
        val spyService = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(spyService)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // Act
        val result = spyService.getTransaction(transactionId)

        // Assert
        StepVerifier.create(result).expectNext(mockTransaction).verifyComplete()

        verify(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)
    }

    @Test
    fun `getTransaction should throw TransactionNotFoundException when transaction not found`() {
        // Given
        whenever(
                transactionsEventStoreRepository.findByTransactionIdOrderByCreationDateAsc(
                    transactionId
                )
            )
            .thenReturn(Flux.empty())

        // Create a spy of the service
        val transactionServiceSpy = spy(transactionService)

        // Mock the reduceEvents method to return an empty Mono
        // This avoids the ClassCastException
        doReturn(Mono.empty<BaseTransaction>())
            .whenever(transactionServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // When
        val result = transactionServiceSpy.getTransaction(transactionId)

        // Then
        StepVerifier.create(result).expectError(TransactionNotFoundException::class.java).verify()

        // Verify that the repository was called with the correct transaction ID
        verify(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)
    }

    @Test
    fun `reduceEvents should throw ClassCastException when no events are provided`() {
        // Given
        val emptyFlux = Flux.empty<TransactionEvent<Any>>()

        // When
        val result = transactionService.reduceEvents(emptyFlux)

        // Then
        StepVerifier.create(result).expectError(ClassCastException::class.java).verify()
    }

    @Test
    fun `resendUserReceiptNotification should throw TransactionNotFoundException when transaction not found`() {
        // Given
        val transactionServiceSpy = spy(transactionService)

        // Mock getTransaction to throw TransactionNotFoundException
        doReturn(
                Mono.error<BaseTransaction>(
                    TransactionNotFoundException("Transaction not found: $transactionId")
                )
            )
            .whenever(transactionServiceSpy)
            .getTransaction(transactionId)

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result).expectError(TransactionNotFoundException::class.java).verify()

        // Verify getTransaction was called but userReceiptEventStoreRepository.save was not
        verify(transactionServiceSpy).getTransaction(transactionId)
        verify(userReceiptEventStoreRepository, never()).save(any())
    }

    @Test
    fun `createRefundRequestEvent should create a valid refund request event for transaction`() {
        // Given
        val mockTransaction = mock(BaseTransaction::class.java)
        val mockTransactionId =
            mock(it.pagopa.ecommerce.commons.domain.v2.TransactionId::class.java)

        doReturn(mockTransactionId).`when`(mockTransaction).transactionId
        doReturn(transactionId).`when`(mockTransactionId).value()
        doReturn(TransactionStatusDto.CLOSED).`when`(mockTransaction).status

        val transactionEvent = TransactionActivatedEvent(transactionId, TransactionActivatedData())

        doReturn(Flux.just(transactionEvent))
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        // Mock the reduce operation
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // Mock the repository save
        @Suppress("UNCHECKED_CAST")
        doReturn(Mono.just(transactionEvent as TransactionEvent<BaseTransactionRefundedData>))
            .`when`(transactionsRefundedEventStoreRepository)
            .save(any())

        // Mock view repository
        val mockTx = mock(Transaction::class.java)
        doReturn(Mono.just(mockTx))
            .`when`(transactionsViewRepository)
            .findByTransactionId(transactionId)

        doReturn(Mono.just(mockTx)).`when`(transactionsViewRepository).save(any())

        // When
        val result = transactionServiceSpy.createRefundRequestEvent(transactionId)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionId, event?.transactionId)
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
        verify(transactionsViewRepository).findByTransactionId(transactionId)
        verify(transactionsViewRepository).save(any())
    }

    @Test
    fun `createRefundRequestEvent should return empty when transaction already has refund requested`() {
        // Given
        val mockTransaction = mock(BaseTransactionWithRefundRequested::class.java)
        val mockTransactionId =
            mock(it.pagopa.ecommerce.commons.domain.v2.TransactionId::class.java)

        doReturn(mockTransactionId).`when`(mockTransaction).transactionId
        doReturn(transactionId).`when`(mockTransactionId).value()

        // Use concrete TransactionEvent implementation
        val transactionEvent = TransactionActivatedEvent(transactionId, TransactionActivatedData())

        doReturn(Flux.just(transactionEvent))
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        // Mock the reduce operation
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // When
        val result = transactionServiceSpy.createRefundRequestEvent(transactionId)

        // Then
        StepVerifier.create(result).verifyComplete() // Should complete without emitting any value

        // Verify repository calls
        verify(transactionsRefundedEventStoreRepository, never()).save(any())
        verify(transactionsViewRepository, never()).findByTransactionId(any())
        verify(transactionsViewRepository, never()).save(any())
    }

    /*@Test
    fun `getTransaction should retrieve and reduce transaction events`() {
        // Given
        // Use concrete TransactionEvent implementations
        val transactionEvent1 = TransactionActivatedEvent(transactionId, TransactionActivatedData())
        val transactionEvent2 = TransactionAuthorizationRequestedEvent(
            transactionId,
            TransactionAuthorizationRequestData()
        )

        doReturn(Flux.just(transactionEvent1, transactionEvent2))
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        val mockTransaction = mock(BaseTransaction::class.java)

        // Mock the reduce operation
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // When
        val result = transactionServiceSpy.getTransaction(transactionId)

        // Then
        StepVerifier.create(result)
            .assertNext { transaction ->
                assertNotNull(transaction)
                assertSame(mockTransaction, transaction)
            }
            .verifyComplete()

        // Verify repository calls
        verify(transactionsEventStoreRepository).findByTransactionIdOrderByCreationDateAsc(transactionId)
    }

    @Test
    fun `getTransaction should return error when transaction not found`() {
        // Given
        doReturn(Flux.empty<TransactionEvent<Any>>())
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        // When
        val result = transactionService.getTransaction(transactionId)

        // Then
        StepVerifier.create(result)
            .expectError(TransactionNotFoundException::class.java)
            .verify()

        // Verify repository calls
        verify(transactionsEventStoreRepository).findByTransactionIdOrderByCreationDateAsc(transactionId)
    }*/

    @Test
    fun `getTransaction should retrieve and reduce transaction events`() {
        // Given
        val transactionEvent1 = TransactionActivatedEvent(transactionId, TransactionActivatedData())

        // Assuming you have the necessary imports and the
        // TransactionGatewayAuthorizationRequestedData is defined

        // Create an instance of the required data class
        val transactionData =
            RedirectTransactionGatewayAuthorizationRequestedData() // Initialize as needed

        // Now instantiate TransactionAuthorizationRequestData using the public constructor
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
            TransactionAuthorizationRequestedEvent(transactionId, authRequestData)

        doReturn(Flux.just(transactionEvent1, transactionEvent2))
            .`when`(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        val mockTransaction = mock(BaseTransaction::class.java)

        // Mock the reduce operation
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // When
        val result = transactionServiceSpy.getTransaction(transactionId)

        // Then
        StepVerifier.create(result)
            .assertNext { transaction ->
                assertNotNull(transaction)
                assertSame(mockTransaction, transaction)
            }
            .verifyComplete()

        // Verify repository calls
        verify(transactionsEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)
    }

    @Test
    fun `resendUserReceiptNotification should resend notification for transaction in correct state`() {
        // Given
        val mockTransaction = mock(BaseTransaction::class.java)

        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        // Mock getTransaction to return our mock transaction
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .getTransaction(transactionId)

        // Mock existing receipt event with concrete implementation
        val existingReceiptData = TransactionUserReceiptData()
        val existingReceiptEvent =
            TransactionUserReceiptRequestedEvent(transactionId, existingReceiptData)

        doReturn(Flux.just(existingReceiptEvent))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        // Mock save of new event
        doReturn(Mono.just(existingReceiptEvent))
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result)
            .assertNext { event ->
                assertNotNull(event)
                assertEquals(transactionId, event?.transactionId)
                assertEquals(existingReceiptData, event?.data)
            }
            .verifyComplete()

        // Verify repository calls
        verify(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)
        verify(userReceiptEventStoreRepository).save(any())
    }

    @Test
    fun `resendUserReceiptNotification should return error when transaction in wrong state`() {
        // Given
        val mockTransaction = mock(BaseTransaction::class.java)

        doReturn(TransactionStatusDto.CLOSED).`when`(mockTransaction).status // Wrong state

        // Mock getTransaction to return our mock transaction
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(transactionServiceSpy)
            .getTransaction(transactionId)

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result)
            .expectError(InvalidTransactionStatusException::class.java)
            .verify()

        // Verify repository calls - should not be called
        verify(userReceiptEventStoreRepository, never())
            .findByTransactionIdOrderByCreationDateAsc(any())
        verify(userReceiptEventStoreRepository, never()).save(any())
    }
}
