package it.pagopa.helpdeskcommands.utils

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import it.pagopa.generated.npg.model.ClientErrorDto
import it.pagopa.generated.npg.model.ServerErrorDto
import java.io.IOException
import org.springframework.web.reactive.function.client.WebClientResponseException

object ErrorResponseUtils {
    /**
     * Parse and format the error response from WebClientResponseException.
     *
     * @param err The exception thrown by WebClient when an error occurs.
     * @param objectMapper The ObjectMapper used to deserialize the response body.
     * @return A list of formatted error strings.
     */
    @Throws(IOException::class)
    fun parseResponseErrors(
        err: WebClientResponseException,
        objectMapper: ObjectMapper
    ): List<String> {
        return when (err.statusCode.value()) {
            INTERNAL_SERVER_ERROR.code() ->
                objectMapper
                    .readValue(err.responseBodyAsByteArray, ServerErrorDto::class.java)
                    .errors
            BAD_REQUEST.code() ->
                objectMapper
                    .readValue(err.responseBodyAsByteArray, ClientErrorDto::class.java)
                    .errors
            else -> emptyList()
        }?.mapNotNull { "[${it.code}] ${it.description}" } ?: emptyList()
    }
}
