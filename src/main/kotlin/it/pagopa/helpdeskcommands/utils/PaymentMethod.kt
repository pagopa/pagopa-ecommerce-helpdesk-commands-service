package it.pagopa.helpdeskcommands.utils

enum class PaymentMethod(val serviceName: String) {
    CARDS("CARDS"),
    PAYPAL("PAYPAL"),
    PAYPAL_PAGAIN3("PAYPAL_PAGAIN3"),
    GIROPAY("GIROPAY"),
    IDEAL("IDEAL"),
    MYBANK("MYBANK"),
    GOOGLEPAY("GOOGLEPAY"),
    APPLEPAY("APPLEPAY"),
    BANCOMATPAY("BANCOMATPAY"),
    BANCONTACT("BANCONTACT"),
    MULTIBANCO("MULTIBANCO"),
    WECHAT("WECHAT"),
    ALIPAY("ALIPAY"),
    PIS("PIS"),
    SATISPAY("SATISPAY_DIRECT");

    companion object {

        /**
         * Retrieves a {@link PaymentMethod} by its service name
         *
         * @param serviceName the service name
         * @return the corresponding payment method
         * @throws IllegalArgumentException if no payment method exists for the given service name
         */
        fun fromServiceName(serviceName: String): PaymentMethod =
            entries.find { serviceName.equals(it.serviceName) }
                ?: throw IllegalArgumentException(
                    "Invalid payment method service name: '$serviceName'"
                )
    }
}
