package it.pagopa.helpdeskcommands.config

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import it.pagopa.ecommerce.commons.queues.TracingUtils
import it.pagopa.ecommerce.commons.utils.OpenTelemetryUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenTelemetryConfiguration {

    @Bean
    fun agentOpenTelemetrySDKInstance(): OpenTelemetry {
        return GlobalOpenTelemetry.get()
    }

    @Bean
    fun openTelemetryTracer(openTelemetry: OpenTelemetry): Tracer {
        return openTelemetry.getTracer("pagopa-ecommerce-helpdesk-commands-service")
    }

    @Bean
    fun tracingUtils(openTelemetry: OpenTelemetry, tracer: Tracer): TracingUtils {
        return TracingUtils(openTelemetry, tracer)
    }

    @Bean
    fun openTelemetryUtils(openTelemetryTracer: Tracer): OpenTelemetryUtils {
        return OpenTelemetryUtils(openTelemetryTracer)
    }
}
