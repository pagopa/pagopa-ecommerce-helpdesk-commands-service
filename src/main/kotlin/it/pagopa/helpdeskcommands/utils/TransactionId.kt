package it.pagopa.helpdeskcommands.utils

import java.util.*

class TransactionId(transactionId: String) {

    val uuid: UUID = fromTrimmedUUIDString(transactionId)

    /**
     * Get transaction id string value
     *
     * @return transaction id as UUID string without dashes
     */
    fun value(): String {
        return uuid.toString().replace("-", "")
    }

    companion object {

        private fun fromTrimmedUUIDString(trimmedUUIDString: String): UUID {
            require(trimmedUUIDString.length == 32) {
                "Invalid transaction id: [${trimmedUUIDString}]. Transaction id must be 32 chars length"
            }
            val uuidtring =
                String.format(
                    "%s-%s-%s-%s-%s",
                    trimmedUUIDString.substring(0, 8),
                    trimmedUUIDString.substring(8, 12),
                    trimmedUUIDString.substring(12, 16),
                    trimmedUUIDString.substring(16, 20),
                    trimmedUUIDString.substring(20, 32),
                )
            return UUID.fromString(uuidtring)
        }
    }
}
