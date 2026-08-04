package it.pagopa.helpdeskcommands.mdcutilities

import it.pagopa.ecommerce.commons.mdcutilities.LogTracingUtils
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

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val headers = exchange.request.headers

        val userId =
            headers.getFirst(HEADER_USER_ID)
                ?: LogTracingUtils.TracingEntry.CTX_USER_ID.defaultValue

        val forwardedFor =
            headers.getFirst(HEADER_FORWARDED_FOR)
                ?: LogTracingUtils.TracingEntry.CTX_FORWARDED_FOR.defaultValue

        val mdcContext =
            Context.of(
                LogTracingUtils.TracingEntry.CTX_USER_ID.key,
                userId,
                LogTracingUtils.TracingEntry.CTX_FORWARDED_FOR.key,
                forwardedFor
            )

        return chain.filter(exchange).contextWrite(mdcContext)
    }
}
