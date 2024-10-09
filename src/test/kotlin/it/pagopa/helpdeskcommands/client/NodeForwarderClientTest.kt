package it.pagopa.helpdeskcommands.client

import com.fasterxml.jackson.core.JsonProcessingException
import it.pagopa.generated.nodeforwarder.v1.dto.ProxyApi
import it.pagopa.helpdeskcommands.client.NodeForwarderClient.NodeForwarderResponse
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import java.io.IOException
import java.net.URI
import java.util.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class NodeForwarderClientTest {

    private data class TestRequest(val testRequestField: String)

    private data class TestResponse(val testResponseField: String)

    private val proxyApi: ProxyApi = Mockito.mock(ProxyApi::class.java)

    private val nodeForwarderClient: NodeForwarderClient<TestRequest, TestResponse> =
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
        val testRequest = TestRequest("test")
        val proxyTo = URI.create("https://localhost/test/request")
        val expectedHostHeader = "localhost"
        val expectedPortHeader = 443
        val expectedPathRequest = "/test/request"
        val expectedPayload = "{\"testRequestField\":\"test\"}"
        val expectedResponse: NodeForwarderResponse<TestResponse> =
            NodeForwarderResponse(TestResponse("123"), Optional.of(requestId))
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
                        .body("{\"testResponseField\":\"123\"}")
                )
            )
        // test
        StepVerifier.create(
                nodeForwarderClient.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    TestResponse::class.java
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
        val testRequest = TestRequest("test")
        val proxyTo = URI.create("http://localhost:123/test/request")
        val expectedHostHeader = "localhost"
        val expectedPortHeader = 123
        val expectedPathRequest = "/test/request"
        val expectedPayload = "{\"testRequestField\":\"test\"}"
        val expectedResponse: NodeForwarderResponse<TestResponse> =
            NodeForwarderResponse(TestResponse("123"), Optional.of(requestId))
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
                        .body("{\"testResponseField\":\"123\"}")
                )
            )
        // test
        StepVerifier.create(
                nodeForwarderClient.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    TestResponse::class.java
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
        val testRequest = TestRequest("test")
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
            .willReturn(Mono.just(ResponseEntity.ok().header("X-Request-Id", requestId).body("{}")))
        // test
        StepVerifier.create(
                nodeForwarderClient.proxyRequest(
                    testRequest,
                    proxyTo,
                    requestId,
                    TestResponse::class.java
                )
            )
            .expectErrorMatches { ex: Throwable ->
                Assertions.assertEquals("Error deserializing body", ex.message)
                Assertions.assertTrue(ex.cause is JsonProcessingException)
                true
            }
            .verify()
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
    @Throws(Exception::class)
    fun `should send request to forwarder with all required headers`() {
        // assertions
        val requestId = UUID.randomUUID().toString()
        val apiKey = "apiKey"
        val client: NodeForwarderClient<TestRequest, TestResponse> =
            NodeForwarderClient(
                apiKey,
                """http://${mockWebServer!!.hostName}:${mockWebServer!!.port}""",
                10000,
                10000
            )
        val testRequest = TestRequest("test")
        val proxyTo = URI.create("http://localhost:123/test/request")
        mockWebServer!!.enqueue(
            MockResponse()
                .addHeader("X-Request-Id", requestId)
                .setBody("{\"testResponseField\":\"123\"}")
                .setResponseCode(200)
        )
        val expectedResponse: NodeForwarderResponse<TestResponse> =
            NodeForwarderResponse(TestResponse("123"), Optional.of(requestId))
        // test
        StepVerifier.create(
                client.proxyRequest(testRequest, proxyTo, requestId, TestResponse::class.java)
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
        val client: NodeForwarderClient<TestRequest, TestResponse> =
            NodeForwarderClient(
                "apiKey",
                """http://${mockWebServer!!.hostName}:${mockWebServer!!.port}""",
                10000,
                10000
            )
        val testRequest = TestRequest("test")
        val proxyTo = URI.create("http://localhost:123/test/request")
        mockWebServer!!.enqueue(
            MockResponse().setBody("{\"testResponseField\":\"123\"}").setResponseCode(200)
        )
        val expectedResponse: NodeForwarderResponse<TestResponse> =
            NodeForwarderResponse(TestResponse("123"), Optional.empty())
        // test
        StepVerifier.create(
                client.proxyRequest(testRequest, proxyTo, requestId, TestResponse::class.java)
            )
            .expectNext(expectedResponse)
            .verifyComplete()
    }

    @Test
    fun `should handle error response from forwarder`() {
        // assertions
        val requestId = UUID.randomUUID().toString()
        val client: NodeForwarderClient<TestRequest, TestResponse> =
            NodeForwarderClient(
                "apiKey",
                """http://${mockWebServer!!.hostName}:${mockWebServer!!.port}""",
                10000,
                10000
            )
        val testRequest = TestRequest("test")
        val proxyTo = URI.create("http://localhost:123/test/request")
        mockWebServer!!.enqueue(MockResponse().setBody("error").setResponseCode(400))
        // test
        StepVerifier.create(
                client.proxyRequest(testRequest, proxyTo, requestId, TestResponse::class.java)
            )
            .expectError(NodeForwarderClientException::class.java)
            .verify()
    }

    companion object {
        private var mockWebServer: MockWebServer? = null
    }
}
