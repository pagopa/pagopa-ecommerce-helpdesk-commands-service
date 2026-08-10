package it.pagopa.helpdeskcommands.mdcutilities

import io.micrometer.context.ContextRegistry
import it.pagopa.ecommerce.commons.mdcutilities.LogTracingUtils
import jakarta.annotation.PostConstruct
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context

@Component
class MDCFilter : WebFilter {

    companion object {
        const val HEADER_USER_ID = "X-User-Id"
        const val HEADER_FORWARDED_FOR = "X-Forwarded-For"
        val contextBound =
            setOf(
                LogTracingUtils.AttributeKeys.CTX_USER_ID.key,
                LogTracingUtils.AttributeKeys.CTX_TRANSACTION_ID.key,
                LogTracingUtils.AttributeKeys.EVENT_ACTION.key,
                LogTracingUtils.AttributeKeys.CTX_AUTHORIZATION_REQUEST_ID.key,
                LogTracingUtils.AttributeKeys.CORRELATION_ID.key
            )
    }

    @PostConstruct
    fun initMdcMicrometerRegistry() {
        LogTracingUtils.AttributeKeys.entries
            .filter { contextBound.contains(it.key) }
            .forEach { entry ->
                ContextRegistry.getInstance()
                    .registerThreadLocalAccessor(
                        entry.key,
                        { MDC.get(entry.key) },
                        { value -> MDC.put(entry.key, value) },
                        { MDC.remove(entry.key) }
                    )
            }
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val headers = exchange.request.headers

        val userId =
            headers.getFirst(HEADER_USER_ID)
                ?: LogTracingUtils.AttributeKeys.CTX_USER_ID.defaultValue

        val mdcContext = Context.of(LogTracingUtils.AttributeKeys.CTX_USER_ID.key, userId)

        return chain.filter(exchange).contextWrite(mdcContext)
    }
}
