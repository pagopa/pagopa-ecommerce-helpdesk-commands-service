package it.pagopa.helpdeskcommands.utils

import arrow.core.Either
import arrow.core.left
import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyConfigurationException

class NpgApiKeyConfiguration(
    private val methodsApiKeyMapping: Map<PaymentMethod, NpgPspApiKeysConfig>
) {

    init {
        require(methodsApiKeyMapping.isNotEmpty()) {
            throw NpgApiKeyConfigurationException(
                "Invalid configuration detected! Payment methods api key mapping cannot be null or empty"
            )
        }
    }

    data class Builder(
        val methodsApiKeyMapping: MutableMap<PaymentMethod, NpgPspApiKeysConfig> = mutableMapOf()
    ) {

        fun withMethodPspMapping(
            paymentMethod: PaymentMethod,
            npgPspApiKeysConfig: NpgPspApiKeysConfig
        ) = apply {
            if (methodsApiKeyMapping.containsKey(paymentMethod)) {
                throw NpgApiKeyConfigurationException(
                    "Api key mapping already registered for payment method: [$paymentMethod]"
                )
            }
            methodsApiKeyMapping.put(paymentMethod, npgPspApiKeysConfig)
        }
        fun build() = NpgApiKeyConfiguration(methodsApiKeyMapping)
    }

    /**
     * Alias for {@link NpgApiKeyConfiguration#getApiKeyForPaymentMethod(NpgClient.PaymentMethod,
     * String)}
     *
     * @param paymentMethod the payment method for which api keys will be searched for
     * @param pspId the searched api key psp id
     * @return either the found api key or an NpgApiKeyConfigurationException exception if no api
     *   key can be found
     */
    operator fun get(paymentMethod: PaymentMethod, pspId: String) =
        getApiKeyForPaymentMethod(paymentMethod, pspId)

    /**
     * Get the api key associated to the input pspId for the given paymentMethod
     *
     * @param paymentMethod the payment method for which api keys will be searched for
     * @param pspId the searched api key psp id
     * @return either the found api key or an NpgApiKeyConfigurationException exception if no api
     *   key can be found
     */
    fun getApiKeyForPaymentMethod(
        paymentMethod: PaymentMethod,
        pspId: String
    ): Either<NpgApiKeyConfigurationException, String> {
        var result: Either<NpgApiKeyConfigurationException, String> =
            NpgApiKeyConfigurationException(
                    "Cannot retrieve api key configuration for payment method: [$paymentMethod]."
                )
                .left()
        val npgPspApiKeysConfig = methodsApiKeyMapping[paymentMethod]
        if (npgPspApiKeysConfig != null) {
            result =
                npgPspApiKeysConfig.get(pspId).mapLeft {
                    NpgApiKeyConfigurationException(
                        "Cannot retrieve api key for payment method: [$paymentMethod]. Cause: ${it.message}"
                    )
                }
        }
        return result
    }
}
