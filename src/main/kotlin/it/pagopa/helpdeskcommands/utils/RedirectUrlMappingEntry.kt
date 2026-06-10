package it.pagopa.helpdeskcommands.utils

import java.net.URI

/** Redirect URL configuration entry with its matching criteria. */
data class RedirectUrlMappingEntry(
    val url: URI,
    val matchingCriteria: Map<RedirectUrlMappingCriteria, String>
)
