package it.pagopa.helpdeskcommands.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
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
            val parsedConfiguration = parseUrlConfiguration(urlConfigurationJsonValue)
            urlConfiguration = parsedConfiguration.map { it.copy(url = urlTransformer(it.url)) }

            val expectedMatchingCriteria =
                parseMatchingCriteriaList(expectedMatchingCriteriaJsonValue)

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

    private fun parseUrlConfiguration(jsonValue: String): List<RedirectUrlMappingEntry> {
        return parseArray(jsonValue).map { entry ->
            RedirectUrlMappingEntry(
                url = URI.create(requiredText(entry, "url")),
                matchingCriteria = parseMatchingCriteria(requiredObject(entry, "matchingCriteria"))
            )
        }
    }

    private fun parseMatchingCriteriaList(
        jsonValue: String
    ): List<Map<RedirectUrlMappingCriteria, String>> {
        return parseArray(jsonValue).map(::parseMatchingCriteria)
    }

    private fun parseArray(jsonValue: String): List<JsonNode> {
        val root = objectMapper.readTree(jsonValue)
        require(root.isArray) { "Expected json array" }
        return root.toList()
    }

    private fun parseMatchingCriteria(node: JsonNode): Map<RedirectUrlMappingCriteria, String> {
        require(node.isObject) { "Expected matching criteria object" }
        return node.fields().asSequence().associate { (name, value) ->
            require(value.isTextual) { "Expected string value for matching criteria $name" }
            RedirectUrlMappingCriteria.valueOf(name) to value.asText()
        }
    }

    private fun requiredObject(node: JsonNode, fieldName: String): JsonNode {
        val field = node.get(fieldName)
        require(field != null && field.isObject) { "Missing or invalid object field $fieldName" }
        return field
    }

    private fun requiredText(node: JsonNode, fieldName: String): String {
        val field = node.get(fieldName)
        require(field != null && field.isTextual) { "Missing or invalid text field $fieldName" }
        return field.asText()
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
