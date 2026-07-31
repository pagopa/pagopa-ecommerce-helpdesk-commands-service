package it.pagopa.helpdeskcommands.mdcutilities

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.MDC

object RequestTracingUtils {

    const val CTX_DETAILS_KEY = "ctx.details"
    val OBJECT_MAPPER = ObjectMapper()

    const val MONGO_DEPENDENCY_KEY = "eCommerce-mongoDB"
    const val STORAGE_ACCOUNT_DEPENDENCY_KEY = "eCommerce-storageAccount"
    const val STORAGE_QUEUE_DEPENDENCY_KEY = "eCommerce-storageQueue"

    /**
     * Tracing keys used in MDC and/or propagated from Reactor Context.
     *
     * Entries marked as `contextBound = true` are injected into the Reactor Context by [MDCFilter]
     * and automatically propagated to the SLF4J MDC by Micrometer Context Propagation. Entries
     * marked as `false` are written locally in MDC (for example by [.withErrorMdc]).
     */
    enum class TracingEntry(val key: String, val defaultValue: String, val contextBound: Boolean) {
        CTX_USER_ID("ctx.user.id", "{userId-not-found}", true),
        CTX_FORWARD_FOR("ctx.forwarded.for", "{forwardedFor-not-found}", true),
        TRANSACTION_ID("transaction.id", "{transactionId-not-found}", false),
        TRANSACTION_STATUS("transaction.status", "{transactionStatus-not-found}", false),
        CORRELATION_ID("correlation.id", "{correlationId-not-found}", false),
        OPERATION_ID("operation.id", "{operationId-not-found}", false),
        RESPONSE_CODE("response.code", "{responseCode-not-found}", false),
        RESPONSE_BODY("response.body", "{responseBody-not-found}", false),
        PSP_ID("psp.id", "{pspId-not-found}", false),
        PSP_CHANNEL_CODE("psp.channel.code", "{pspChannelCode-not-found}", false),
        PSP_TRANSACTION_ID("psp.transaction.id", "{pspTransactionId-not-found}", false),
        QUEUE_EVENT_ID("queue.event.id", "{queueEventId-not-found}", false),
        DEPENDENCY("dependency", "{dependency-not-found}", false),
        PATH("path", "{path-not-found}", false),
        ERROR_TYPE("error.type", "{errorType-not-found}", false),
        ERROR_MESSAGE("error.message", "{errorMessage-not-found}", false)
    }

    /**
     * Executes a block with error attributes (`error.type` and `error.message`) and an arbitrary
     * map of top-level attributes temporarily stored in MDC.
     *
     * Error attributes are extracted from the provided [Throwable]. Top-level attributes are passed
     * to MDC cleanup logic where string conversion is handled. All keys are guaranteed to be
     * removed after block execution.
     *
     * @param error the exception to extract type and message from
     * @param attributes map of top-level MDC key-value attributes
     * @param block code to execute while attributes are available in MDC
     */
    inline fun <T> withErrorMdc(
        error: Throwable?,
        attributes: Map<String, Any> = emptyMap(),
        block: () -> T
    ): T {
        val mdcMap = mutableMapOf<String, String>()

        mdcMap[TracingEntry.ERROR_TYPE.key] =
            error?.javaClass?.name ?: TracingEntry.ERROR_TYPE.defaultValue
        mdcMap[TracingEntry.ERROR_MESSAGE.key] =
            error?.message ?: TracingEntry.ERROR_MESSAGE.defaultValue
        attributes.forEach { (k, v) -> mdcMap[k] = v.toString() }

        return insertIntoMdcAndCleanup(mdcMap, block)
    }

    /**
     * Executes a block with `ctx.details` temporarily stored in MDC as a JSON string.
     *
     * The input map is serialized to raw JSON and stored under key `ctx.details`. If serialization
     * fails, an empty JSON object (`{}`) is used as fallback. The key is always removed after block
     * execution.
     *
     * @param details map of detail values to serialize under `ctx.details`
     * @param attributes map of top-level MDC key-value attributes
     * @param block code to execute while `ctx.details` is available in MDC
     */
    inline fun <T> withContextDetailsMdc(
        details: Map<String, Any>?,
        attributes: Map<String, Any> = emptyMap(),
        block: () -> T
    ): T {
        val mdcMap = mutableMapOf<String, String>()

        val rawDetails =
            try {
                details?.let { OBJECT_MAPPER.writeValueAsString(it) } ?: "{}"
            } catch (e: JsonProcessingException) {
                "{}"
            }

        mdcMap[CTX_DETAILS_KEY] = rawDetails
        attributes.forEach { (k, v) -> mdcMap[k] = v.toString() }

        return insertIntoMdcAndCleanup(mdcMap, block)
    }

    /**
     * Inserts the provided entries into MDC, executes the given block, and always removes the
     * inserted keys afterward.
     *
     * This method guarantees MDC cleanup through a `finally` block, so temporary values do not leak
     * across log statements or threads.
     *
     * @param entries key/value pairs to temporarily add to MDC
     * @param block code to execute while MDC entries are available
     */
    inline fun <T> insertIntoMdcAndCleanup(entries: Map<String, String>, block: () -> T): T {
        val detailKeys = mutableListOf<String>()
        try {
            entries.forEach { (key, value) ->
                MDC.put(key, value)
                detailKeys.add(key)
            }
            return block()
        } finally {
            detailKeys.forEach { MDC.remove(it) }
        }
    }
}
