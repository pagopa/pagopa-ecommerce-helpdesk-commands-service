package it.pagopa.helpdeskcommands.controllers

import it.pagopa.generated.helpdeskcommands.api.CommandsApi
import it.pagopa.generated.helpdeskcommands.model.*
import it.pagopa.helpdeskcommands.exceptions.InvalidTransactionStatusException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotFoundException
import it.pagopa.helpdeskcommands.exceptions.TransactionNotRefundableException
import it.pagopa.helpdeskcommands.services.CommandsService
import it.pagopa.helpdeskcommands.services.TransactionService
import it.pagopa.helpdeskcommands.utils.PaymentMethod
import it.pagopa.helpdeskcommands.utils.TransactionId
import jakarta.validation.constraints.NotNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController("CommandsController")
class CommandsController(
    @Autowired private val commandsService: CommandsService,
    @Autowired private val transactionService: TransactionService
) : CommandsApi {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun commandsRefundRedirectPost(
        xUserId: String,
        xForwardedFor: String,
        refundRedirectRequestDto: Mono<RefundRedirectRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<RefundRedirectResponseDto>> {
        return refundRedirectRequestDto.flatMap { requestDto ->
            logger.info(
                "Received refund redirect request for userId: [{}], transactionId: [{}}, idPSPTransaction: [{}], " +
                    "from IP: [{}]",
                xUserId,
                requestDto.idTransaction,
                requestDto.idPSPTransaction,
                xForwardedFor
            )
            commandsService
                .requestRedirectRefund(
                    transactionId = TransactionId(requestDto.idTransaction),
                    touchpoint = requestDto.touchpoint,
                    pspTransactionId = requestDto.idPSPTransaction,
                    paymentTypeCode = requestDto.paymentTypeCode,
                    pspId = requestDto.pspId
                )
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
            logger.info(
                "Refund transaction for userId: [{}], transactionId: [{}] from IP: [{}]",
                xUserId,
                it.transactionId,
                xForwardedFor
            )
            commandsService
                .requestNpgRefund(
                    operationId = it.operationId,
                    transactionId = TransactionId(it.transactionId),
                    correlationId = it.correlationId,
                    paymentMethod = PaymentMethod.fromServiceName(it.paymentMethodName),
                    pspId = it.pspId,
                    amount = it.amount.toBigDecimal()
                )
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

        // Validate required parameters
        if (transactionId.isNullOrBlank()) {
            return Mono.just(ResponseEntity.badRequest().build())
        }

        // Log the request
        logger.info(
            "Refund request received for transaction [{}] from user [{}] with IP [{}]",
            transactionId,
            xUserId,
            xForwardedFor
        )

        return transactionService
            .createRefundRequestEvent(transactionId)
            .flatMap<ResponseEntity<Void?>> { event ->
                // If event is not null, refund was successfully requested
                if (event != null) {
                    logger.info(
                        "Refund successfully requested for transaction [{}], event ID: [{}]",
                        transactionId,
                        event.id
                    )
                    // TODO: call the queue

                    Mono.just(ResponseEntity.accepted().build())
                } else {
                    // If event is null, refund was already requested
                    logger.info(
                        "Refund already requested for transaction [{}], no action taken",
                        transactionId
                    )
                    Mono.just(ResponseEntity.noContent().build())
                }
            }
            .onErrorResume { error ->
                when (error) {
                    is TransactionNotRefundableException -> {
                        logger.warn(
                            "Transaction [{}] not refundable: {}",
                            transactionId,
                            error.message
                        )
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build())
                    }
                    is TransactionNotFoundException -> {
                        logger.warn("Transaction [{}] not found", transactionId)
                        Mono.just(ResponseEntity.notFound().build())
                    }
                    else -> {
                        logger.error(
                            "Error processing refund request for transaction [{}]",
                            transactionId,
                            error
                        )
                        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
                    }
                }
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

        return transactionService
            .resendUserReceiptNotification(transactionId)
            .flatMap<ResponseEntity<Void>> { event ->
                logger.info(
                    "Successfully resent user receipt notification for transaction ID: {}",
                    transactionId
                )
                // TODO: Send event to the queue
                Mono.just(ResponseEntity.accepted().build())
            }
            .onErrorResume { error ->
                when (error) {
                    is TransactionNotFoundException -> {
                        logger.error("Transaction not found: {}", transactionId, error)
                        Mono.just(ResponseEntity.notFound().build())
                    }
                    is InvalidTransactionStatusException -> {
                        logger.error("Invalid transaction status: {}", transactionId, error)
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build())
                    }
                    else -> {
                        logger.error(
                            "Error resending user receipt for transaction ID: {}",
                            transactionId,
                            error
                        )
                        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
                    }
                }
            }
    }
}
