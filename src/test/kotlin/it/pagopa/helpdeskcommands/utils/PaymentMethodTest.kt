package it.pagopa.helpdeskcommands.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentMethodTest {

    @Test
    fun shouldReturnPaymentMethodSuccessfully() {
        val paymentMethod = PaymentMethod.fromServiceName("CARDS")
        assertEquals(paymentMethod, PaymentMethod.CARDS)
    }

    @Test
    fun shouldThrowExceptionForInvalidPaymentMethod() {
        val invalidPaymentMethod = "BANK_INVALID"
        val exception =
            assertThrows<IllegalArgumentException> {
                PaymentMethod.fromServiceName(invalidPaymentMethod)
            }

        assertEquals(
            "Invalid payment method service name: '$invalidPaymentMethod'",
            exception.message
        )
    }
}
