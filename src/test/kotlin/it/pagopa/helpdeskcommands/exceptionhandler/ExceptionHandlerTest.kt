package it.pagopa.helpdeskcommands.exceptionhandler

import it.pagopa.helpdeskcommands.HelpDeskCommandsTestUtils
import it.pagopa.helpdeskcommands.HelpDeskCommandsTestUtils.TRANSACTION_ID
import it.pagopa.helpdeskcommands.exceptions.*
import it.pagopa.helpdeskcommands.utils.TransactionId
import jakarta.xml.bind.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange

class ExceptionHandlerTest {

    private val exceptionHandler = ExceptionHandler()

    @Test
    fun `Should handle RestApiException`() {
        val response =
            exceptionHandler.handleException(
                RestApiException(
                    httpStatus = HttpStatus.UNAUTHORIZED,
                    title = "title",
                    description = "description"
                )
            )
        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.UNAUTHORIZED,
                title = "title",
                description = "description"
            ),
            response.body
        )
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `Should handle ApiError`() {
        val exception =
            NpgClientException(
                httpStatusCode = HttpStatus.UNAUTHORIZED,
                description = "Api error",
                errors = listOf("[123] Error description")
            )
        assertEquals("Api error", exception.description)
        exception.errors.forEach { assertEquals("[123] Error description", it) }
        assertEquals(HttpStatus.UNAUTHORIZED, exception.httpStatusCode)
        val response = exceptionHandler.handleException(exception)
        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.UNAUTHORIZED,
                title = "Npg Invocation exception - Api error",
                description = "[123] Error description"
            ),
            response.body
        )
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `Should handle api error without errors response`() {
        val exception =
            NpgClientException(
                httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                description = "Generic error",
                errors = emptyList()
            )
        val response = exceptionHandler.handleException(exception)
        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                title = "Npg Invocation exception - Generic error",
                description = "Not available"
            ),
            response.body
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `Should handle ValidationExceptions`() {
        val exception = ValidationException("Invalid request")
        val webExchange = mock<ServerWebExchange>()
        val response = exceptionHandler.handleRequestValidationException(exception, webExchange)
        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.BAD_REQUEST,
                title = "Input request is not valid",
                description = "Invalid request"
            ),
            response.body
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `Should handle NpgApiKeyConfigurationException`() {
        val exception =
            NpgApiKeyConfigurationException("Cannot retrieve api key for payment method: [CARDS]")
        val response = exceptionHandler.handleNpgApikeyException(exception)
        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.BAD_REQUEST,
                title = "Exception retrieving apikey",
                description = "Cannot retrieve api key for payment method: [CARDS]"
            ),
            response.body
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `Should handle RedirectConfigurationException`() {
        val exception =
            RedirectConfigurationException(
                "Cannot retrieve redirect PSP configuration for type: [BACKEND_URLS]"
            )
        val response = exceptionHandler.handleRedirectConfigurationException(exception)
        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.BAD_REQUEST,
                title = "Exception retrieving configuration type",
                description = "Cannot retrieve redirect PSP configuration for type: [BACKEND_URLS]"
            ),
            response.body
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `Should handle generic exception`() {
        val exception = NullPointerException("Nullpointer exception")
        val response = exceptionHandler.handleGenericException(exception)
        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                title = "Error processing the request",
                description = "An internal error occurred processing the request"
            ),
            response.body
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `Should handle RefundNotAllowedException`() {
        val transactionId = TransactionId(TRANSACTION_ID)
        val errorMessage = "N/A"
        val cause = Throwable("Underlying cause")
        val exception = RefundNotAllowedException(transactionId, errorMessage, cause)
        val response = exceptionHandler.handleGenericException(exception)

        val expectedMessage = "An internal error occurred processing the request"

        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                title = "Error processing the request",
                description = expectedMessage
            ),
            response.body
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `Should handle RefundNotAllowedException widouth cause and errorMessage`() {
        val transactionId = TransactionId(TRANSACTION_ID)
        val exception = RefundNotAllowedException(transactionId)
        val response = exceptionHandler.handleGenericException(exception)

        val expectedMessage = "An internal error occurred processing the request"

        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                title = "Error processing the request",
                description = expectedMessage
            ),
            response.body
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `Should handle BadGatewayException`() {
        val detail = "Service unavailable"
        val exception = BadGatewayException(detail)
        val response = exceptionHandler.handleGenericException(exception)

        val expectedMessage = "An internal error occurred processing the request"

        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                title = "Error processing the request",
                description = expectedMessage
            ),
            response.body
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `Should handle NodeForwarderClientException`() {
        val description = "Error communicating with node forwarder"
        val httpStatusCode = HttpStatus.BAD_GATEWAY
        val errors = listOf("Timeout occurred", "Invalid response")
        val exception = NodeForwarderClientException(description, httpStatusCode, errors)
        val response = exceptionHandler.handleException(exception)

        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.BAD_GATEWAY,
                title = "Forwarder Invocation exception - Error communicating with node forwarder",
                description = "Timeout occurred. Invalid response"
            ),
            response.body
        )
        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
    }

    @Test
    fun `Should handle TransactionNotFoundException`() {
        val transactionId = TransactionId(TRANSACTION_ID)
        val exception = TransactionNotFoundException(transactionId.value())
        val response = exceptionHandler.handleTransactionNotFoundException(exception)

        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.NOT_FOUND,
                title = "Transaction not found",
                description = TRANSACTION_ID
            ),
            response.body
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `Should handle InvalidTransactionStatusException`() {
        val transactionId = TransactionId(TRANSACTION_ID)
        val exception = InvalidTransactionStatusException(transactionId.value())
        val response = exceptionHandler.handleInvalidTransactionStatusException(exception)

        assertEquals(
            HelpDeskCommandsTestUtils.buildProblemJson(
                httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
                title = "Invalid transaction status",
                description = TRANSACTION_ID
            ),
            response.body
        )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
    }
}
