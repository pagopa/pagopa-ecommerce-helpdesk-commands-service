package it.pagopa.helpdeskcommands.utils

import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NpgApiKeyConfigurationTest {

    val DEFAULT_API_KEY = "default-api-key"
    val PSP_ID = "pspId1"

    val npgApiKeyConfiguration =
        NpgApiKeyConfiguration.Builder()
            .setDefaultApiKey(DEFAULT_API_KEY)
            .withMethodPspMapping(
                PaymentMethod.PAYPAL,
                NpgPspApiKeysConfig(mapOf(PSP_ID to "pspId1-paypal-api-key"))
            )
            .withMethodPspMapping(
                PaymentMethod.CARDS,
                NpgPspApiKeysConfig(mapOf(PSP_ID to "pspId1-cards-api-key"))
            )
            .build()

    @Test
    fun shouldRetrieveApiKeySuccessfully() {
        // test
        val defaultApiKey = npgApiKeyConfiguration.getDefaultApiKey()
        val paypalApiKey = npgApiKeyConfiguration.get(PaymentMethod.PAYPAL, PSP_ID)
        val cardsApiKey = npgApiKeyConfiguration[PaymentMethod.CARDS, PSP_ID]
        // assertions
        assertEquals(DEFAULT_API_KEY, defaultApiKey)
        assertEquals("pspId1-paypal-api-key", paypalApiKey.getOrNull())
        assertEquals("pspId1-cards-api-key", cardsApiKey.getOrNull())
    }

    @Test
    fun shouldThrowExceptionAddingAlreadyExistingApiMapping() {
        // test
        val exception =
            assertThrows<NpgApiKeyConfigurationException> {
                NpgApiKeyConfiguration.Builder()
                    .setDefaultApiKey(DEFAULT_API_KEY)
                    .withMethodPspMapping(
                        PaymentMethod.PAYPAL,
                        NpgPspApiKeysConfig(mapOf(PSP_ID to "pspId1-paypal-api-key"))
                    )
                    .withMethodPspMapping(
                        PaymentMethod.PAYPAL,
                        NpgPspApiKeysConfig(mapOf(PSP_ID to "pspId1-paypal-api-key"))
                    )
                    .build()
            }
        assertEquals(
            "Api key mapping already registered for payment method: [${PaymentMethod.PAYPAL.serviceName}]",
            exception.message
        )
    }

    @Test
    fun shouldReturnEitherLeftForMissingApiKey() {
        // test
        val pspId = "missingPspId"
        val paypalApiKey =
            npgApiKeyConfiguration.getApiKeyForPaymentMethod(PaymentMethod.PAYPAL, pspId)
        // assertions
        assertEquals(
            "Cannot retrieve api key for payment method: [${PaymentMethod.PAYPAL.serviceName}]. Cause: Requested API key for PSP: [missingPspId]. Available PSPs: [pspId1]",
            paypalApiKey.leftOrNull()?.message
        )
    }

    @Test
    fun shouldThrowExceptionForMissingMethodsKeys() {
        // test
        val exception =
            assertThrows<NpgApiKeyConfigurationException> {
                NpgApiKeyConfiguration.Builder().setDefaultApiKey(DEFAULT_API_KEY).build()
            }
        // assertions
        assertEquals(
            "Invalid configuration detected! Payment methods api key mapping cannot be null or empty",
            exception.message
        )
    }
}
