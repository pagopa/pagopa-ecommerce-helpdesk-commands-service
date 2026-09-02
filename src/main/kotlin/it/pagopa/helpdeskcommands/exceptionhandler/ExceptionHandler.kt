package it.pagopa.helpdeskcommands.exceptionhandler

import it.pagopa.ecommerce.commons.exceptions.RedirectConfigurationException
import it.pagopa.ecommerce.commons.mdcutilities.LogTracingUtils
import it.pagopa.generated.helpdeskcommands.model.ProblemJsonDto
import it.pagopa.helpdeskcommands.exceptions.ApiError
import it.pagopa.helpdeskcommands.exceptions.InvalidTransactionStatusException
import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyConfigurationException
import it.pagopa.helpdeskcommands.exceptions.RestApiException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import jakarta.validation.ConstraintViolationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException

/**
 * Exception handler used to output a custom message in case an incoming request is invalid or an
 * api encounter an error and throw an RestApiException
 */
@RestControllerAdvice
class ExceptionHandler {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    /** RestApiException exception handler */
    @ExceptionHandler(RestApiException::class)
    fun handleException(e: RestApiException): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils()
            .failure()
            .logError(logger, e, "Exception processing request")
        return ResponseEntity.status(e.httpStatus)
            .body(
                ProblemJsonDto().status(e.httpStatus.value()).title(e.title).detail(e.description)
            )
    }

    /** ApiError exception handler */
    @ExceptionHandler(ApiError::class)
    fun handleException(e: ApiError): ResponseEntity<ProblemJsonDto> {
        return handleException(e.toRestException())
    }

    /** Validation request exception handler */
    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        MethodArgumentTypeMismatchException::class,
        ServerWebInputException::class,
        HttpMessageNotReadableException::class,
        WebExchangeBindException::class,
        ConstraintViolationException::class,
        IllegalArgumentException::class
    )
    fun handleRequestValidationException(
        e: Exception,
        exchange: ServerWebExchange?
    ): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils()
            .failure()
            .logError(logger, e, "Input request is not valid")
        return ResponseEntity.badRequest()
            .body(
                ProblemJsonDto()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .title("Input request is not valid")
                    .detail(e.message)
            )
    }

    /** NpgApiKeyConfigurationException exception handler */
    @ExceptionHandler(NpgApiKeyConfigurationException::class)
    fun handleNpgApikeyException(
        e: NpgApiKeyConfigurationException
    ): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils()
            .failure()
            .logError(logger, e, "Exception retrieving apikey")
        return ResponseEntity.badRequest()
            .body(
                ProblemJsonDto()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .title("Exception retrieving apikey")
                    .detail(e.message)
            )
    }

    /** RedirectConfigurationException handler */
    @ExceptionHandler(RedirectConfigurationException::class)
    fun handleRedirectConfigurationException(
        e: RedirectConfigurationException
    ): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils()
            .failure()
            .logError(logger, e, "Exception retrieving redirect PSP configuration type")
        return ResponseEntity.badRequest()
            .body(
                ProblemJsonDto()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .title("Exception retrieving configuration type")
                    .detail(e.message)
            )
    }

    @ExceptionHandler(TransactionNotFoundException::class)
    fun handleTransactionNotFoundException(
        e: TransactionNotFoundException
    ): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils().failure().logError(logger, e, "Transaction not found")
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ProblemJsonDto()
                    .status(HttpStatus.NOT_FOUND.value())
                    .title("Transaction not found")
                    .detail(e.message)
            )
    }

    @ExceptionHandler(InvalidTransactionStatusException::class)
    fun handleInvalidTransactionStatusException(
        e: InvalidTransactionStatusException
    ): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils()
            .failure()
            .logError(logger, e, "Invalid transaction status")
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(
                ProblemJsonDto()
                    .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                    .title("Invalid transaction status")
                    .detail(e.message)
            )
    }

    /** Handler for generic exception */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils()
            .failure()
            .logErrorWithStackTrace(logger, e, "Exception processing the request")
        return ResponseEntity.internalServerError()
            .body(
                ProblemJsonDto()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .title("Error processing the request")
                    .detail("An internal error occurred processing the request")
            )
    }
}
