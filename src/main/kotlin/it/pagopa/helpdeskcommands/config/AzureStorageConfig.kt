package it.pagopa.helpdeskcommands.config

import com.azure.core.http.HttpHeaders
import com.azure.core.http.HttpRequest
import com.azure.core.http.rest.Response
import com.azure.core.util.serializer.JsonSerializer
import com.azure.core.util.serializer.TypeReference
import com.azure.storage.queue.QueueAsyncClient as AzureQueueAsyncClient
import com.azure.storage.queue.QueueClientBuilder
import com.azure.storage.queue.models.QueueStorageException
import com.azure.storage.queue.models.SendMessageResult
import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.documents.BaseTransactionEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.ecommerce.commons.queues.StrictJsonSerializerProvider
import it.pagopa.ecommerce.commons.queues.TracingInfo
import it.pagopa.ecommerce.commons.queues.mixin.serialization.v2.QueueEventMixInClassFieldDiscriminator
import it.pagopa.helpdeskcommands.client.AzureApiQueueClient
import it.pagopa.helpdeskcommands.config.properties.QueueConfig
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.filter.AssignableTypeFilter
import reactor.core.publisher.Mono

@Configuration
@EnableConfigurationProperties(QueueConfig::class)
class AzureStorageConfig(
    @Value("\${azurestorage.queues.nativeClient.enabled}")
    private val isNativeClientEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(AzureStorageConfig::class.java)

    /**
     * Custom JsonSerializer wrapper that replaces null tracingInfo with valid TracingInfo during
     * serialization. This ensures consumer compatibility without modifying the consumer's strict
     * null checking.
     */
    private class TracingInfoReplacingJsonSerializer(private val delegate: JsonSerializer) :
        JsonSerializer {

        override fun <T> deserialize(stream: InputStream, typeReference: TypeReference<T>): T {
            return delegate.deserialize(stream, typeReference)
        }

        override fun <T> deserializeAsync(
            stream: InputStream,
            typeReference: TypeReference<T>,
        ): Mono<T> {
            return delegate.deserializeAsync(stream, typeReference)
        }

        override fun serialize(stream: OutputStream, value: Any) {
            val jsonString = String(delegate.serializeToBytes(value), Charsets.UTF_8)
            val modifiedJson = replaceNullTracingInfo(jsonString)
            stream.write(modifiedJson.toByteArray(Charsets.UTF_8))
        }

        override fun serializeAsync(stream: OutputStream, value: Any): Mono<Void> {
            return Mono.fromRunnable { serialize(stream, value) }
        }

        override fun serializeToBytes(value: Any): ByteArray {
            val jsonString = String(delegate.serializeToBytes(value), Charsets.UTF_8)
            val modifiedJson = replaceNullTracingInfo(jsonString)
            return modifiedJson.toByteArray(Charsets.UTF_8)
        }

        override fun serializeToBytesAsync(value: Any): Mono<ByteArray> {
            return Mono.fromCallable { serializeToBytes(value) }
        }

        private fun replaceNullTracingInfo(jsonString: String): String {
            if (!jsonString.contains("\"tracingInfo\":null")) {
                return jsonString
            }

            val traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 32)
            val spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            val traceparent = "00-$traceId-$spanId-00"

            val tracingInfoReplacement =
                """
                "tracingInfo": {
                  "traceparent": "$traceparent",
                  "tracestate": null,
                  "baggage": null
                }
            """
                    .trimIndent()

            return jsonString.replace("\"tracingInfo\":null", tracingInfoReplacement)
        }
    }

    /**
     * Creates a JSON serializer configured with V2 queue event mixins for proper event
     * deserialization. Registers all TransactionEvent classes for native compilation.
     *
     * @return Configured StrictJsonSerializerProvider instance with QueueEvent mixins
     */
    @Bean
    @RegisterReflectionForBinding(
        QueueEvent::class,
        TracingInfo::class,
        QueueEventMixin::class,
        QueueEventMixInClassFieldDiscriminator::class,
        StrictJsonSerializerProvider::class,
        TransactionRefundRequestedEvent::class,
        TransactionUserReceiptRequestedEvent::class,
        TransactionRefundRequestedData::class,
        TransactionUserReceiptData::class,
        QueueStorageException::class,
        ClassPathScanningCandidateComponentProvider::class,
        AssignableTypeFilter::class,
    )
    fun jsonSerializerV2(): JsonSerializer {
        logger.info("Creating JsonSerializer for Azure Storage Queue with custom V2 mixin")
        val provider =
            StrictJsonSerializerProvider()
                .addMixIn(
                    QueueEvent::class.java,
                    QueueEventMixInClassFieldDiscriminator::class.java,
                )

        val baseSerializer = provider.createInstance()
        return TracingInfoReplacingJsonSerializer(baseSerializer)
    }

    /**
     * Creates a standard QueueAsyncClient using Azure SDK (bypassing AzureApiQueueClient for
     * testing)
     */
    private fun createNativeCompatibleQueueClient(
        azureQueueClient: AzureQueueAsyncClient,
        jsonSerializer: JsonSerializer,
        azureApiQueueClient: AzureApiQueueClient,
        queueConfig: QueueConfig,
    ): QueueAsyncClient {
        logger.info("Using client based on isNativeClientEnabled = {}", isNativeClientEnabled)
        return if (isNativeClientEnabled) {
            return object : QueueAsyncClient(azureQueueClient, jsonSerializer) {
                override fun <T : BaseTransactionEvent<*>> sendMessageWithResponse(
                    event: QueueEvent<T>,
                    visibilityTimeout: Duration?,
                    timeToLive: Duration?,
                ): Mono<Response<SendMessageResult>> {
                    return try {
                        val jsonBytes = jsonSerializer.serializeToBytes(event)
                        val jsonString = String(jsonBytes, Charsets.UTF_8)

                        val queueName = azureQueueClient.queueName
                        val queueUrl = azureQueueClient.queueUrl

                        azureApiQueueClient
                            .parseConnectionString(queueConfig.storageConnectionString)
                            .flatMap { credentials ->
                                azureApiQueueClient.sendMessageWithStorageKey(
                                    queueUrl,
                                    queueName,
                                    jsonString,
                                    credentials.accountName,
                                    credentials.accountKey,
                                )
                            }
                            .map { response -> createMockSendMessageResponse() }
                    } catch (e: Exception) {
                        logger.error("Azure API queue HTTP client error: {}", e.message)
                        Mono.error(e)
                    }
                }
            }
        } else {
            QueueAsyncClient(azureQueueClient, jsonSerializer)
        }
    }

    /**
     * Creates a reactive queue client for handling transaction refund events.
     *
     * @param queueConfig Configuration properties containing queue connection details
     * @param jsonSerializerV2 Serializer for marshalling queue messages
     * @return configured QueueAsyncClient
     */
    @Bean
    @Qualifier("transactionRefundQueueAsyncClient")
    fun transactionRefundQueueAsyncClient(
        queueConfig: QueueConfig,
        jsonSerializerV2: JsonSerializer,
        azureApiQueueClient: AzureApiQueueClient,
    ): QueueAsyncClient {
        val queueName = queueConfig.transactionRefundQueueName
        return buildQueueAsyncClient(
            queueConfig.storageConnectionString,
            queueName,
            jsonSerializerV2,
            queueConfig,
            azureApiQueueClient,
        )
    }

    /**
     * Creates a reactive queue client for handling transaction notification requests.
     *
     * @param queueConfig Configuration properties containing queue connection details
     * @param jsonSerializerV2 Serializer for marshalling queue messages
     * @return configured QueueAsyncClient
     */
    @Bean
    @Qualifier("transactionNotificationQueueAsyncClient")
    fun transactionNotificationQueueAsyncClient(
        queueConfig: QueueConfig,
        jsonSerializerV2: JsonSerializer,
        azureApiQueueClient: AzureApiQueueClient,
    ): QueueAsyncClient {
        val queueName = queueConfig.transactionNotificationRequestedQueueName
        return buildQueueAsyncClient(
            queueConfig.storageConnectionString,
            queueName,
            jsonSerializerV2,
            queueConfig,
            azureApiQueueClient,
        )
    }

    /**
     * Builds a reactive queue client, compatible with native builds.
     *
     * @param storageConnectionString Azure Storage connection string
     * @param queueName Name of the queue to connect to
     * @param jsonSerializer Serializer for message marshalling
     * @return QueueAsyncClient compatible with native builds
     */
    private fun buildQueueAsyncClient(
        storageConnectionString: String,
        queueName: String,
        jsonSerializer: JsonSerializer,
        queueConfig: QueueConfig,
        azureApiQueueClient: AzureApiQueueClient,
    ): QueueAsyncClient {
        val azureQueueClient = createAzureQueueClient(storageConnectionString, queueName)
        val queueAsyncClient =
            createNativeCompatibleQueueClient(
                azureQueueClient,
                jsonSerializer,
                azureApiQueueClient,
                queueConfig,
            )
        return queueAsyncClient
    }

    /**
     * Creates minimal Azure Queue client for metadata only (queueName, queueUrl) Actual message
     * sending bypassed via AzureApiQueueClient
     */
    private fun createAzureQueueClient(
        connectionString: String,
        queueName: String,
    ): AzureQueueAsyncClient {
        return QueueClientBuilder()
            .connectionString(connectionString)
            .queueName(queueName)
            .buildAsyncClient()
    }

    /** Creates a mock SendMessageResult response for the Azure API queue HTTP client fallback */
    private fun createMockSendMessageResponse(): Response<SendMessageResult> {
        val mockResult = SendMessageResult()
        return object : Response<SendMessageResult> {
            override fun getStatusCode(): Int = 201

            override fun getHeaders(): HttpHeaders = HttpHeaders()

            override fun getRequest(): HttpRequest? = null

            override fun getValue(): SendMessageResult = mockResult
        }
    }
}
