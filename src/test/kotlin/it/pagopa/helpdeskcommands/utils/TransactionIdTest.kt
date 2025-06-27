package it.pagopa.helpdeskcommands.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TransactionIdTest {

    val INVALID_TRANSACTION_ID_STRING = "93cce28d3b7c4cb9975e6d856ecee"
    val TRANSACTION_ID_STRING = "93cce28d3b7c4cb9975e6d856ecee89f"
    val TRANSACTION_ID_UUID = "93cce28d-3b7c-4cb9-975e-6d856ecee89f"

    @Test
    fun shouldReturnUUIDFromValidTransactionId() {
        val transactionId = TransactionId(TRANSACTION_ID_STRING)
        assertEquals(TRANSACTION_ID_UUID, transactionId.uuid.toString())
        assertEquals(TRANSACTION_ID_STRING, transactionId.value())
    }

    @Test
    fun shouldThrowExceptionForInvalidTransactionId() {
        val exception =
            assertThrows<IllegalArgumentException> { TransactionId(INVALID_TRANSACTION_ID_STRING) }

        assertEquals(
            "Invalid transaction id: [${INVALID_TRANSACTION_ID_STRING}]. " +
                "Transaction id must be 32 chars length",
            exception.message,
        )
    }
}
