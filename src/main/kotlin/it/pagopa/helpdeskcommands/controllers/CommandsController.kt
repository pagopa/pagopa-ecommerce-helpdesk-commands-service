package it.pagopa.helpdeskcommands.controllers

import it.pagopa.ecommerce.commons.mdcutilities.LogTracingUtils
import it.pagopa.generated.helpdeskcommands.api.CommandsApi
import it.pagopa.generated.helpdeskcommands.model.*
import it.pagopa.helpdeskcommands.services.CommandsService
import it.pagopa.helpdeskcommands.services.TransactionEventService
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import it.pagopa.helpdeskcommands.utils.TransactionId
import jakarta.validation.constraints.NotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController("CommandsController")
class CommandsController(
    @Autowired private val commandsService: CommandsService,
    @Autowired private val transactionEventService: TransactionEventService
) : CommandsApi {

    override fun commandsRefundRedirectPost(
        xUserId: String,
        xForwardedFor: String,
        refundRedirectRequestDto: Mono<RefundRedirectRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<RefundRedirectResponseDto>> {
        return refundRedirectRequestDto.flatMap { requestDto ->
            commandsService
                .requestRedirectRefund(
                    transactionId = TransactionId(requestDto.idTransaction),
                    touchpoint = requestDto.touchpoint,
                    pspTransactionId = requestDto.idPSPTransaction,
                    paymentTypeCode = requestDto.paymentTypeCode,
                    pspId = requestDto.pspId,
                    pspChannelCode = requestDto.pspChannelCode
                )
                .contextWrite { context ->
                    LogTracingUtils.enrichContextForEvent(
                        mapOf(
                            LogTracingUtils.AttributeKeys.CTX_TRANSACTION_ID to
                                requestDto.idTransaction,
                            LogTracingUtils.AttributeKeys.CTX_AUTHORIZATION_REQUEST_ID to
                                requestDto.idPSPTransaction,
                        ),
                        context
                    )
                }
                .map {
                    ResponseEntity.ok(
                        RefundRedirectResponseDto()
                            .outcome(it.outcome)
                            .idTransaction(it.idTransaction)
                    )
                }
        }
    }

    override fun refundOperation(
        xUserId: String,
        xForwardedFor: String,
        refundTransactionRequestDto: Mono<RefundTransactionRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<RefundTransactionResponseDto>> {
        return refundTransactionRequestDto.flatMap {
            commandsService
                .requestNpgRefund(
                    operationId = it.operationId,
                    transactionId = TransactionId(it.transactionId),
                    correlationId = it.correlationId,
                    paymentMethod = PaymentMethod.fromServiceName(it.paymentMethodName),
                    pspId = it.pspId,
                    amount = it.amount.toBigDecimal()
                )
                .contextWrite { context ->
                    LogTracingUtils.enrichContextForEvent(
                        mapOf(
                            LogTracingUtils.AttributeKeys.CTX_TRANSACTION_ID to it.transactionId,
                            LogTracingUtils.AttributeKeys.CORRELATION_ID to it.correlationId,
                        ),
                        context
                    )
                }
                .map {
                    ResponseEntity.ok(
                        RefundTransactionResponseDto().refundOperationId(it.operationId)
                    )
                }
        }
    }

    /**
     * POST /commands/transactions/{transactionId}/refund : Request a refund for a transaction Sends
     * a refund request to the dedicated service for processing
     *
     * @param transactionId The unique identifier of the transaction (required)
     * @param xUserId User ID (populated by APIM policy) (required)
     * @param xForwardedFor Client Source IP Address (required)
     * @return TransactionRefundRequested message successfully queued to the dedicated service
     *   (status code 202) or Formally invalid input (status code 400) or Transaction not found
     *   (status code 404) or Transaction not in a refundable state (status code 422) or Internal
     *   server error (status code 500)
     */
    @Suppress("kotlin:S6508")
    /** Controller method to handle transaction refund requests */
    override fun requestTransactionRefund(
        transactionId: String?,
        xUserId: @NotNull String?,
        xForwardedFor: @NotNull String?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void?>> {
        if (transactionId.isNullOrBlank()) {
            return Mono.just(ResponseEntity.badRequest().build())
        }
        return transactionEventService
            .createRefundRequestEvent(transactionId)
            .contextWrite { context ->
                LogTracingUtils.enrichContextForEvent(
                    mapOf(LogTracingUtils.AttributeKeys.CTX_TRANSACTION_ID to transactionId),
                    context
                )
            }
            .flatMap { event ->
                transactionEventService
                    .sendRefundRequestedEvent(event)
                    .then(Mono.just(ResponseEntity.accepted().build()))
            }
    }

    /**
     * POST /commands/transactions/{transactionId}/resend-email : Request to resend the transaction
     * email notification Sends an email notification request to the dedicated service for
     * processing
     *
     * @param transactionId The unique identifier of the transaction (required)
     * @param xUserId User ID (populated by APIM policy) (required)
     * @param xForwardedFor Client Source IP Address (required)
     * @return TransactionUserReceipt message successfully queued to the dedicated service (status
     *   code 202) or Invalid transaction ID format (status code 400) or Transaction not found
     *   (status code 404) or Internal server error (status code 500)
     */
    @Suppress("kotlin:S6508")
    override fun resendTransactionEmail(
        transactionId: String?,
        xUserId: @NotNull String?,
        xForwardedFor: @NotNull String?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void>> {
        // Validate required transactionId
        if (transactionId.isNullOrBlank()) {
            return Mono.just(ResponseEntity.badRequest().build())
        }

        return transactionEventService
            .resendUserReceiptNotification(transactionId)
            .contextWrite { context ->
                LogTracingUtils.enrichContextForEvent(
                    mapOf(LogTracingUtils.AttributeKeys.CTX_TRANSACTION_ID to transactionId),
                    context
                )
            }
            .flatMap { event ->
                transactionEventService
                    .sendNotificationRequestedEvent(event)
                    .then(Mono.just(ResponseEntity.accepted().build()))
            }
    }
}
