package it.pagopa.helpdeskcommands.exceptions

import it.pagopa.helpdeskcommands.utils.TransactionId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.BAD_GATEWAY)
class RefundNotAllowedException(
    transactionID: TransactionId,
    errorMessage: String = "N/A",
    cause: Throwable? = null,
) :
    RuntimeException(
        "Transaction with id ${transactionID.value()} cannot be refunded. Reason: $errorMessage",
        cause,
    )
