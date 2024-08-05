package it.pagopa.helpdeskcommands.exceptions

import org.springframework.http.HttpStatus

class NpgClientException(val description: String, val httpStatusCode: HttpStatus) :
    ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatus = httpStatusCode,
            description = description,
            title = "Npg Invocation exception"
        )
}
