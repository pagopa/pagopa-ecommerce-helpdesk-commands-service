package it.pagopa.helpdeskcommands.exceptions

class NpgApiKeyMissingPspRequestedException(psp: String, availablePsps: Set<String>) :
    RuntimeException("Requested API key for PSP: [$psp]. Available PSPs: $availablePsps")
