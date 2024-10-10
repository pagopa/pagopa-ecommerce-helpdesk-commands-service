package it.pagopa.helpdeskcommands.exceptions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RedirectConfigurationTypeTest {

    @Test
    fun `should return configuration type successfully`() {
        val redirectConfigurationType =
            RedirectConfigurationType.fromConfigurationType("BACKEND_URLS")
        assertEquals(redirectConfigurationType, RedirectConfigurationType.BACKEND_URLS)
    }

    @Test
    fun `should throw exception for invalid configuration type`() {
        val invalidConfigurationType = "CONFIGURATION_TYPE_INVALID"
        val exception =
            assertThrows<IllegalArgumentException> {
                RedirectConfigurationType.fromConfigurationType(invalidConfigurationType)
            }

        assertEquals("Invalid configuration type: '$invalidConfigurationType'", exception.message)
    }
}
