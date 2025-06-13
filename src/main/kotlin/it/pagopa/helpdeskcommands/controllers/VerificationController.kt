package it.pagopa.helpdeskcommands.controllers

import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedData
import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptData
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import it.pagopa.ecommerce.commons.generated.server.model.TransactionStatusDto
import it.pagopa.helpdeskcommands.services.TransactionEventServiceInterface
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

/**
 * Minimal test controller for verifying production TransactionEventService
 *
 * TODO: Remove before production deployment
 */
@RestController
@RequestMapping("/verify")
class VerificationController(
    private val transactionEventService: TransactionEventServiceInterface
) {

    @PostMapping("/refund/{transactionId}")
    fun verifyRefundEvent(@PathVariable transactionId: String): Mono<String> {
        val event =
            TransactionRefundRequestedEvent(
                transactionId,
                TransactionRefundRequestedData(
                    TransactionStatusDto.CLOSED
                )
            )

        return transactionEventService
            .sendRefundRequestedEvent(event)
            .thenReturn("✅ SUCCESS: Refund event sent for transaction: $transactionId")
            .onErrorReturn("❌ ERROR: Failed to send refund event")
    }

    @PostMapping("/notification/{transactionId}")
    fun verifyNotificationEvent(@PathVariable transactionId: String): Mono<String> {
        val event =
            TransactionUserReceiptRequestedEvent(
                transactionId,
                TransactionUserReceiptData(
                    TransactionUserReceiptData.Outcome.OK,
                    "IT",
                    "2024-06-05T10:00:00",
                    TransactionUserReceiptData.NotificationTrigger.MANUAL
                )
            )

        return transactionEventService
            .sendNotificationRequestedEvent(event)
            .thenReturn("✅ SUCCESS: Notification event sent for transaction: $transactionId")
            .onErrorReturn("❌ ERROR: Failed to send notification event")
    }

    @GetMapping("/status")
    fun getStatus(): Map<String, String> {
        return mapOf(
            "service" to transactionEventService.javaClass.simpleName,
            "status" to "Production service verification endpoint active"
        )
    }
}
