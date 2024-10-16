package it.pagopa.helpdeskcommands

import it.pagopa.generated.helpdeskcommands.model.ProblemJsonDto
import it.pagopa.generated.helpdeskcommands.model.RefundRedirectRequestDto
import it.pagopa.generated.helpdeskcommands.model.RefundTransactionRequestDto
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import java.util.*
import org.springframework.http.HttpStatus

object HelpDeskCommandsTestUtils {

    val CARDS_PSP_ID = "CIPBITMM"
    val REDIRECT_PSP_ID = "REDIRECT"
    val TRANSACTION_ID = "9549d38941184b5eb5dfab90aaf3a6d7"
    val TOUCHPOINT = "5f521592f3d84ffa8d8f68651da91144"
    val PSP_TRANSACTION_ID = ""
    val ACTION = "refund"

    val CREATE_REFUND_TRANSACTION_REQUEST =
        RefundTransactionRequestDto()
            .transactionId(TRANSACTION_ID)
            .paymentMethodName(PaymentMethod.CARDS.serviceName)
            .pspId(CARDS_PSP_ID)
            .operationId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .amount(200)

    val CREATE_REFUND_REDIRECT_REQUEST =
        RefundRedirectRequestDto()
            .idTransaction(TRANSACTION_ID)
            .touchpoint(TOUCHPOINT)
            .pspId(REDIRECT_PSP_ID)
            .idPSPTransaction(PSP_TRANSACTION_ID)
            .paymentTypeCode(PaymentMethod.RBPR.serviceName)
            .action(ACTION)

    fun buildProblemJson(
        httpStatus: HttpStatus,
        title: String,
        description: String
    ): ProblemJsonDto = ProblemJsonDto().status(httpStatus.value()).detail(description).title(title)
}
