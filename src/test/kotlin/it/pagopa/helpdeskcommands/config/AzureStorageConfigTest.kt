package it.pagopa.helpdeskcommands.config

import com.azure.core.util.serializer.JsonSerializer
import com.azure.core.util.serializer.TypeReference
import com.azure.storage.queue.QueueAsyncClient as AzureQueueAsyncClient
import com.azure.storage.queue.models.SendMessageResult
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.helpdeskcommands.client.AzureApiQueueClient
import it.pagopa.helpdeskcommands.config.properties.QueueConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class AzureStorageConfigTest {

    @Mock private lateinit var azureApiQueueClient: AzureApiQueueClient

    @Mock private lateinit var azureQueueAsyncClient: AzureQueueAsyncClient

    @Mock private lateinit var queueConfig: QueueConfig

    private lateinit var azureStorageConfigNativeEnabled: AzureStorageConfig
    private lateinit var azureStorageConfigNativeDisabled: AzureStorageConfig

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        azureStorageConfigNativeEnabled = AzureStorageConfig(isNativeClientEnabled = true)
        azureStorageConfigNativeDisabled = AzureStorageConfig(isNativeClientEnabled = false)

        whenever(queueConfig.storageConnectionString)
            .thenReturn(
                "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;QueueEndpoint=http://localhost:10001/devstoreaccount1"
            )
        whenever(queueConfig.transactionRefundQueueName).thenReturn("refund-queue")
        whenever(queueConfig.transactionNotificationRequestedQueueName)
            .thenReturn("notification-queue")
        whenever(azureQueueAsyncClient.queueName).thenReturn("test-queue")
        whenever(azureQueueAsyncClient.queueUrl)
            .thenReturn("http://localhost:10001/devstoreaccount1/test-queue")
    }

    @Test
    fun `jsonSerializerV2 should create TracingInfoReplacingJsonSerializer`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()

        assertNotNull(jsonSerializer)
        assertTrue(
            jsonSerializer.javaClass.simpleName.contains("TracingInfoReplacingJsonSerializer")
        )
    }

    @Test
    fun `TracingInfoReplacingJsonSerializer should handle serialization with null tracingInfo`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val event = createTestQueueEvent()

        val serializedBytes = jsonSerializer.serializeToBytes(event)
        val serializedString = String(serializedBytes, Charsets.UTF_8)

        assertFalse(serializedString.contains("\"tracingInfo\":null"))
        assertTrue(serializedString.contains("\"tracingInfo\""))
        assertTrue(serializedString.contains("\"traceparent\""))
    }

    @Test
    fun `TracingInfoReplacingJsonSerializer should handle serialization with existing tracingInfo`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val testObject =
            mapOf("data" to "test", "tracingInfo" to mapOf("traceparent" to "existing-traceparent"))

        val serializedBytes = jsonSerializer.serializeToBytes(testObject)
        val serializedString = String(serializedBytes, Charsets.UTF_8)

        assertTrue(serializedString.contains("\"tracingInfo\""))
        assertTrue(serializedString.contains("existing-traceparent"))
        assertFalse(serializedString.contains("\"tracingInfo\":null"))
    }

    @Test
    fun `TracingInfoReplacingJsonSerializer should handle serialize to stream`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val event = createTestQueueEvent()
        val outputStream = ByteArrayOutputStream()

        jsonSerializer.serialize(outputStream, event)

        val serializedString = outputStream.toString(Charsets.UTF_8)
        assertFalse(serializedString.contains("\"tracingInfo\":null"))
        assertTrue(serializedString.contains("\"tracingInfo\""))
    }

    @Test
    fun `TracingInfoReplacingJsonSerializer should handle serializeAsync`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val event = createTestQueueEvent()
        val outputStream = ByteArrayOutputStream()

        StepVerifier.create(jsonSerializer.serializeAsync(outputStream, event)).verifyComplete()

        val serializedString = outputStream.toString(Charsets.UTF_8)
        assertFalse(serializedString.contains("\"tracingInfo\":null"))
    }

    @Test
    fun `TracingInfoReplacingJsonSerializer should handle serializeToBytesAsync`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val event = createTestQueueEvent()

        StepVerifier.create(jsonSerializer.serializeToBytesAsync(event))
            .assertNext { bytes ->
                val serializedString = String(bytes, Charsets.UTF_8)
                assertFalse(serializedString.contains("\"tracingInfo\":null"))
                assertTrue(serializedString.contains("\"tracingInfo\""))
            }
            .verifyComplete()
    }

    @Test
    fun `TracingInfoReplacingJsonSerializer should handle deserialize with simple data`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val simpleJson = "{\"test\": \"value\"}"
        val inputStream = ByteArrayInputStream(simpleJson.toByteArray())

        val result =
            jsonSerializer.deserialize(
                inputStream,
                object : TypeReference<Map<String, String>>() {}
            )

        assertNotNull(result)
        assertEquals("value", result["test"])
    }

    @Test
    fun `TracingInfoReplacingJsonSerializer should handle deserializeAsync with simple data`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val simpleJson = "{\"test\": \"value\"}"
        val inputStream = ByteArrayInputStream(simpleJson.toByteArray())

        StepVerifier.create(
                jsonSerializer.deserializeAsync(
                    inputStream,
                    object : TypeReference<Map<String, String>>() {}
                )
            )
            .assertNext { result ->
                assertNotNull(result)
                assertEquals("value", result["test"])
            }
            .verifyComplete()
    }

    @Test
    fun `transactionRefundQueueAsyncClient should create QueueAsyncClient`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()

        val client =
            azureStorageConfigNativeEnabled.transactionRefundQueueAsyncClient(
                queueConfig,
                jsonSerializer,
                azureApiQueueClient
            )

        assertNotNull(client)
    }

    @Test
    fun `transactionNotificationQueueAsyncClient should create QueueAsyncClient`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()

        val client =
            azureStorageConfigNativeEnabled.transactionNotificationQueueAsyncClient(
                queueConfig,
                jsonSerializer,
                azureApiQueueClient
            )

        assertNotNull(client)
    }

    @Test
    fun `createNativeCompatibleQueueClient should return standard client when native disabled`() {
        val jsonSerializer = azureStorageConfigNativeDisabled.jsonSerializerV2()

        val client =
            azureStorageConfigNativeDisabled.transactionRefundQueueAsyncClient(
                queueConfig,
                jsonSerializer,
                azureApiQueueClient
            )

        assertNotNull(client)
    }

    @Test
    fun `native client should handle sendMessageWithResponse success case`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val credentials = AzureApiQueueClient.StorageCredentials("testAccount", "testKey")

        whenever(azureApiQueueClient.parseConnectionString(any()))
            .thenReturn(Mono.just(credentials))
        whenever(azureApiQueueClient.sendMessageWithStorageKey(any(), any(), any(), any(), any()))
            .thenReturn(Mono.just("Success"))

        val client =
            azureStorageConfigNativeEnabled.transactionRefundQueueAsyncClient(
                queueConfig,
                jsonSerializer,
                azureApiQueueClient
            )

        val event = createTestQueueEvent()

        StepVerifier.create(
                client.sendMessageWithResponse(event, Duration.ofSeconds(30), Duration.ofHours(1))
            )
            .assertNext { response ->
                assertEquals(201, response.statusCode)
                assertNotNull(response.value)
                assertTrue(response.value is SendMessageResult)
            }
            .verifyComplete()
    }

    @Test
    fun `native client should handle sendMessageWithResponse error case`() {
        val jsonSerializer = azureStorageConfigNativeEnabled.jsonSerializerV2()
        val testException = RuntimeException("Test error")

        whenever(azureApiQueueClient.parseConnectionString(any()))
            .thenReturn(Mono.error(testException))

        val client =
            azureStorageConfigNativeEnabled.transactionRefundQueueAsyncClient(
                queueConfig,
                jsonSerializer,
                azureApiQueueClient
            )

        val event = createTestQueueEvent()

        StepVerifier.create(
                client.sendMessageWithResponse(event, Duration.ofSeconds(30), Duration.ofHours(1))
            )
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `native client should handle sendMessageWithResponse serialization error case`() {
        val mockBadSerializer = mock<JsonSerializer>()

        whenever(mockBadSerializer.serializeToBytes(any()))
            .thenThrow(RuntimeException("Serialization error"))

        val config = AzureStorageConfig(isNativeClientEnabled = true)
        val client =
            config.transactionRefundQueueAsyncClient(
                queueConfig,
                mockBadSerializer,
                azureApiQueueClient
            )

        val event = createTestQueueEvent()

        StepVerifier.create(
                client.sendMessageWithResponse(event, Duration.ofSeconds(30), Duration.ofHours(1))
            )
            .expectError(RuntimeException::class.java)
            .verify()
    }

    private fun createTestQueueEvent(): QueueEvent<TransactionRefundRequestedEvent> {
        val transactionId = "93cce28d3b7c4cb9975e6d856ecee89f"
        val refundRequestedData =
            TransactionRefundRequestedData(
                null,
                TransactionStatusDto.CLOSED,
                TransactionRefundRequestedData.RefundTrigger.MANUAL
            )
        val refundRequestedEvent =
            TransactionRefundRequestedEvent(transactionId, refundRequestedData)

        return QueueEvent(refundRequestedEvent, null)
    }
}
