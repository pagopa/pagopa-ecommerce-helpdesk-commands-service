package it.pagopa.helpdeskcommands.client

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import it.pagopa.generated.nodeforwarder.v1.ApiClient
import it.pagopa.generated.nodeforwarder.v1.dto.ProxyApi
import it.pagopa.helpdeskcommands.exceptions.NodeForwarderClientException
import it.pagopa.helpdeskcommands.utils.ErrorResponseUtils
import java.io.IOException
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.netty.Connection
import reactor.netty.http.client.HttpClient

/**
 * Node forwarder api client implementation
 *
 * @param <T> the request to proxy POJO class type
 * @param <R> the expected body POJO class type
 * @see ProxyApi </R></T>
 */
class NodeForwarderClient<T, R> {
    private val proxyApiClient: ProxyApi
    private val logger = LoggerFactory.getLogger(javaClass)

    private val objectMapper: ObjectMapper =
        ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

    /**
     * Node forward response
     *
     * @param body the parsed body
     * @param requestId the received request id
     * @param <R> type parameter for body POJO class type </R>
     */
    data class NodeForwarderResponse<R>(val body: R, val requestId: Optional<String>)

    /**
     * Build a new instance for this Node Forwarder Client
     *
     * @param apiKey the node forwarder api key
     * @param backendUrl the node forwarder backend URL
     * @param readTimeout the node forwarder read timeout
     * @param connectionTimeout the node forwarder connection timeout
     */
    constructor(apiKey: String, backendUrl: String, readTimeout: Int, connectionTimeout: Int) {
        this.proxyApiClient = initializeClient(apiKey, backendUrl, readTimeout, connectionTimeout)
    }

    /**
     * Build a new NodeForwarderClient instance with the using the input proxuApiClient instance
     *
     * @param proxyApiClient the api client instance
     */
    internal constructor(proxyApiClient: ProxyApi) {
        this.proxyApiClient = proxyApiClient
    }

    /**
     * Build a new [ProxyApi] that will be used to perform api calls to be forwarded
     *
     * @return the initialized api client instance
     */
    private fun initializeClient(
        apiKey: String,
        backendUrl: String,
        readTimeout: Int,
        connectionTimeout: Int
    ): ProxyApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .doOnConnected { connection: Connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.MILLISECONDS)
                    )
                }

        val webClient: WebClient =
            ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(backendUrl)
                .defaultHeader(API_KEY_REQUEST_HEADER_KEY, apiKey)
                .build()

        val apiClient: ApiClient = ApiClient(webClient).setBasePath(backendUrl)
        apiClient.setApiKey(apiKey)
        return ProxyApi(apiClient)
    }

    /**
     * Proxy the input request to the proxyTo destination
     *
     * @param request the request to proxy
     * @param proxyTo the destination URL where proxy request to
     * @param requestId an optional request id that
     * @param responseClass the response class
     * @return the parsed response body or a Mono error with causing error code
     */
    fun proxyRequest(
        request: T,
        proxyTo: URI,
        requestId: String?,
        responseClass: Class<R>?
    ): Mono<NodeForwarderResponse<R>> {
        Objects.requireNonNull(request)
        Objects.requireNonNull(proxyTo)
        val requestPayload: String
        try {
            requestPayload = objectMapper.writeValueAsString(request)
        } catch (e: JsonProcessingException) {
            return Mono.error(exceptionToNodeForwarderClientException(e))
        }
        val hostName = proxyTo.host
        var port = proxyTo.port
        if (port == -1) {
            port = 443
        }
        val path = proxyTo.path
        logger.info(
            "Sending request to node forwarder. hostName: [{}], port: [{}], path: [{}], requestId: [{}]",
            hostName,
            port,
            path,
            requestId
        )
        return proxyApiClient
            .forwardWithHttpInfo(hostName, port, path, requestId, requestPayload)
            .flatMap { response ->
                try {
                    Mono.just(
                        NodeForwarderResponse(
                            objectMapper.readValue(response.body, responseClass),
                            Optional.ofNullable(response.headers.getFirst(REQUEST_ID_HEADER_VALUE))
                        )
                    )
                } catch (e: JsonProcessingException) {
                    Mono.error(exceptionToNodeForwarderClientException(e))
                }
            }
            .doOnError(WebClientResponseException::class.java) {
                logger.error(
                    "Error communicating with Node forwarder\nError response code: [{}], body: [{}]",
                    it.statusCode,
                    it.responseBodyAsString
                )
            }
            .onErrorMap { error -> exceptionToNodeForwarderClientException(error) }
    }

    /**
     * Map exceptions to NodeForwarderClientException with appropriate logging
     *
     * @param err the Throwable to map
     * @return a NodeForwarderClientException with detailed error information
     */
    private fun exceptionToNodeForwarderClientException(
        err: Throwable
    ): NodeForwarderClientException {
        if (err is WebClientResponseException) {
            try {
                var responseErrors = ErrorResponseUtils.parseResponseErrors(err, objectMapper)
                if (responseErrors.isEmpty()) responseErrors = listOf(err.responseBodyAsString)
                logger.error("Forwarder error codes: [{}]", responseErrors)
                return mapNodeForwarderException(err.statusCode, responseErrors)
            } catch (ex: IOException) {
                return NodeForwarderClientException(
                    description =
                        "Invalid error response from forwarder with status code ${err.statusCode}",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    errors = emptyList()
                )
            }
        }
        return NodeForwarderClientException(
            description = "Unexpected error while invoking proxyRequest: ${err.message}",
            httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
            errors = emptyList()
        )
    }

    /**
     * Map HTTP status codes to NodeForwarderClientException with appropriate descriptions
     *
     * @param statusCode the HTTP status code returned by the Node Forwarder
     * @param errors the list of error messages
     * @return a NodeForwarderClientException with the mapped error details
     */
    private fun mapNodeForwarderException(
        statusCode: HttpStatusCode,
        errors: List<String>
    ): NodeForwarderClientException =
        when (statusCode) {
            HttpStatus.BAD_REQUEST ->
                NodeForwarderClientException(
                    description = "Bad request to Node Forwarder",
                    httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                    errors = errors
                )
            HttpStatus.UNAUTHORIZED ->
                NodeForwarderClientException(
                    description = "Misconfigured Node Forwarder API key",
                    httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                    errors = errors
                )
            HttpStatus.INTERNAL_SERVER_ERROR ->
                NodeForwarderClientException(
                    description = "Node Forwarder internal server error",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    errors = errors
                )
            HttpStatus.NOT_FOUND ->
                NodeForwarderClientException(
                    description = "Node Forwarder resource not found",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    errors = errors
                )
            else ->
                NodeForwarderClientException(
                    description = "Node Forwarder server error: $statusCode",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    errors = errors
                )
        }

    companion object {
        /** Node forwarder api key header */
        private const val API_KEY_REQUEST_HEADER_KEY = "Ocp-Apim-Subscription-Key"

        /** Header that contains unique request id */
        private const val REQUEST_ID_HEADER_VALUE = "X-Request-Id"
    }
}
