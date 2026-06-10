package it.pagopa.helpdeskcommands.utils

/** Criteria supported by redirect URL mapping configuration entries. */
enum class RedirectUrlMappingCriteria {
    PAYMENT_TYPE_CODE,
    PSP_ID,
    TOUCHPOINT,
    PSP_CHANNEL_ID
}
