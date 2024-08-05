package it.pagopa.helpdeskcommands

import it.pagopa.generated.helpdeskcommands.model.ProblemJsonDto
import it.pagopa.generated.helpdeskcommands.model.RefundTransactionRequestDto
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import java.util.*
import org.springframework.http.HttpStatus

object HelpDeskCommandsTestUtils {

    val CARDS_PSP_ID = "CIPBITMM"
    val TRANSACTION_ID = "9549d38941184b5eb5dfab90aaf3a6d7"

    val CREATE_REFUND_TRANSACTION_REQUEST =
        RefundTransactionRequestDto()
            .transactionId(TRANSACTION_ID)
            .paymentMethodName(PaymentMethod.CARDS.serviceName)
            .pspId(CARDS_PSP_ID)
            .operationId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .amount(200)

    fun buildProblemJson(
        httpStatus: HttpStatus,
        title: String,
        description: String
    ): ProblemJsonDto = ProblemJsonDto().status(httpStatus.value()).detail(description).title(title)
}
