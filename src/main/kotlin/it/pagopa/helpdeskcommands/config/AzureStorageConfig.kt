package it.pagopa.helpdeskcommands.config

import com.azure.core.http.HttpHeaders
import com.azure.core.http.HttpRequest
import com.azure.core.http.rest.Response
import com.azure.core.util.serializer.JsonSerializer
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
import it.pagopa.helpdeskcommands.client.DirectAzureQueueClient
import it.pagopa.helpdeskcommands.config.properties.QueueConfig
import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

@Configuration
@EnableConfigurationProperties(QueueConfig::class)
class AzureStorageConfig {
    private val logger = LoggerFactory.getLogger(AzureStorageConfig::class.java)

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
        QueueEventMixInClassFieldDiscriminator::class,
        StrictJsonSerializerProvider::class,
        TransactionRefundRequestedEvent::class,
        TransactionUserReceiptRequestedEvent::class,
        TransactionRefundRequestedData::class,
        TransactionUserReceiptData::class,
        QueueStorageException::class,
        org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider::class,
        org.springframework.core.type.filter.AssignableTypeFilter::class
    )
    fun jsonSerializerV2(): JsonSerializer {
        logger.info("Creating JsonSerializer for Azure Storage Queue")
        return StrictJsonSerializerProvider()
            .addMixIn(QueueEvent::class.java, QueueEventMixInClassFieldDiscriminator::class.java)
            .createInstance()
    }

    /** Creates a native-compatible QueueAsyncClient using DirectAzureQueueClient */
    private fun createNativeCompatibleQueueClient(
        azureQueueClient: AzureQueueAsyncClient,
        jsonSerializer: JsonSerializer,
        directClient: DirectAzureQueueClient,
        queueConfig: QueueConfig
    ): QueueAsyncClient {
        return object : QueueAsyncClient(azureQueueClient, jsonSerializer) {
            override fun <T : BaseTransactionEvent<*>> sendMessageWithResponse(
                event: QueueEvent<T>,
                visibilityTimeout: java.time.Duration?,
                timeToLive: java.time.Duration?
            ): Mono<Response<SendMessageResult>> {
                return try {
                    val jsonBytes = jsonSerializer.serializeToBytes(event)
                    val jsonString = String(jsonBytes, Charsets.UTF_8)

                    val queueName = azureQueueClient.queueName
                    val queueUrl = azureQueueClient.queueUrl
                    val credentials =
                        directClient.parseConnectionString(queueConfig.storageConnectionString)

                    directClient
                        .sendMessageWithStorageKey(
                            queueUrl,
                            queueName,
                            jsonString,
                            credentials.accountName,
                            credentials.accountKey
                        )
                        .map { response -> createMockSendMessageResponse() }
                } catch (e: Exception) {
                    logger.error("Direct HTTP client error: {}", e.message)
                    Mono.error(e)
                }
            }
        }
    }

    /**
     * Creates a reactive queue client for handling transaction refund events.
     *
     * @param queueConfig Configuration properties containing queue connection details
     * @param jsonSerializerV2 Serializer for marshalling queue messages
     * @return Mono emitting configured QueueAsyncClient or empty on failure
     */
    @Bean
    @Qualifier("transactionRefundQueueAsyncClient")
    fun transactionRefundQueueAsyncClient(
        queueConfig: QueueConfig,
        jsonSerializerV2: JsonSerializer,
        directClient: DirectAzureQueueClient
    ): Mono<QueueAsyncClient> {
        val queueName = queueConfig.transactionRefundQueueName
        return buildQueueAsyncClient(
            queueConfig.storageConnectionString,
            queueName,
            jsonSerializerV2,
            queueConfig,
            directClient
        )
    }

    /**
     * Creates a reactive queue client for handling transaction notification requests.
     *
     * @param queueConfig Configuration properties containing queue connection details
     * @param jsonSerializerV2 Serializer for marshalling queue messages
     * @return Mono emitting configured QueueAsyncClient or empty on failure
     */
    @Bean
    @Qualifier("transactionNotificationQueueAsyncClient")
    fun transactionNotificationQueueAsyncClient(
        queueConfig: QueueConfig,
        jsonSerializerV2: JsonSerializer,
        directClient: DirectAzureQueueClient
    ): Mono<QueueAsyncClient> {
        val queueName = queueConfig.transactionNotificationRequestedQueueName
        return buildQueueAsyncClient(
            queueConfig.storageConnectionString,
            queueName,
            jsonSerializerV2,
            queueConfig,
            directClient
        )
    }

    /**
     * Builds a reactive queue client, compatible with native builds.
     *
     * @param storageConnectionString Azure Storage connection string
     * @param queueName Name of the queue to connect to
     * @param jsonSerializer Serializer for message marshalling
     * @return Mono emitting QueueAsyncClient or empty if queue setup fails
     */
    private fun buildQueueAsyncClient(
        storageConnectionString: String,
        queueName: String,
        jsonSerializer: JsonSerializer,
        queueConfig: QueueConfig,
        directClient: DirectAzureQueueClient
    ): Mono<QueueAsyncClient> {
        val azureQueueClient = createAzureQueueClient(storageConnectionString, queueName)
        val queueAsyncClient =
            createNativeCompatibleQueueClient(
                azureQueueClient,
                jsonSerializer,
                directClient,
                queueConfig
            )
        return Mono.just(queueAsyncClient)
    }

    /**
     * Creates minimal Azure Queue client for metadata only (queueName, queueUrl) Actual message
     * sending bypassed via DirectAzureQueueClient
     */
    private fun createAzureQueueClient(
        connectionString: String,
        queueName: String
    ): AzureQueueAsyncClient {
        return QueueClientBuilder()
            .connectionString(connectionString)
            .queueName(queueName)
            .buildAsyncClient()
    }

    /** Creates a mock SendMessageResult response for the direct HTTP client fallback */
    private fun createMockSendMessageResponse(): Response<SendMessageResult> {
        logger.info("called createMockSendMessageResponse method")
        val mockResult = SendMessageResult()
        return object : Response<SendMessageResult> {
            override fun getStatusCode(): Int = 201
            override fun getHeaders(): HttpHeaders = HttpHeaders()
            override fun getRequest(): HttpRequest? = null
            override fun getValue(): SendMessageResult = mockResult
        }
    }
}
