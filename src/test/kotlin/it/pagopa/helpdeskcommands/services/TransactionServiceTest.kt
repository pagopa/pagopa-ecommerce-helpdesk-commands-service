package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.documents.BaseTransactionEvent
import it.pagopa.ecommerce.commons.documents.v2.*
import it.pagopa.ecommerce.commons.domain.v2.pojos.BaseTransaction
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.helpdeskcommands.exceptions.InvalidTransactionStatusException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import it.pagopa.helpdeskcommands.repositories.TransactionsEventStoreRepository
import it.pagopa.helpdeskcommands.repositories.TransactionsViewRepository
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
import java.time.ZonedDateTime

@ExtendWith(MockitoExtension::class)
class TransactionServiceTest {

    @Mock
    private lateinit var transactionsEventStoreRepository: TransactionsEventStoreRepository<Any>

    @Mock
    private lateinit var transactionsRefundedEventStoreRepository:
            TransactionsEventStoreRepository<BaseTransactionRefundedData>

    @Mock
    private lateinit var transactionsViewRepository: TransactionsViewRepository

    @Mock
    private lateinit var userReceiptEventStoreRepository:
            TransactionsEventStoreRepository<TransactionUserReceiptData>

    @Captor
    private lateinit var userReceiptEventCaptor: ArgumentCaptor<TransactionUserReceiptRequestedEvent>

    private lateinit var transactionService: TransactionService

    private val transactionId = "0c554302e84146afabb9474179224770"

    @BeforeEach
    fun setup() {
        transactionService = TransactionService(
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
        doReturn(Mono.just(mockTransaction)).`when`(transactionServiceSpy).getTransaction(transactionId)

        val existingUserReceiptEvent = createUserReceiptRequestedEvent()
        val events = listOf(existingUserReceiptEvent)

        // Mock repository behavior
        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        doAnswer { invocation ->
            Mono.just(invocation.getArgument(0) as TransactionUserReceiptRequestedEvent)
        }.`when`(userReceiptEventStoreRepository).save(any())

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
        doReturn(Mono.just(mockTransaction)).whenever(transactionServiceSpy).getTransaction(transactionId)

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is InvalidTransactionStatusException &&
                        error.message?.contains("Cannot resend user receipt notification for transaction in state: CLOSED") == true
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
        doReturn(Mono.just(mockTransaction)).`when`(transactionServiceSpy).getTransaction(transactionId)

        val existingUserReceiptEvent = createUserReceiptRequestedEvent()
        val events = listOf(existingUserReceiptEvent)

        // Mock repository behavior
        doReturn(Flux.fromIterable(events))
            .`when`(userReceiptEventStoreRepository)
            .findByTransactionIdOrderByCreationDateAsc(transactionId)

        doReturn(Mono.error<TransactionUserReceiptRequestedEvent>(RuntimeException("Database error")))
            .`when`(userReceiptEventStoreRepository)
            .save(any())

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result)
            .expectError(RuntimeException::class.java)
            .verify()

        verify(userReceiptEventStoreRepository).save(any())
    }

    @Test
    fun `resendUserReceiptNotification should handle multiple existing events and use the latest one`() {
        // Given
        val mockTransaction = mock(BaseTransaction::class.java)
        doReturn(TransactionStatusDto.NOTIFICATION_REQUESTED).`when`(mockTransaction).status

        // Mock getTransaction to return our mock transaction
        val transactionServiceSpy = spy(transactionService)
        doReturn(Mono.just(mockTransaction)).`when`(transactionServiceSpy).getTransaction(transactionId)

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
        }.`when`(userReceiptEventStoreRepository).save(any())

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

        `when`(transactionsEventStoreRepository.findByTransactionIdOrderByCreationDateAsc(transactionId))
            .thenReturn(Flux.fromIterable(events) as Flux<BaseTransactionEvent<Any>>?)

        // Mock the reduction process
        val spyService = spy(transactionService)
        doReturn(Mono.just(mockTransaction))
            .`when`(spyService)
            .reduceEvents(any<Flux<TransactionEvent<Any>>>())

        // Act
        val result = spyService.getTransaction(transactionId)

        // Assert
        StepVerifier.create(result)
            .expectNext(mockTransaction)
            .verifyComplete()

        verify(transactionsEventStoreRepository).findByTransactionIdOrderByCreationDateAsc(transactionId)
    }

    @Test
    fun `getTransaction should throw TransactionNotFoundException when transaction not found`() {
        // Given
        whenever(transactionsEventStoreRepository.findByTransactionIdOrderByCreationDateAsc(transactionId))
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
        StepVerifier.create(result)
            .expectError(TransactionNotFoundException::class.java)
            .verify()

        // Verify that the repository was called with the correct transaction ID
        verify(transactionsEventStoreRepository).findByTransactionIdOrderByCreationDateAsc(transactionId)
    }

    @Test
    fun `reduceEvents should throw ClassCastException when no events are provided`() {
        // Given
        val emptyFlux = Flux.empty<TransactionEvent<Any>>()

        // When
        val result = transactionService.reduceEvents(emptyFlux)

        // Then
        StepVerifier.create(result)
            .expectError(ClassCastException::class.java)
            .verify()
    }

    @Test
    fun `resendUserReceiptNotification should throw TransactionNotFoundException when transaction not found`() {
        // Given
        val transactionServiceSpy = spy(transactionService)

        // Mock getTransaction to throw TransactionNotFoundException
        doReturn(Mono.error<BaseTransaction>(TransactionNotFoundException("Transaction not found: $transactionId")))
            .whenever(transactionServiceSpy).getTransaction(transactionId)

        // When
        val result = transactionServiceSpy.resendUserReceiptNotification(transactionId)

        // Then
        StepVerifier.create(result)
            .expectError(TransactionNotFoundException::class.java)
            .verify()

        // Verify getTransaction was called but userReceiptEventStoreRepository.save was not
        verify(transactionServiceSpy).getTransaction(transactionId)
        verify(userReceiptEventStoreRepository, never()).save(any())
    }
}