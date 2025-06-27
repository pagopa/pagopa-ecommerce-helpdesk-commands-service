package it.pagopa.helpdeskcommands.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyConfigurationException
import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyMissingPspRequestedException

class NpgPspApiKeysConfig(private val configuration: Map<String, String> = mutableMapOf()) {
    /**
     * Retrieves an API key for a specific PSP
     *
     * @param psp the PSP you want the API key for
     * @return the API key corresponding to the input PSP
     */
    fun get(psp: String): Either<NpgApiKeyMissingPspRequestedException, String> {
        if (configuration.containsKey(psp)) return configuration[psp]!!.right()
        else return NpgApiKeyMissingPspRequestedException(psp, configuration.keys).left()
    }

    companion object {
        /**
         * Return a map where valued with each psp id - api keys entries
         *
         * @param jsonSecretConfiguration - secret configuration json representation
         * @param pspToHandle - psp expected to be present into configuration json
         * @param paymentMethod - payment method for which api key have been configured
         * @param objectMapper - [ObjectMapper] used to parse input JSON
         * @return either the parsed map or the related parsing exception
         */
        fun parseApiKeyConfiguration(
            jsonSecretConfiguration: String,
            pspToHandle: Set<String>,
            paymentMethod: PaymentMethod,
            objectMapper: ObjectMapper
        ): Either<NpgApiKeyConfigurationException, NpgPspApiKeysConfig> {
            try {
                val expectedKeys: MutableSet<String> = HashSet(pspToHandle)
                val apiKeys: Map<String, String> =
                    objectMapper.readValue(
                        jsonSecretConfiguration,
                        object : TypeReference<HashMap<String, String>>() {}
                    )
                val configuredKeys = apiKeys.keys
                expectedKeys.removeAll(configuredKeys)
                if (!expectedKeys.isEmpty()) {
                    return NpgApiKeyConfigurationException(
                            "Misconfigured api keys. Missing keys: $expectedKeys",
                            paymentMethod
                        )
                        .left()
                }
                return NpgPspApiKeysConfig(apiKeys).right()
            } catch (ignored: JacksonException) {
                // exception here is ignored on purpose in order to avoid secret configuration
                // logging in case of wrong configured json string object
                return NpgApiKeyConfigurationException(
                        "Invalid json configuration map",
                        paymentMethod
                    )
                    .left()
            }
        }
    }
}
