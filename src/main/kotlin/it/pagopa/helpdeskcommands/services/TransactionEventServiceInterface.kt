package it.pagopa.helpdeskcommands.services

import it.pagopa.ecommerce.commons.documents.v2.TransactionRefundRequestedEvent
import it.pagopa.ecommerce.commons.documents.v2.TransactionUserReceiptRequestedEvent
import reactor.core.publisher.Mono

interface TransactionEventServiceInterface {
    /**
     * Sends a refund request event to the transaction refund queue for processing by the dedicated
     * service.
     *
     * @param event The refund request event with transaction details and manual trigger
     * @return Mono that completes when the message is queued successfully
     */
    fun sendRefundRequestedEvent(event: TransactionRefundRequestedEvent): Mono<Void>
    /**
     * Sends a notification request event to the transaction notification queue for processing by
     * the dedicated service.
     *
     * @param event The notification request event with transaction details and manual trigger
     * @return Mono that completes when the message is queued successfully
     */
    fun sendNotificationRequestedEvent(event: TransactionUserReceiptRequestedEvent): Mono<Void>
}
