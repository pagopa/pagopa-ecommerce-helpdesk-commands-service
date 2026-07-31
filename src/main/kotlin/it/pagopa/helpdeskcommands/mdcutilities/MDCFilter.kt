package it.pagopa.helpdeskcommands.mdcutilities

import io.micrometer.context.ContextRegistry
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
    }

    @PostConstruct
    fun initMdcMicrometerRegistry() {
        RequestTracingUtils.TracingEntry.entries
            .filter { it.contextBound }
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
                ?: RequestTracingUtils.TracingEntry.CTX_USER_ID.defaultValue

        val forwardFor =
            headers.getFirst(HEADER_FORWARDED_FOR)
                ?: RequestTracingUtils.TracingEntry.CTX_FORWARD_FOR.defaultValue

        val mdcContext =
            Context.of(
                RequestTracingUtils.TracingEntry.CTX_USER_ID.key,
                userId,
                RequestTracingUtils.TracingEntry.CTX_FORWARD_FOR.key,
                forwardFor
            )

        return chain.filter(exchange).contextWrite(mdcContext)
    }
}
