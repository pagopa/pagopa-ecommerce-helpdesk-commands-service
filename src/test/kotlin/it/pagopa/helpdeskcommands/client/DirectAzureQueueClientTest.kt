package it.pagopa.helpdeskcommands.client

import java.io.IOException
import java.util.stream.Stream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.test.StepVerifier

class DirectAzureQueueClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var directAzureQueueClient: DirectAzureQueueClient

    companion object {
        private const val VALID_CONNECTION_STRING =
            "DefaultEndpointsProtocol=https;AccountName=testaccount;AccountKey=dGVzdGtleWZvcmF6dXJlc3RvcmFnZTEyMzQ1Njc4OTA=;EndpointSuffix=core.windows.net"
        private const val INVALID_CONNECTION_STRING_NO_ACCOUNT =
            "DefaultEndpointsProtocol=https;AccountKey=dGVzdGtleWZvcmF6dXJlc3RvcmFnZTEyMzQ1Njc4OTA=;EndpointSuffix=core.windows.net"
        private const val INVALID_CONNECTION_STRING_NO_KEY =
            "DefaultEndpointsProtocol=https;AccountName=testaccount;EndpointSuffix=core.windows.net"
        private const val MALFORMED_CONNECTION_STRING = "invalid_connection_string"

        private const val TEST_STORAGE_ACCOUNT = "testaccount"
        private const val TEST_STORAGE_KEY = "dGVzdGtleWZvcmF6dXJlc3RvcmFnZTEyMzQ1Njc4OTA="
        private const val TEST_QUEUE_NAME = "test-queue"
        private const val TEST_MESSAGE = """{"event":"test","data":"value"}"""

        @JvmStatic
        private fun invalidConnectionStrings(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    INVALID_CONNECTION_STRING_NO_ACCOUNT,
                    "Invalid Azure Storage connection string"
                ),
                Arguments.of(
                    INVALID_CONNECTION_STRING_NO_KEY,
                    "Invalid Azure Storage connection string"
                ),
                Arguments.of(
                    MALFORMED_CONNECTION_STRING,
                    "Invalid Azure Storage connection string"
                ),
                Arguments.of("", "Invalid Azure Storage connection string"),
                Arguments.of("AccountName=test", "Invalid Azure Storage connection string")
            )

        @JvmStatic
        private fun httpErrorResponses(): Stream<Arguments> =
            Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST, "Bad Request"),
                Arguments.of(HttpStatus.UNAUTHORIZED, "Unauthorized"),
                Arguments.of(HttpStatus.FORBIDDEN, "Forbidden"),
                Arguments.of(HttpStatus.NOT_FOUND, "Not Found"),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable")
            )
    }

    @BeforeEach
    @Throws(IOException::class)
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        directAzureQueueClient = DirectAzureQueueClient()
    }

    @AfterEach
    @Throws(IOException::class)
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should parse valid connection string successfully`() {
        val credentials =
            directAzureQueueClient.parseConnectionString(VALID_CONNECTION_STRING).block()!!

        assertEquals(TEST_STORAGE_ACCOUNT, credentials.accountName)
        assertEquals(TEST_STORAGE_KEY, credentials.accountKey)
    }

    @ParameterizedTest
    @MethodSource("invalidConnectionStrings")
    fun `should throw exception for invalid connection strings`(
        connectionString: String,
        expectedErrorMessage: String
    ) {
        val exception =
            assertThrows<IllegalArgumentException> {
                directAzureQueueClient.parseConnectionString(connectionString).block()
            }

        assertTrue(exception.message!!.contains(expectedErrorMessage))
    }

    @Test
    fun `should send message successfully with storage key`() {
        val expectedResponse =
            """<?xml version="1.0" encoding="utf-8"?>
            <QueueMessagesList>
                <QueueMessage>
                    <MessageId>12345-67890-abcdef</MessageId>
                    <InsertionTime>Mon, 10 Jun 2025 14:00:00 GMT</InsertionTime>
                    <ExpirationTime>Mon, 17 Jun 2025 14:00:00 GMT</ExpirationTime>
                    <PopReceipt>ABC123</PopReceipt>
                    <TimeNextVisible>Mon, 10 Jun 2025 14:00:00 GMT</TimeNextVisible>
                </QueueMessage>
            </QueueMessagesList>"""

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/xml")
                .setBody(expectedResponse)
        )

        val queueUrl =
            "http://${mockWebServer.hostName}:${mockWebServer.port}/$TEST_STORAGE_ACCOUNT/$TEST_QUEUE_NAME"

        StepVerifier.create(
                directAzureQueueClient.sendMessageWithStorageKey(
                    queueUrl = queueUrl,
                    queueName = TEST_QUEUE_NAME,
                    message = TEST_MESSAGE,
                    storageAccount = TEST_STORAGE_ACCOUNT,
                    storageKey = TEST_STORAGE_KEY
                )
            )
            .expectNext(expectedResponse)
            .verifyComplete()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/$TEST_STORAGE_ACCOUNT/$TEST_QUEUE_NAME/messages", recordedRequest.path)
        assertEquals("application/xml", recordedRequest.getHeader("Content-Type"))
        assertEquals("2021-02-12", recordedRequest.getHeader("x-ms-version"))
        assertEquals("helpdesk-commands-service/1.0", recordedRequest.getHeader("User-Agent"))

        val authHeader = recordedRequest.getHeader("Authorization")
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("SharedKey $TEST_STORAGE_ACCOUNT:"))

        val dateHeader = recordedRequest.getHeader("x-ms-date")
        assertNotNull(dateHeader)
        assertTrue(
            dateHeader!!.matches(Regex("\\w{3}, \\d{2} \\w{3} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT"))
        )

        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(requestBody.contains("<QueueMessage>"))
        assertTrue(requestBody.contains("<MessageText>"))
        assertTrue(requestBody.contains("</MessageText>"))
        assertTrue(requestBody.contains("</QueueMessage>"))
    }

    @ParameterizedTest
    @MethodSource("httpErrorResponses")
    fun `should handle HTTP error responses`(httpStatus: HttpStatus) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(httpStatus.value())
                .setHeader("Content-Type", "application/xml")
                .setBody(
                    """<?xml version="1.0" encoding="utf-8"?>
                    <Error>
                        <Code>InvalidXmlDocument</Code>
                        <Message>XML specified is not syntactically valid.</Message>
                    </Error>"""
                )
        )

        val queueUrl =
            "http://${mockWebServer.hostName}:${mockWebServer.port}/$TEST_STORAGE_ACCOUNT/$TEST_QUEUE_NAME"

        StepVerifier.create(
                directAzureQueueClient.sendMessageWithStorageKey(
                    queueUrl = queueUrl,
                    queueName = TEST_QUEUE_NAME,
                    message = TEST_MESSAGE,
                    storageAccount = TEST_STORAGE_ACCOUNT,
                    storageKey = TEST_STORAGE_KEY
                )
            )
            .expectError(WebClientResponseException::class.java)
            .verify()
    }

    @Test
    fun `should handle invalid storage key gracefully`() {
        val invalidStorageKey = "invalid-key-not-base64"
        val queueUrl =
            "http://${mockWebServer.hostName}:${mockWebServer.port}/$TEST_STORAGE_ACCOUNT/$TEST_QUEUE_NAME"

        StepVerifier.create(
                directAzureQueueClient.sendMessageWithStorageKey(
                    queueUrl = queueUrl,
                    queueName = TEST_QUEUE_NAME,
                    message = TEST_MESSAGE,
                    storageAccount = TEST_STORAGE_ACCOUNT,
                    storageKey = invalidStorageKey
                )
            )
            .expectError(IllegalStateException::class.java)
            .verify()
    }

    @Test
    fun `should handle empty message content`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/xml")
                .setBody(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><QueueMessagesList></QueueMessagesList>"
                )
        )

        val queueUrl =
            "http://${mockWebServer.hostName}:${mockWebServer.port}/$TEST_STORAGE_ACCOUNT/$TEST_QUEUE_NAME"

        StepVerifier.create(
                directAzureQueueClient.sendMessageWithStorageKey(
                    queueUrl = queueUrl,
                    queueName = TEST_QUEUE_NAME,
                    message = "",
                    storageAccount = TEST_STORAGE_ACCOUNT,
                    storageKey = TEST_STORAGE_KEY
                )
            )
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `should handle large message payload`() {
        val largeMessage = "x".repeat(60000) // 60KB message

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/xml")
                .setBody(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><QueueMessagesList></QueueMessagesList>"
                )
        )

        val queueUrl =
            "http://${mockWebServer.hostName}:${mockWebServer.port}/$TEST_STORAGE_ACCOUNT/$TEST_QUEUE_NAME"

        StepVerifier.create(
                directAzureQueueClient.sendMessageWithStorageKey(
                    queueUrl = queueUrl,
                    queueName = TEST_QUEUE_NAME,
                    message = largeMessage,
                    storageAccount = TEST_STORAGE_ACCOUNT,
                    storageKey = TEST_STORAGE_KEY
                )
            )
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `should validate authorization header generation with different parameters`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/xml")
                .setBody(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><QueueMessagesList></QueueMessagesList>"
                )
        )

        val customQueueName = "custom-queue-name"
        val customStorageAccount = "customstorageaccount"
        val queueUrl =
            "http://${mockWebServer.hostName}:${mockWebServer.port}/$customStorageAccount/$customQueueName"

        StepVerifier.create(
                directAzureQueueClient.sendMessageWithStorageKey(
                    queueUrl = queueUrl,
                    queueName = customQueueName,
                    message = TEST_MESSAGE,
                    storageAccount = customStorageAccount,
                    storageKey = TEST_STORAGE_KEY
                )
            )
            .expectNextCount(1)
            .verifyComplete()

        val recordedRequest = mockWebServer.takeRequest()
        val authHeader = recordedRequest.getHeader("Authorization")
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("SharedKey $customStorageAccount:"))
    }
}
