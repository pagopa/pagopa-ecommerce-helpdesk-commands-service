package it.pagopa.helpdeskcommands.utils

import arrow.core.Either
import it.pagopa.helpdeskcommands.exceptions.RedirectConfigurationException
import java.net.URI
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class RedirectionKeysConfigTest {

    @ParameterizedTest
    @MethodSource("redirectRetrieveUrlPaymentMethodsTestSearch")
    fun `should handle both successful and failed redirect url searches`(
        touchpoint: String?,
        pspId: String?,
        paymentMethodId: String?,
        expectedResult:
            Either<RedirectConfigurationException, URI> // not just URI, to cover both cases
    ) {
        val redirectUrlMapping =
            mapOf(
                "CHECKOUT-psp1-RBPR" to "http://localhost:8096/redirections1/CHECKOUT",
                "IO-psp1-RBPR" to "http://localhost:8096/redirections1/IO",
                "psp2-RBPB" to "http://localhost:8096/redirections2",
                "RBPS" to "http://localhost:8096/redirections3"
            )
        val codeTypeList = setOf("CHECKOUT-psp1-RBPR", "IO-psp1-RBPR", "psp2-RBPB", "RBPS")

        val redirectionKeysConfig = RedirectKeysConfiguration(redirectUrlMapping, codeTypeList)
        val result: Either<RedirectConfigurationException, URI> =
            redirectionKeysConfig.getRedirectUrlForPsp(touchpoint!!, pspId!!, paymentMethodId!!)

        when (expectedResult) {
            is Either.Right -> {
                assertTrue(result.isRight())
                assertEquals(expectedResult.value, result.getOrNull())
            }
            is Either.Left -> {
                assertTrue(result.isLeft())
                assertEquals(expectedResult.value.message, (result as Either.Left).value.message)
            }
        }
    }

    @Test
    fun `should return error during search redirect url for invalid search key`() {
        val redirectUrlMapping =
            mapOf(
                "CHECKOUT-psp1-RBPR" to "http://localhost:8096/redirections1/CHECKOUT",
                "IO-psp1-RBPR" to "http://localhost:8096/redirections1/IO",
                "psp2-RBPB" to "http://localhost:8096/redirections2",
                "RBPS" to "http://localhost:8096/redirections3"
            )
        val codeTypeList = setOf("CHECKOUT-psp1-RBPR", "IO-psp1-RBPR", "psp2-RBPB", "RBPS")
        val touchpoint = "CHECKOUT"
        val pspId = "psp1"
        val paymentMethodId = "RBPP"

        val redirectionKeysConfig = RedirectKeysConfiguration(redirectUrlMapping, codeTypeList)
        val result: Either<RedirectConfigurationException, URI> =
            redirectionKeysConfig.getRedirectUrlForPsp(touchpoint, pspId, paymentMethodId)
        assertTrue(result.isLeft())
        assertEquals(
            "Error parsing Redirect PSP BACKEND_URLS configuration, cause: Missing key for redirect return url with following search parameters: touchpoint: [$touchpoint] pspId: [$pspId] paymentTypeCode: [$paymentMethodId]",
            (result as Either.Left<Throwable>).value.message
        )
    }

    @Test
    fun `should throw exception building backend uri map for missing key`() {
        val redirectUrlMapping =
            mapOf("CHECKOUT-psp1-RBPR" to "http://localhost:8096/redirections1/CHECKOUT")
        val codeTypeList = setOf("key1")

        val e =
            assertThrows<RedirectConfigurationException> {
                RedirectKeysConfiguration(redirectUrlMapping, codeTypeList)
            }

        assertEquals(
            "Error parsing Redirect PSP BACKEND_URLS configuration, cause: Misconfigured redirect.pspUrlMapping, the following redirect payment type code b.e. URIs are not configured: [key1]",
            e.message
        )
    }

    companion object {
        @JvmStatic
        fun redirectRetrieveUrlPaymentMethodsTestSearch(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "CHECKOUT",
                    "psp1",
                    "RBPR",
                    Either.Right(URI("http://localhost:8096/redirections1/CHECKOUT"))
                ),
                Arguments.of(
                    "IO",
                    "psp1",
                    "RBPR",
                    Either.Right(URI("http://localhost:8096/redirections1/IO"))
                ),
                Arguments.of(
                    "ANYPOINT",
                    "psp2",
                    "RBPB",
                    Either.Right(URI("http://localhost:8096/redirections2"))
                ),
                Arguments.of(
                    "ANYPOINT",
                    "ANYPSP",
                    "RBPS",
                    Either.Right(URI("http://localhost:8096/redirections3"))
                )
            )
    }
}
