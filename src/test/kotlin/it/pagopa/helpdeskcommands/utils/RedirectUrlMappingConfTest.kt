package it.pagopa.helpdeskcommands.utils

import arrow.core.Either
import it.pagopa.helpdeskcommands.exceptions.RedirectConfigurationException
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RedirectUrlMappingConfTest {

    private val redirectUrlMappingConf =
        RedirectUrlMappingConf(
            """
            [
              {
                "url": "http://localhost:8096/redirections1/CHECKOUT",
                "matchingCriteria": {
                  "TOUCHPOINT": "CHECKOUT",
                  "PSP_ID": "psp1",
                  "PAYMENT_TYPE_CODE": "RBPR"
                }
              },
              {
                "url": "http://localhost:8096/redirections1/IO",
                "matchingCriteria": {
                  "TOUCHPOINT": "IO",
                  "PSP_ID": "psp1",
                  "PAYMENT_TYPE_CODE": "RBPR"
                }
              },
              {
                "url": "http://localhost:8096/redirections2",
                "matchingCriteria": {
                  "PSP_ID": "psp2",
                  "PAYMENT_TYPE_CODE": "RBPB",
                  "PSP_CHANNEL_ID": "channel2"
                }
              },
              {
                "url": "http://localhost:8096/redirections3",
                "matchingCriteria": {
                  "PAYMENT_TYPE_CODE": "RBPS"
                }
              }
            ]
            """
                .trimIndent(),
            """
            [
              {
                "TOUCHPOINT": "CHECKOUT",
                "PSP_ID": "psp1",
                "PAYMENT_TYPE_CODE": "RBPR"
              },
              {
                "TOUCHPOINT": "IO",
                "PSP_ID": "psp1",
                "PAYMENT_TYPE_CODE": "RBPR"
              },
              {
                "PSP_ID": "psp2",
                "PAYMENT_TYPE_CODE": "RBPB",
                "PSP_CHANNEL_ID": "channel2"
              },
              {
                "PAYMENT_TYPE_CODE": "RBPS"
              }
            ]
            """
                .trimIndent()
        )

    private val overlappingConf =
        """
        [
          {
            "url": "http://localhost/psp1",
            "matchingCriteria": {
              "PAYMENT_TYPE_CODE": "paymentTypeCode",
              "PSP_ID": "pspId"
            }
          },
          {
            "url": "http://localhost/psp2",
            "matchingCriteria": {
              "PAYMENT_TYPE_CODE": "paymentTypeCode",
              "PSP_ID": "pspId",
              "PSP_CHANNEL_ID": "channelId"
            }
          }
        ]
        """
            .trimIndent()

    @Test
    fun `should fetch configuration matching provided criteria`() {
        val result =
            redirectUrlMappingConf.getRedirectUrlForCriteria(
                mapOf(
                    RedirectUrlMappingCriteria.TOUCHPOINT to "CHECKOUT",
                    RedirectUrlMappingCriteria.PSP_ID to "psp1",
                    RedirectUrlMappingCriteria.PAYMENT_TYPE_CODE to "RBPR"
                )
            )

        assertEquals(
            URI("http://localhost:8096/redirections1/CHECKOUT"),
            (result as Either.Right).value.url
        )
    }

    @Test
    fun `should use optional channel criteria when provided`() {
        val result =
            redirectUrlMappingConf.getRedirectUrlForCriteria(
                mapOf(
                    RedirectUrlMappingCriteria.PSP_ID to "psp2",
                    RedirectUrlMappingCriteria.PAYMENT_TYPE_CODE to "RBPB",
                    RedirectUrlMappingCriteria.PSP_CHANNEL_ID to "channel2"
                )
            )

        assertEquals(URI("http://localhost:8096/redirections2"), (result as Either.Right).value.url)
    }

    @Test
    fun `should fallback to less specific entries when criteria are not configured on entry`() {
        val result =
            redirectUrlMappingConf.getRedirectUrlForCriteria(
                mapOf(
                    RedirectUrlMappingCriteria.TOUCHPOINT to "ANYPOINT",
                    RedirectUrlMappingCriteria.PSP_ID to "ANYPSP",
                    RedirectUrlMappingCriteria.PAYMENT_TYPE_CODE to "RBPS"
                )
            )

        assertEquals(URI("http://localhost:8096/redirections3"), (result as Either.Right).value.url)
    }

    @Test
    fun `should return error during search redirect url for invalid search key`() {
        val result =
            redirectUrlMappingConf.getRedirectUrlForCriteria(
                mapOf(
                    RedirectUrlMappingCriteria.TOUCHPOINT to "CHECKOUT",
                    RedirectUrlMappingCriteria.PSP_ID to "psp1",
                    RedirectUrlMappingCriteria.PAYMENT_TYPE_CODE to "RBPP"
                )
            )

        assertTrue(result.isLeft())
        assertTrue(
            (result as Either.Left<RedirectConfigurationException>)
                .value
                .message
                ?.contains("No configuration found for the provided matching criteria") == true
        )
    }

    @Test
    fun `should throw exception building backend uri map for missing expected criteria`() {
        val e =
            assertThrows<RedirectConfigurationException> {
                RedirectUrlMappingConf(
                    """
                    [
                      {
                        "url": "http://localhost:8096/redirections1/CHECKOUT",
                        "matchingCriteria": {
                          "TOUCHPOINT": "CHECKOUT",
                          "PSP_ID": "psp1",
                          "PAYMENT_TYPE_CODE": "RBPR"
                        }
                      }
                    ]
                    """
                        .trimIndent(),
                    """
                    [
                      {
                        "PAYMENT_TYPE_CODE": "missing"
                      }
                    ]
                    """
                        .trimIndent()
                )
            }

        assertTrue(
            e.message?.contains("Redirect url configuration does not match expected criteria") ==
                true
        )
    }

    @Test
    fun `should transform configured urls when transformer is provided`() {
        val conf =
            RedirectUrlMappingConf(
                """
                [
                  {
                    "url": "http://localhost:8096/redirections",
                    "matchingCriteria": {
                      "PAYMENT_TYPE_CODE": "RBPS"
                    }
                  }
                ]
                """
                    .trimIndent(),
                """
                [
                  {
                    "PAYMENT_TYPE_CODE": "RBPS"
                  }
                ]
                """
                    .trimIndent(),
                { it.resolve("${it.path.trimEnd('/')}/refunds") }
            )

        val result =
            conf.getRedirectUrlForCriteria(
                mapOf(RedirectUrlMappingCriteria.PAYMENT_TYPE_CODE to "RBPS")
            )

        assertEquals(
            URI("http://localhost:8096/redirections/refunds"),
            (result as Either.Right).value.url
        )
    }

    @Test
    fun `should return configuration with higher matching parameters count`() {
        val conf = RedirectUrlMappingConf(overlappingConf, "[]")

        val result =
            conf.getRedirectUrlForCriteria(
                mapOf(
                    RedirectUrlMappingCriteria.PAYMENT_TYPE_CODE to "paymentTypeCode",
                    RedirectUrlMappingCriteria.PSP_ID to "pspId",
                    RedirectUrlMappingCriteria.PSP_CHANNEL_ID to "channelId"
                )
            )

        assertTrue(result.isRight())
        assertEquals(URI("http://localhost/psp2"), (result as Either.Right).value.url)
    }

    @Test
    fun `should return error for configuration with same matching parameters count`() {
        val conf = RedirectUrlMappingConf(overlappingConf, "[]")

        val result =
            conf.getRedirectUrlForCriteria(
                mapOf(
                    RedirectUrlMappingCriteria.PAYMENT_TYPE_CODE to "paymentTypeCode",
                    RedirectUrlMappingCriteria.PSP_ID to "pspId"
                )
            )

        assertTrue(result.isLeft())
        assertTrue(
            (result as Either.Left<RedirectConfigurationException>)
                .value
                .message
                ?.startsWith(
                    "Error parsing Redirect PSP BACKEND_URLS configuration, cause: Multiple configurations found"
                ) == true
        )
    }
}
