package it.pagopa.helpdeskcommands.client

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundOutcomeDto
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundRequestDto
import it.pagopa.generated.ecommerce.redirect.v1.dto.RefundResponseDto
import it.pagopa.generated.nodeforwarder.v1.dto.ProxyApi
import it.pagopa.helpdeskcommands.client.NodeForwarderClient.NodeForwarderResponse
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import java.io.IOException
import java.net.URI
import java.util.*
import java.util.stream.Stream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class NodeForwarderClientTest {

    private val proxyApi: ProxyApi = Mockito.mock(ProxyApi::class.java)

    private val nodeForwarderClient: NodeForwarderClient<RefundRequestDto, RefundResponseDto> =
        NodeForwarderClient(proxyApi)

    @BeforeEach
    @Throws(IOException::class)
    fun beforeAll() {
        mockWebServer = MockWebServer()
        mockWebServer!!.start(8080)
        System.out.printf(
            "Mock web server listening on %s:%s%n",
            mockWebServer!!.hostName,
            mockWebServer!!.port
        )
    }

    @AfterEach
    @Throws(IOException::class)
    fun afterAll() {
        mockWebServer!!.shutdown()
        println("Mock web server stopped")
    }

    @Test
    fun `should proxy request successfully retrieving default port for https URL`() {
        // pre-requisites
        val requestId = UUID.randomUUID().toString()
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("https://localhost/test/request")
        val expectedHostHeader = "localhost"
        val expectedPortHeader = 443
        val expectedPathRequest = "/test/request"
        val expectedPayload =
            "{\"idTransaction\":\"ecf06892c9e04ae39626dfdfda631b94\",\"idPSPTransaction\":\"5f521592f3d84ffa8d8f68651da91144\",\"action\":\"refund\"}"
        val expectedResponse: NodeForwarderResponse<RefundResponseDto> =
            NodeForwarderResponse(
                RefundResponseDto()
                    .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                    .outcome(RefundOutcomeDto.OK),
                Optional.of(requestId)
            )
        BDDMockito.given(
                proxyApi.forwardWithHttpInfo(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                )
            )
            .willReturn(
                Mono.just(
                    ResponseEntity.ok()
                        .header("X-Request-Id", requestId)
                        .body(
                            "{\"idTransaction\":\"ecf06892c9e04ae39626dfdfda631b94\",\"outcome\":\"OK\"}"
                        )
                )
            )
        // test
        StepVerifier.create(
                nodeForwarderClient.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    RefundResponseDto::class.java
                )
            )
            .expectNext(expectedResponse)
            .verifyComplete()
        Mockito.verify(proxyApi, Mockito.times(1))
            .forwardWithHttpInfo(
                expectedHostHeader,
                expectedPortHeader,
                expectedPathRequest,
                requestId,
                expectedPayload
            )
    }

    @Test
    fun `should proxy request successfully using custom port`() {
        // pre-requisites
        val requestId = UUID.randomUUID().toString()
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("http://localhost:123/test/request")
        val expectedHostHeader = "localhost"
        val expectedPortHeader = 123
        val expectedPathRequest = "/test/request"
        val expectedPayload =
            "{\"idTransaction\":\"ecf06892c9e04ae39626dfdfda631b94\",\"idPSPTransaction\":\"5f521592f3d84ffa8d8f68651da91144\",\"action\":\"refund\"}"
        val expectedResponse: NodeForwarderResponse<RefundResponseDto> =
            NodeForwarderResponse(
                RefundResponseDto()
                    .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                    .outcome(RefundOutcomeDto.OK),
                Optional.of(requestId)
            )
        BDDMockito.given(
                proxyApi.forwardWithHttpInfo(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                )
            )
            .willReturn(
                Mono.just(
                    ResponseEntity.ok()
                        .header("X-Request-Id", requestId)
                        .body(
                            "{\"idTransaction\":\"ecf06892c9e04ae39626dfdfda631b94\",\"outcome\":\"OK\"}"
                        )
                )
            )
        // test
        StepVerifier.create(
                nodeForwarderClient.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    RefundResponseDto::class.java
                )
            )
            .expectNext(expectedResponse)
            .verifyComplete()
        Mockito.verify(proxyApi, Mockito.times(1))
            .forwardWithHttpInfo(
                expectedHostHeader,
                expectedPortHeader,
                expectedPathRequest,
                requestId,
                expectedPayload
            )
    }

    @Test
    fun `should handle error deserializing response`() {
        // pre-requisites
        val requestId = UUID.randomUUID().toString()
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("http://localhost:123/test/request")
        val expectedHostHeader = "localhost"
        val expectedPortHeader = 123
        val expectedPathRequest = "/test/request"
        val expectedPayload = "{\"testRequestField\":\"test\"}"
        BDDMockito.given(
                proxyApi.forwardWithHttpInfo(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()
                )
            )
            .willReturn(
                Mono.just(
                    ResponseEntity.ok().header("X-Request-Id", requestId).body(expectedPayload)
                )
            )
        // test
        StepVerifier.create(
                nodeForwarderClient.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    RefundResponseDto::class.java
                )
            )
            .expectErrorMatches { ex: Throwable ->
                Assertions.assertNotNull(ex.message)
                ex.message?.let {
                    Assertions.assertTrue(
                        it.contains(
                            "Unexpected error while invoking proxyRequest: Unrecognized field \"testRequestField\""
                        )
                    )
                }

                Assertions.assertTrue(ex is NodeForwarderClientException)
                true
            }
            .verify()
        Mockito.verify(proxyApi, Mockito.times(1))
            .forwardWithHttpInfo(
                expectedHostHeader,
                expectedPortHeader,
                expectedPathRequest,
                requestId,
                "{\"idTransaction\":\"ecf06892c9e04ae39626dfdfda631b94\",\"idPSPTransaction\":\"5f521592f3d84ffa8d8f68651da91144\",\"action\":\"refund\"}"
            )
    }

    @Test
    @Throws(Exception::class)
    fun `should send request to forwarder with all required headers`() {
        // assertions
        val requestId = UUID.randomUUID().toString()
        val apiKey = "apiKey"
        val client: NodeForwarderClient<RefundRequestDto, RefundResponseDto> =
            NodeForwarderClient(
                apiKey,
                """http://${mockWebServer!!.hostName}:${mockWebServer!!.port}""",
                10000,
                10000
            )
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("http://localhost:123/test/request")
        mockWebServer!!.enqueue(
            MockResponse()
                .addHeader("X-Request-Id", requestId)
                .setBody(
                    "{\"idTransaction\":\"ecf06892c9e04ae39626dfdfda631b94\",\"outcome\":\"OK\"}"
                )
                .setResponseCode(200)
        )
        val expectedResponse: NodeForwarderResponse<RefundResponseDto> =
            NodeForwarderResponse(
                RefundResponseDto()
                    .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                    .outcome(RefundOutcomeDto.OK),
                Optional.of(requestId)
            )
        // test
        StepVerifier.create(
                client.proxyRequest(testRequest, proxyTo, requestId, RefundResponseDto::class.java)
            )
            .expectNext(expectedResponse)
            .verifyComplete()
        // assertions
        val recordedRequest = mockWebServer!!.takeRequest()
        val requestHeaders = recordedRequest.headers
        Assertions.assertEquals(requestId, requestHeaders["x-request-id"])
        Assertions.assertEquals("localhost", requestHeaders["x-host-url"])
        Assertions.assertEquals("123", requestHeaders["x-host-port"])
        Assertions.assertEquals("/test/request", requestHeaders["x-host-path"])
        Assertions.assertEquals(apiKey, requestHeaders["Ocp-Apim-Subscription-Key"])
    }

    @Test
    fun `should handle missing xRequestId response header`() {
        // assertions
        val requestId = UUID.randomUUID().toString()
        val client: NodeForwarderClient<RefundRequestDto, RefundResponseDto> =
            NodeForwarderClient(
                "apiKey",
                """http://${mockWebServer!!.hostName}:${mockWebServer!!.port}""",
                10000,
                10000
            )
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("http://localhost:123/test/request")
        mockWebServer!!.enqueue(
            MockResponse()
                .setBody(
                    "{\"idTransaction\":\"ecf06892c9e04ae39626dfdfda631b94\",\"outcome\":\"OK\"}"
                )
                .setResponseCode(200)
        )
        val expectedResponse: NodeForwarderResponse<RefundResponseDto> =
            NodeForwarderResponse(
                RefundResponseDto()
                    .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                    .outcome(RefundOutcomeDto.OK),
                Optional.empty()
            )
        // test
        StepVerifier.create(
                client.proxyRequest(testRequest, proxyTo, requestId, RefundResponseDto::class.java)
            )
            .expectNext(expectedResponse)
            .verifyComplete()
    }

    @Test
    fun `should handle error response from forwarder`() {
        // assertions
        val requestId = UUID.randomUUID().toString()
        val client: NodeForwarderClient<RefundRequestDto, RefundResponseDto> =
            NodeForwarderClient(
                "apiKey",
                """http://${mockWebServer!!.hostName}:${mockWebServer!!.port}""",
                10000,
                10000
            )
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("http://localhost:123/test/request")
        mockWebServer!!.enqueue(MockResponse().setBody("error").setResponseCode(400))
        // test
        StepVerifier.create(
                client.proxyRequest(testRequest, proxyTo, requestId, RefundResponseDto::class.java)
            )
            .expectError(NodeForwarderClientException::class.java)
            .verify()
    }

    @Test
    fun `should handle JsonProcessingException when serializing request`() {
        val requestId = UUID.randomUUID().toString()
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("http://localhost:123/test/request")

        // ObjectMapper mock
        val objectMapperMock = Mockito.mock(ObjectMapper::class.java)
        val nodeForwarderClientWithMockedMapper =
            NodeForwarderClient<RefundRequestDto, RefundResponseDto>(proxyApi)

        // refelction for change objectMapper with mock
        val field = NodeForwarderClient::class.java.getDeclaredField("objectMapper")
        field.isAccessible = true
        field.set(nodeForwarderClientWithMockedMapper, objectMapperMock)

        Mockito.`when`(objectMapperMock.writeValueAsString(testRequest))
            .thenThrow(JsonProcessingException::class.java)

        // Test
        StepVerifier.create(
                nodeForwarderClientWithMockedMapper.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    RefundResponseDto::class.java
                )
            )
            .expectErrorMatches { ex ->
                ex is NodeForwarderClientException &&
                    ex.description.contains("Unexpected error while invoking proxyRequest")
            }
            .verify()

        Mockito.verify(proxyApi, Mockito.times(0))
            .forwardWithHttpInfo(any(), any(), any(), any(), any())
    }

    @Test
    fun `should handle non-WebClientResponseException in exceptionToNodeForwarderClientException`() {
        val requestId = UUID.randomUUID().toString()
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("http://localhost:123/test/request")

        Mockito.`when`(proxyApi.forwardWithHttpInfo(any(), any(), any(), any(), any()))
            .thenReturn(Mono.error(RuntimeException("Some error")))

        // Test
        StepVerifier.create(
                nodeForwarderClient.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    RefundResponseDto::class.java
                )
            )
            .expectErrorMatches { ex ->
                ex is NodeForwarderClientException &&
                    ex.description.contains(
                        "Unexpected error while invoking proxyRequest: Some error"
                    )
            }
            .verify()
    }

    @ParameterizedTest
    @MethodSource("provideErrorResponses")
    fun `should map NodeForwarder exceptions correctly`(
        statusCode: HttpStatus,
        expectedDescription: String,
        expectedHttpStatusCode: HttpStatus
    ) {
        val requestId = UUID.randomUUID().toString()
        val testRequest =
            RefundRequestDto()
                .idTransaction("ecf06892c9e04ae39626dfdfda631b94")
                .idPSPTransaction("5f521592f3d84ffa8d8f68651da91144")
                .action("refund")
        val proxyTo = URI.create("http://localhost:123/test/request")
        val responseBody =
            "{\"errors\":[{\"code\":\"ERROR_CODE\",\"description\":\"Error description\"}]}"

        val exception =
            WebClientResponseException.create(
                statusCode.value(),
                statusCode.reasonPhrase,
                HttpHeaders.EMPTY,
                responseBody.toByteArray(),
                null
            )
        Mockito.`when`(proxyApi.forwardWithHttpInfo(any(), any(), any(), any(), any()))
            .thenReturn(Mono.error(exception))

        // Test
        StepVerifier.create(
                nodeForwarderClient.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    RefundResponseDto::class.java
                )
            )
            .expectErrorMatches { ex ->
                ex is NodeForwarderClientException &&
                    ex.description == expectedDescription &&
                    ex.httpStatusCode == expectedHttpStatusCode
            }
            .verify()
    }

    companion object {
        private var mockWebServer: MockWebServer? = null

        @JvmStatic
        fun provideErrorResponses(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    HttpStatus.BAD_REQUEST,
                    "Bad request to Node Forwarder",
                    HttpStatus.INTERNAL_SERVER_ERROR
                ),
                Arguments.of(
                    HttpStatus.UNAUTHORIZED,
                    "Misconfigured Node Forwarder API key",
                    HttpStatus.INTERNAL_SERVER_ERROR
                ),
                Arguments.of(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Node Forwarder internal server error",
                    HttpStatus.BAD_GATEWAY
                ),
                Arguments.of(
                    HttpStatus.NOT_FOUND,
                    "Node Forwarder resource not found",
                    HttpStatus.BAD_GATEWAY
                ),
                Arguments.of(
                    HttpStatus.FORBIDDEN,
                    "Node Forwarder server error: 403 FORBIDDEN",
                    HttpStatus.BAD_GATEWAY
                )
            )
        }
    }
}
