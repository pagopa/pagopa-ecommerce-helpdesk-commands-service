package it.pagopa.helpdeskcommands.exceptions

import org.springframework.http.HttpStatus

class NpgClientException(
    val description: String,
    val httpStatusCode: HttpStatus,
    val errors: List<String>
) : ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatus = httpStatusCode,
            description = if (errors.isEmpty()) "Not available" else errors.joinToString(". "),
            title = "Npg Invocation exception - $description"
        )
}
