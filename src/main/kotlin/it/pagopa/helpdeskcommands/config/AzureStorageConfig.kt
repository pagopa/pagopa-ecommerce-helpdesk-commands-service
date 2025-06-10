package it.pagopa.helpdeskcommands.config

import com.azure.core.http.HttpClient as AzureHttpClient
import com.azure.core.http.HttpHeaders
import com.azure.core.http.HttpRequest
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder
import com.azure.core.http.policy.HttpPipelinePolicy
import com.azure.core.http.rest.Response
import com.azure.core.util.BinaryData
import com.azure.core.util.serializer.JsonSerializer
import com.azure.storage.queue.QueueAsyncClient as AzureQueueAsyncClient
import com.azure.storage.queue.QueueClientBuilder
import com.azure.storage.queue.models.QueueStorageException
import com.azure.storage.queue.models.SendMessageResult
import it.pagopa.ecommerce.commons.client.QueueAsyncClient
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
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
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
     * deserialization. Registers all TransactionEvent classes for native compilation.
     *
     * @return Configured JsonSerializer instance with QueueEvent mixins
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

        // native-compatible approach
        val serializer =
            try {
                logger.info("Attempting native-compatible Jackson serializer...")
                createNativeCompatibleSerializer()
            } catch (e: Exception) {
                logger.warn(
                    "Native serializer failed, falling back to StrictJsonSerializerProvider: {}",
                    e.message
                )
                StrictJsonSerializerProvider()
                    .addMixIn(
                        QueueEvent::class.java,
                        QueueEventMixInClassFieldDiscriminator::class.java
                    )
                    .createInstance()
            }

        logger.info("JsonSerializer created successfully")

        // test for debugging
        try {
            val testEvent =
                TransactionRefundRequestedEvent(
                    "93cce28d3b7c4cb9975e6d856ecee89f",
                    TransactionRefundRequestedData(
                        null,
                        it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
                            .CLOSED,
                        TransactionRefundRequestedData.RefundTrigger.MANUAL
                    )
                )
            val testQueueEvent = QueueEvent(testEvent, null)
            val testJson = serializer.serializeToBytes(testQueueEvent)
            logger.info("Test serialization successful - JSON length: {} bytes", testJson.size)
            logger.info("Test JSON content: {}", String(testJson))

            // additional debugging: check if json is valid
            val jsonString = String(testJson)
            if (jsonString.startsWith("{") && jsonString.endsWith("}")) {
                logger.info("JSON format appears valid (starts with { ends with })")
            } else {
                logger.warn("JSON format suspicious - first 50 chars: {}", jsonString.take(50))
            }
        } catch (e: Exception) {
            logger.error("Test serialization failed: {}", e.message, e)
        }

        return serializer
    }

    /**
     * Creates a native-compatible JsonSerializer using StrictJsonSerializerProvider This approach
     * ensures compatibility with the Azure SDK's expected interface
     */
    private fun createNativeCompatibleSerializer(): JsonSerializer {
        logger.info("Creating StrictJsonSerializerProvider with QueueEvent mixin")
        return StrictJsonSerializerProvider()
            .addMixIn(QueueEvent::class.java, QueueEventMixInClassFieldDiscriminator::class.java)
            .createInstance()
    }

    /**
     * Creates a native-compatible QueueAsyncClient that handles Base64 encoding properly in GraalVM
     * native mode where BinaryData.fromObjectAsync behaves differently
     */
    private fun createNativeCompatibleQueueClient(
        azureQueueClient: AzureQueueAsyncClient,
        jsonSerializer: JsonSerializer,
        directClient: DirectAzureQueueClient,
        queueConfig: QueueConfig
    ): QueueAsyncClient {
        logger.info("Creating native-compatible QueueAsyncClient wrapper with direct HTTP fallback")

        // create a wrapper that uses manual Base64 encoding for native mode compatibility
        return object : QueueAsyncClient(azureQueueClient, jsonSerializer) {
            override fun <
                T : it.pagopa.ecommerce.commons.documents.BaseTransactionEvent<
                    *
                >> sendMessageWithResponse(
                event: QueueEvent<T>,
                visibilityTimeout: java.time.Duration?,
                timeToLive: java.time.Duration?
            ): Mono<Response<SendMessageResult>> {

                logger.debug(
                    "Native-compatible message sending for event: {}",
                    event.event().javaClass.simpleName
                )

                return try {
                    val jsonBytes = jsonSerializer.serializeToBytes(event)
                    val jsonString = String(jsonBytes, Charsets.UTF_8)

                    logger.debug("JSON serialized: {} bytes", jsonBytes.size)
                    logger.debug(
                        "JSON content: {}",
                        jsonString.take(200) + if (jsonString.length > 200) "..." else ""
                    )

                    // approach 1: use azure sdk's default behavior with base64 encoding
                    // this is the working solution for classic gradle build
                    logger.debug("Attempting Azure SDK sendMessage with Base64 content...")
                    val base64Content = Base64.getEncoder().encodeToString(jsonBytes)

                    logger.debug("Base64 message length: {} chars", base64Content.length)
                    logger.debug("Base64 content (first 100): {}", base64Content.take(100))

                    azureQueueClient
                        .sendMessage(base64Content)
                        .flatMap { result ->
                            logger.info("Base64 sendMessage SUCCESS with result: {}", result)
                            // return a successful response by using sendMessageWithResponse for
                            // consistency
                            val binaryData = BinaryData.fromString(base64Content)
                            azureQueueClient.sendMessageWithResponse(
                                binaryData,
                                visibilityTimeout,
                                timeToLive
                            )
                        }
                        .doOnError { error ->
                            logger.error("Base64 sendMessage failed: {}", error.message)
                            logger.debug("Falling back to sendMessageWithResponse approaches...")
                        }
                        .onErrorResume { _ ->
                            // fallback with multi-approach
                            logger.debug("Attempting sendMessageWithResponse with JSON string...")
                            val jsonBinaryData = BinaryData.fromString(jsonString)

                            azureQueueClient
                                .sendMessageWithResponse(
                                    jsonBinaryData,
                                    visibilityTimeout,
                                    timeToLive
                                )
                                .doOnError { error ->
                                    logger.error("Direct JSON string failed: {}", error.message)
                                    logger.debug("Falling back to BinaryData.fromBytes approach...")
                                }
                                .onErrorResume { _ ->
                                    // fallback 1: BinaryData.fromBytes
                                    logger.debug("Trying BinaryData.fromBytes...")
                                    val binaryData = BinaryData.fromBytes(jsonBytes)

                                    azureQueueClient
                                        .sendMessageWithResponse(
                                            binaryData,
                                            visibilityTimeout,
                                            timeToLive
                                        )
                                        .doOnError { error2 ->
                                            logger.error(
                                                "BinaryData.fromBytes also failed: {}",
                                                error2.message
                                            )
                                            logger.debug(
                                                "Falling back to Base64 encoding approach..."
                                            )
                                        }
                                        .onErrorResume { _ ->
                                            // fallback 2: manual base64 encoding
                                            val base64Encoded =
                                                Base64.getEncoder().encodeToString(jsonBytes)
                                            val base64BinaryData =
                                                BinaryData.fromString(base64Encoded)

                                            logger.debug(
                                                "Base64 fallback: {} chars",
                                                base64Encoded.length
                                            )
                                            logger.debug(
                                                "Base64 content (first 100): {}",
                                                base64Encoded.take(100) + "..."
                                            )

                                            azureQueueClient
                                                .sendMessageWithResponse(
                                                    base64BinaryData,
                                                    visibilityTimeout,
                                                    timeToLive
                                                )
                                                .doOnError { error3 ->
                                                    logger.error(
                                                        "ALL Azure SDK approaches failed. Trying direct HTTP client: {}",
                                                        error3.message
                                                    )
                                                }
                                                .onErrorResume { _ ->
                                                    // final fallback: direct HTTP client with
                                                    // storage account key
                                                    logger.info(
                                                        "Attempting direct HTTP Azure Queue client with Storage Account Key"
                                                    )

                                                    val queueName = azureQueueClient.queueName
                                                    val queueUrl = azureQueueClient.queueUrl

                                                    val credentials =
                                                        directClient.parseConnectionString(
                                                            queueConfig.storageConnectionString
                                                        )

                                                    directClient
                                                        .sendMessageWithStorageKey(
                                                            queueUrl,
                                                            queueName,
                                                            jsonString,
                                                            credentials.accountName,
                                                            credentials.accountKey
                                                        )
                                                        .map { response ->
                                                            logger.info(
                                                                "AzureStorageConfig: Direct HTTP client SUCCESS with Storage Account Key: {}",
                                                                response
                                                            )
                                                            // create a mock response to match the
                                                            // expected interface
                                                            createMockSendMessageResponse()
                                                        }
                                                        .doOnError { httpError ->
                                                            logger.error(
                                                                "Direct HTTP client also failed: {}",
                                                                httpError.message
                                                            )
                                                        }
                                                }
                                        }
                                }
                        }
                } catch (e: Exception) {
                    logger.error(
                        "Native serialization failed, falling back to direct HTTP: {}",
                        e.message
                    )
                    // final fallback: direct HTTP client with storage account key
                    logger.info("Direct HTTP fallback triggered due to serialization error")
                    logger.info("Attempting Storage Account Key approach for direct HTTP fallback")

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
                        .map { response ->
                            logger.info(
                                "Direct HTTP fallback SUCCESS with Storage Account Key: {}",
                                response
                            )
                            createMockSendMessageResponse()
                        }
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
        logger.info("Configuring QueueAsyncClient for refund queue: {}", queueName)
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
        logger.info("Configuring QueueAsyncClient for notification queue: {}", queueName)
        return buildQueueAsyncClient(
            queueConfig.storageConnectionString,
            queueName,
            jsonSerializerV2,
            queueConfig,
            directClient
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
        jsonSerializer: JsonSerializer,
        queueConfig: QueueConfig,
        directClient: DirectAzureQueueClient
    ): Mono<QueueAsyncClient> {
        val azureQueueClient = createAzureQueueClient(storageConnectionString, queueName)
        logger.debug("Queue client created for queue: {}", queueName)

        // use native-compatible wrapper to handle Base64 encoding properly in GraalVM
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
     * Creates an Azure Queue async client with custom HTTP configuration. Enhanced for native mode
     * with proper Content-Type headers.
     *
     * @param connectionString Azure Storage connection string
     * @param queueName Target queue name
     * @return Configured AzureQueueAsyncClient instance
     */
    private fun createAzureQueueClient(
        connectionString: String,
        queueName: String
    ): AzureQueueAsyncClient {
        logger.info("Creating Azure Queue client with native-compatible HTTP configuration")

        // create a custom HTTP pipeline policy to ensure proper headers
        val contentTypePolicy = HttpPipelinePolicy { context, next ->
            val request = context.httpRequest
            logger.debug("Intercepting request: {} {}", request.httpMethod, request.url)

            // check and log current headers
            val currentContentType = request.headers["Content-Type"]
            logger.debug("Current Content-Type: {}", currentContentType)

            // for queue message operations, just force the api version
            if (request.url.path.contains("/messages")) {
                when (request.httpMethod.toString()) {
                    "POST" -> {
                        logger.debug("Queue POST operation detected, setting API version only")
                        request.headers["x-ms-version"] = "2019-12-12" // Force older API version
                    }
                    "PUT" -> {
                        logger.debug("Queue PUT operation detected, setting API version only")
                        request.headers["x-ms-version"] = "2019-12-12"
                    }
                }
            }

            next.process()
        }

        return QueueClientBuilder()
            .connectionString(connectionString)
            .queueName(queueName)
            .httpClient(createHttpClient())
            .addPolicy(contentTypePolicy)
            .serviceVersion(
                com.azure.storage.queue.QueueServiceVersion.V2019_12_12
            ) // use older stable version for native compatibility
            .buildAsyncClient()
    }

    /** Creates a mock SendMessageResult response for the direct HTTP client fallback */
    private fun createMockSendMessageResponse(): Response<SendMessageResult> {
        val mockResult = SendMessageResult()
        return object : Response<SendMessageResult> {
            override fun getStatusCode(): Int = 201
            override fun getHeaders(): HttpHeaders = HttpHeaders()
            override fun getRequest(): HttpRequest? = null
            override fun getValue(): SendMessageResult = mockResult
        }
    }

    /**
     * Creates an HTTP client optimized for Azure Storage with custom DNS resolver settings.
     * Enhanced for native mode compatibility with proper Content-Type handling.
     *
     * @return Configured Netty-based HTTP client
     */
    private fun createHttpClient(): AzureHttpClient {
        logger.info("Creating native-compatible HTTP client for Azure Storage Queue")

        return NettyAsyncHttpClientBuilder(
                HttpClient.create()
                    .resolver { nameResolverSpec -> nameResolverSpec.ndots(1) }
                    .doOnRequest { request, connection ->
                        logger.debug("HTTP Request: {} {}", request.method(), request.uri())
                        logger.debug("Request Headers: {}", request.requestHeaders())
                    }
                    .doOnResponse { response, connection ->
                        logger.debug("HTTP Response: {}", response.status())
                        logger.debug("Response Headers: {}", response.responseHeaders())
                    }
            )
            .build()
    }
}
