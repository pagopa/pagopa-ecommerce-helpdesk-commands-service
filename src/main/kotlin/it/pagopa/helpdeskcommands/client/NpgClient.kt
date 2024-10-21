package it.pagopa.helpdeskcommands.client

import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.RefundRequestDto
import it.pagopa.generated.npg.model.RefundResponseDto
import it.pagopa.helpdeskcommands.exceptions.NpgClientException
import it.pagopa.helpdeskcommands.utils.ErrorResponseUtils
import it.pagopa.helpdeskcommands.utils.PaymentConstants
import java.io.IOException
import java.math.BigDecimal
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

/** NPG API client service class */
@Component
class NpgClient(
    @Autowired private val npgWebClient: PaymentServicesApi,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * method to request the payment refund using a sessionId passed as input.
     *
     * @param correlationId the unique id to identify the rest api invocation
     * @param operationId the unique id used to identify a payment operation
     * @param idempotenceKey the idempotenceKey used to identify a refund request for the same
     *   transaction
     * @param grandTotal the grand total to be refunded
     * @param description the description of the refund request. Not mandatory.
     * @return An object containing the state of the transaction and the info about operation
     *   details.
     */
    fun refundPayment(
        apikey: String,
        correlationId: UUID,
        operationId: String,
        idempotenceKey: UUID,
        grandTotal: BigDecimal,
        description: String?
    ): Mono<RefundResponseDto> {
        return npgWebClient
            .pspApiV1OperationsOperationIdRefundsPost(
                operationId,
                correlationId,
                apikey,
                idempotenceKey.toString(),
                RefundRequestDto()
                    .amount(grandTotal.toString())
                    .currency(PaymentConstants.EUR_CURRENCY)
                    .description(description)
            )
            .doOnError(WebClientResponseException::class.java) {
                logger.error(
                    "Error communicating with NPG-refund for correlationId [{}] - response: [{}]",
                    correlationId,
                    it.responseBodyAsString,
                    it
                )
            }
            .onErrorMap { error -> exceptionToNpgResponseException(error) }
    }

    private fun exceptionToNpgResponseException(err: Throwable): NpgClientException {
        if (err is WebClientResponseException) {
            try {
                var responseErrors = ErrorResponseUtils.parseResponseErrors(err, objectMapper)
                if (responseErrors.isEmpty()) responseErrors = listOf(err.responseBodyAsString)
                logger.error("Npg error codes: [{}]", responseErrors)
                return mapNpgException(err.statusCode, responseErrors)
            } catch (ex: IOException) {
                return NpgClientException(
                    description =
                        "Invalid error response from NPG with status code ${err.statusCode}",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    errors = emptyList()
                )
            }
        }
        return NpgClientException(
            "Unexpected error while invoke method for refund: ${err.message}",
            HttpStatus.INTERNAL_SERVER_ERROR,
            errors = emptyList()
        )
    }

    private fun mapNpgException(
        statusCode: HttpStatusCode,
        errors: List<String>
    ): NpgClientException =
        when (statusCode) {
            HttpStatus.BAD_REQUEST ->
                NpgClientException(
                    description = "Bad request",
                    httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                    errors
                )
            HttpStatus.UNAUTHORIZED ->
                NpgClientException(
                    description = "Misconfigured NPG api key",
                    httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                    errors
                )
            HttpStatus.INTERNAL_SERVER_ERROR ->
                NpgClientException(
                    description = "NPG internal server error",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    errors
                )
            HttpStatus.NOT_FOUND ->
                NpgClientException(
                    description = "NPG transaction not found",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    errors
                )
            else ->
                NpgClientException(
                    description = "NPG server error: $statusCode",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    errors
                )
        }
}
