package it.pagopa.helpdeskcommands.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import it.pagopa.helpdeskcommands.exceptions.RedirectConfigurationException
import it.pagopa.helpdeskcommands.exceptions.RedirectConfigurationType
import java.net.URI

/**
 * Handles redirect backend URL configuration using structured matching criteria.
 *
 * This is the local Kotlin port of the dynamic redirect URL lookup introduced in ecommerce-commons.
 */
class RedirectUrlMappingConf(
    urlConfigurationJsonValue: String,
    expectedMatchingCriteriaJsonValue: String,
    urlTransformer: (URI) -> URI = { it }
) {

    private val urlConfiguration: List<RedirectUrlMappingEntry>

    init {
        try {
            val parsedConfiguration =
                objectMapper.readValue(
                    urlConfigurationJsonValue,
                    object : TypeReference<List<RedirectUrlMappingEntry>>() {}
                )
            urlConfiguration = parsedConfiguration.map { it.copy(url = urlTransformer(it.url)) }

            val expectedMatchingCriteria =
                objectMapper.readValue(
                    expectedMatchingCriteriaJsonValue,
                    object : TypeReference<List<Map<RedirectUrlMappingCriteria, String>>>() {}
                )

            expectedMatchingCriteria.forEach { matchingCriteria ->
                getRedirectUrlForCriteria(matchingCriteria)
                    .fold(
                        { error ->
                            throw RedirectConfigurationException(
                                "Redirect url configuration does not match expected criteria: ${error.message}",
                                RedirectConfigurationType.BACKEND_URLS
                            )
                        },
                        {}
                    )
            }
        } catch (e: RedirectConfigurationException) {
            throw e
        } catch (e: Exception) {
            throw RedirectConfigurationException(
                "Invalid redirect url configuration: error parsing json values",
                RedirectConfigurationType.BACKEND_URLS,
                e
            )
        }
    }

    fun getRedirectUrlForCriteria(
        matchingCriteria: Map<RedirectUrlMappingCriteria, String>
    ): Either<RedirectConfigurationException, RedirectUrlMappingEntry> {
        val normalizedCriteria = matchingCriteria.filterValues { it.isNotBlank() }
        val entries =
            urlConfiguration.filter { confEntry ->
                normalizedCriteria.all { (criteria, value) ->
                    confEntry.matchingCriteria.getOrDefault(criteria, value) == value
                }
            }

        if (entries.size != 1) {
            val errorMessageHeader =
                if (entries.isEmpty()) {
                    "No configuration found"
                } else {
                    "Multiple configurations found: $entries"
                }
            return RedirectConfigurationException(
                    "$errorMessageHeader for the provided matching criteria: $normalizedCriteria",
                    RedirectConfigurationType.BACKEND_URLS
                )
                .left()
        }

        return entries.first().right()
    }

    companion object {
        private val objectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
    }
}
