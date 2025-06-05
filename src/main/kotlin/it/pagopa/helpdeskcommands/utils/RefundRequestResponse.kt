package it.pagopa.helpdeskcommands.utils

import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData.RefundTrigger
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto

data class RefundRequestResponse(
    val transactionId: String,
    val status: TransactionStatusDto,
    val refundRequestedAt: String,
    val refundTrigger: RefundTrigger = RefundTrigger.MANUAL
)
