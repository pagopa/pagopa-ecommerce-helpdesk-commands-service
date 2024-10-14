package it.pagopa.helpdeskcommands.utils

import arrow.core.Either
import arrow.core.right
import it.pagopa.helpdeskcommands.exceptions.RedirectConfigurationException
import it.pagopa.helpdeskcommands.exceptions.RedirectConfigurationType
import java.net.URI
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * This class manages the PSP redirect URL map. It has been converted to Kotlin from the
 * ecommerce-commons Java class
 */
class RedirectKeysConfiguration(
    pspUrlMapping: Map<String, String>,
    paymentTypeCodeList: Set<String>
) {
    private val redirectBeApiCallUriMap: Map<String, URI>

    init {
        val redirectUriMap: MutableMap<String, URI> = HashMap()
        // URI.create throws IllegalArgumentException that will prevent module load for
        // invalid PSP URI configuration
        pspUrlMapping.forEach { (pspId: String?, uri: String?) ->
            redirectUriMap[pspId] = uri.let { URI.create(it) }!!
        }
        val missingKeys =
            paymentTypeCodeList
                .stream()
                .filter(Predicate.not { key: String -> redirectUriMap.containsKey(key) })
                .collect(Collectors.toSet())
        if (missingKeys.isNotEmpty()) {
            throw RedirectConfigurationException(
                "Misconfigured redirect.pspUrlMapping, " +
                    "the following redirect payment type code b.e. URIs are not configured: $missingKeys",
                RedirectConfigurationType.BACKEND_URLS
            )
        }
        this.redirectBeApiCallUriMap = Collections.unmodifiableMap(redirectUriMap)
    }

    /**
     * Returns a PSP redirect URL given a key by searching among the configured URLs.
     *
     * @param touchpoint the touchpoint used to initiate the transaction
     * @param pspId the psp id chosen for the current
     * @param paymentTypeCode redirect payment type code
     * @return Either valued with a PSP redirect URI, if valid, or exception for invalid key param
     *   or bad configuration
     */
    fun getRedirectUrlForPsp(
        touchpoint: String,
        pspId: String,
        paymentTypeCode: String
    ): Either<RedirectConfigurationException, URI> {
        /*
         * Search for the key touchpoint-paymentTypeCode-pspId in the redirectUrlMap. If
         * the key is not found, the method searches for paymentTypeCode-pspId, and if
         * not found, it searches for pspId.
         */
        val searchResult = searchRedirectUrlForPsp(touchpoint, paymentTypeCode, pspId)

        return searchResult?.right()
            ?: Either.Left(
                RedirectConfigurationException(
                    "Missing key for redirect return url with following search parameters: " +
                        "touchpoint: [$touchpoint] pspId: [$pspId] paymentTypeCode: [$paymentTypeCode]",
                    RedirectConfigurationType.BACKEND_URLS
                )
            )
    }

    /**
     * Execute a recursive search on the redirectBeApiCallUriMap. The recursion method will be
     * called with the key without the first parameter element. The method has been converted to
     * Kotlin from the ecommerce-commons Java library.
     *
     * @param params List of parameters that compose the key.
     * @return The found URI or an empty value.
     */
    private fun searchRedirectUrlForPsp(vararg params: String): URI? {
        val key = params.joinToString("-")
        redirectBeApiCallUriMap[key]?.let {
            return it
        }

        return if (params.size > 1) {
            searchRedirectUrlForPsp(*params.sliceArray(1 until params.size))
        } else {
            null
        }
    }
}
