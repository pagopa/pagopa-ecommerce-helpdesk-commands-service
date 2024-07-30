package it.pagopa.helpdeskcommands.exceptions

/**
 * Class that bridges business-related exception to `RestException`. Business-related exceptions
 * should extend this class.
 */
abstract class ApiError(message: String?) : RuntimeException(message) {
    /** Convert this ApiError to RestApiException */
    abstract fun toRestException(): RestApiException
}
