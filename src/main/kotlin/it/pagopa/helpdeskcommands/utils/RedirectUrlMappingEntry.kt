package it.pagopa.helpdeskcommands.utils

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI

/** Redirect URL configuration entry with its matching criteria. */
data class RedirectUrlMappingEntry
@JsonCreator
constructor(
    @param:JsonProperty("url") val url: URI,
    @param:JsonProperty("matchingCriteria")
    val matchingCriteria: Map<RedirectUrlMappingCriteria, String>
)
