package it.pagopa.helpdeskcommands.config

import it.pagopa.ecommerce.commons.mdcutilities.LogTracingUtils
import it.pagopa.ecommerce.commons.mdcutilities.MDCContextLifterConfiguration
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MDCContextLifterConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun initializeMdcContextLifter(): MDCContextLifterConfiguration {
        LogTracingUtils.setContextBounded(
            setOf(
                LogTracingUtils.TracingEntry.CTX_USER_ID,
                LogTracingUtils.TracingEntry.CTX_FORWARDED_FOR
            )
        )
        return MDCContextLifterConfiguration()
    }
}
