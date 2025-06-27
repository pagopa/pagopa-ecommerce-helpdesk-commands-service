package it.pagopa.helpdeskcommands.exceptions

import org.springframework.http.HttpStatus

/** Exception thrown when an error occurs communicating with Node Forwarder */
class NodeForwarderClientException(
    val description: String,
    val httpStatusCode: HttpStatus,
    val errors: List<String>,
) : ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatus = httpStatusCode,
            description = if (errors.isEmpty()) "Not available" else errors.joinToString(". "),
            title = "Forwarder Invocation exception - $description",
        )
}
