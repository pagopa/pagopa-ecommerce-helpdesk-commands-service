package it.pagopa.helpdeskcommands.exceptions

/** Exception thrown when NPG per PSP api key configuration cannot be successfully parsed */
class RedirectConfigurationException : RuntimeException {

    constructor(
        errorCause: String,
        configurationType: RedirectConfigurationType
    ) : super("Error parsing Redirect PSP $configurationType configuration, cause: $errorCause")

    constructor(errorCause: String) : super(errorCause)
}
