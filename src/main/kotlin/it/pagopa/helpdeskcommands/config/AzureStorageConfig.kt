package it.pagopa.helpdeskcommands.config

import com.azure.core.http.HttpClient as AzureHttpClient
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder
import com.azure.core.util.serializer.JsonSerializer
import com.azure.storage.queue.QueueAsyncClient as AzureQueueAsyncClient
import com.azure.storage.queue.QueueClientBuilder
import it.pagopa.ecommerce.commons.client.QueueAsyncClient
import it.pagopa.ecommerce.commons.queues.QueueEvent
import it.pagopa.ecommerce.commons.queues.StrictJsonSerializerProvider
import it.pagopa.ecommerce.commons.queues.mixin.serialization.v2.QueueEventMixInClassFieldDiscriminator
import it.pagopa.helpdeskcommands.config.properties.QueueConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient

@Configuration
@EnableConfigurationProperties(QueueConfig::class)
class AzureStorageConfig {
    private val logger = LoggerFactory.getLogger(AzureStorageConfig::class.java)

    /**
     * Creates a JSON serializer configured with V2 queue event mixins for proper event
     * deserialization.
     *
     * @return Configured JsonSerializer instance with QueueEvent mixins
     */
    @Bean
    fun jsonSerializerV2(): JsonSerializer {
        return StrictJsonSerializerProvider()
            .addMixIn(QueueEvent::class.java, QueueEventMixInClassFieldDiscriminator::class.java)
            .createInstance()
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
        jsonSerializerV2: JsonSerializer
    ): Mono<QueueAsyncClient> {
        val queueName = queueConfig.transactionRefundQueueName
        logger.info("Configuring QueueAsyncClient for refund queue: {}", queueName)
        return buildQueueAsyncClient(
            queueConfig.storageConnectionString,
            queueName,
            jsonSerializerV2
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
        jsonSerializerV2: JsonSerializer
    ): Mono<QueueAsyncClient> {
        val queueName = queueConfig.transactionNotificationRequestedQueueName
        logger.info("Configuring QueueAsyncClient for notification queue: {}", queueName)
        return buildQueueAsyncClient(
            queueConfig.storageConnectionString,
            queueName,
            jsonSerializerV2
        )
    }

    /**
     * Builds a reactive queue client with automatic queue creation and error handling.
     *
     * @param storageConnectionString Azure Storage connection string
     * @param queueName Name of the queue to connect to
     * @param jsonSerializer Serializer for message marshalling
     * @return Mono emitting QueueAsyncClient or empty if queue setup fails
     */
    private fun buildQueueAsyncClient(
        storageConnectionString: String,
        queueName: String,
        jsonSerializer: JsonSerializer
    ): Mono<QueueAsyncClient> {
        val azureQueueClient = createAzureQueueClient(storageConnectionString, queueName)

        return ensureQueueExists(azureQueueClient, queueName)
            .map { QueueAsyncClient(azureQueueClient, jsonSerializer) }
            .onErrorResume { e ->
                logger.error(
                    "Failed to ensure queue '{}' exists. No QueueAsyncClient will be provided for this queue.",
                    queueName,
                    e
                )
                Mono.empty()
            }
    }

    /**
     * Creates an Azure Queue async client with custom HTTP configuration.
     *
     * @param connectionString Azure Storage connection string
     * @param queueName Target queue name
     * @return Configured AzureQueueAsyncClient instance
     */
    private fun createAzureQueueClient(
        connectionString: String,
        queueName: String
    ): AzureQueueAsyncClient {
        return QueueClientBuilder()
            .connectionString(connectionString)
            .queueName(queueName)
            .httpClient(createHttpClient())
            .buildAsyncClient()
    }

    /**
     * Creates an HTTP client optimized for Azure Storage with custom DNS resolver settings.
     *
     * @return Configured Netty-based HTTP client
     */
    private fun createHttpClient(): AzureHttpClient {
        return NettyAsyncHttpClientBuilder(
                HttpClient.create().resolver { nameResolverSpec -> nameResolverSpec.ndots(1) }
            )
            .build()
    }

    /**
     * Ensures the specified queue exists in Azure Storage, creating it if necessary.
     *
     * @param queueClient Azure queue client to check
     * @param queueName Queue name for logging purposes
     * @return Mono emitting the queue client on success
     */
    private fun ensureQueueExists(
        queueClient: AzureQueueAsyncClient,
        queueName: String
    ): Mono<AzureQueueAsyncClient> {
        return queueClient
            .createIfNotExistsWithResponse(null)
            .doOnSuccess { response ->
                logger.info(
                    "Queue '{}' creation check completed. Status code: {}",
                    queueName,
                    response?.statusCode ?: "N/A"
                )
            }
            .doOnError { e ->
                logger.error("Error during queue '{}' creation check: {}", queueName, e.message, e)
            }
            .thenReturn(queueClient)
    }
}
