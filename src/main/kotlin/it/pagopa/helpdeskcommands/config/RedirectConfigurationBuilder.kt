package it.pagopa.helpdeskcommands.config

import it.pagopa.helpdeskcommands.utils.RedirectUrlMappingConf
import java.net.URI
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration class used to read all the PSP configurations that will be used during redirect
 * transaction
 */
@Configuration
class RedirectConfigurationBuilder {
    /**
     * Create a {@code RedirectUrlMappingConf} that will contain every handled PSP backend URI for
     * Redirect refund calls, then provides a method to search based on structured criteria.
     *
     * @param expectedMatchingCriteria
     * - JSON list of criteria that must be covered by redirect URL configuration
     *
     * @param pspUrlMapping
     * - JSON list of PSP backend URI mappings
     *
     * @return a configuration map for every PSPs
     */
    @Bean
    fun redirectBeApiCallUriConf(
        @Value("\${redirect.pspUrlMapping}") pspUrlMapping: String,
        @Value("\${redirect.expectedMatchingCriteria}") expectedMatchingCriteria: String
    ): RedirectUrlMappingConf {
        return RedirectUrlMappingConf(pspUrlMapping, expectedMatchingCriteria, ::appendRefundsPath)
    }

    private fun appendRefundsPath(uri: URI): URI {
        val currentPath = uri.path.orEmpty().trimEnd('/')
        if (currentPath.endsWith("/refunds")) {
            return uri
        }

        return uri.resolve("$currentPath/refunds")
    }
}
