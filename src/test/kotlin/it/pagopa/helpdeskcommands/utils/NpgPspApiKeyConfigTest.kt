package it.pagopa.helpdeskcommands.utils

import arrow.core.Either
import com.fasterxml.jackson.databind.ObjectMapper
import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyConfigurationException
import it.pagopa.helpdeskcommands.exceptions.NpgApiKeyMissingPspRequestedException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NpgPspApiKeyConfigTest {

    val OBJECT_MAPPER = ObjectMapper()

    val pspConfigurationJson =
        """
        {
            "psp1" : "key-psp1",
            "psp2" : "key-psp2",
            "psp3" : "key-psp3"
        }
    """
            .trimIndent()

    val pspToHandle = setOf("psp1", "psp2", "psp3")

    @ParameterizedTest
    @ValueSource(strings = ["psp1", "psp2", "psp3"])
    @Throws(NpgApiKeyMissingPspRequestedException::class)
    fun shouldParsePspConfigurationSuccessfully(pspId: String) {
        val pspConfiguration: Either<NpgApiKeyConfigurationException, NpgPspApiKeysConfig> =
            NpgPspApiKeysConfig.parseApiKeyConfiguration(
                pspConfigurationJson,
                pspToHandle,
                PaymentMethod.CARDS,
                OBJECT_MAPPER
            )

        assertTrue(pspConfiguration.isRight())
        assertEquals("key-$pspId", pspConfiguration.getOrNull()?.get(pspId)?.getOrNull())
    }

    @Test
    fun shouldThrowExceptionForInvalidJsonStructure() {
        val pspConfiguration: Either<NpgApiKeyConfigurationException, NpgPspApiKeysConfig> =
            NpgPspApiKeysConfig.parseApiKeyConfiguration(
                "{",
                pspToHandle,
                PaymentMethod.CARDS,
                OBJECT_MAPPER
            )
        assertTrue(pspConfiguration.isLeft())
        assertEquals(
            "Error parsing NPG PSP api keys configuration for payment method: [CARDS], " +
                "cause: Invalid json configuration map",
            pspConfiguration.leftOrNull()?.message
        )
    }

    @Test
    fun shouldThrowExceptionForMissingPspId() {
        val psps: MutableSet<String> = HashSet(pspToHandle)
        psps.add("psp4")
        val pspConfiguration: Either<NpgApiKeyConfigurationException, NpgPspApiKeysConfig> =
            NpgPspApiKeysConfig.parseApiKeyConfiguration(
                pspConfigurationJson,
                psps,
                PaymentMethod.CARDS,
                OBJECT_MAPPER
            )
        assertTrue(pspConfiguration.isLeft())
        assertEquals(
            "Error parsing NPG PSP api keys configuration for payment method: [CARDS], " +
                "cause: Misconfigured api keys. Missing keys: [psp4]",
            pspConfiguration.leftOrNull()?.message
        )
    }

    @Test
    fun shouldThrowExceptionForRetrievingMissingPsp() {
        val pspConfiguration: Either<NpgApiKeyConfigurationException, NpgPspApiKeysConfig> =
            NpgPspApiKeysConfig.parseApiKeyConfiguration(
                pspConfigurationJson,
                pspToHandle,
                PaymentMethod.CARDS,
                OBJECT_MAPPER
            )

        assertTrue(pspConfiguration.isRight())
        Assertions.assertInstanceOf(
            NpgApiKeyMissingPspRequestedException::class.java,
            pspConfiguration.getOrNull()?.get("missingPSP")?.leftOrNull()
        )
    }
}
