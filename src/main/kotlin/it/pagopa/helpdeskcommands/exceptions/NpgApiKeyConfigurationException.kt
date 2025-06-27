package it.pagopa.helpdeskcommands.exceptions

import it.pagopa.helpdeskcommands.utils.PaymentMethod

class NpgApiKeyConfigurationException : RuntimeException {

    constructor(
        message: String,
        paymentMethod: PaymentMethod,
    ) : super(
        "Error parsing NPG PSP api keys configuration for payment method: [$paymentMethod], cause: $message"
    )

    constructor(message: String) : super(message)
}
