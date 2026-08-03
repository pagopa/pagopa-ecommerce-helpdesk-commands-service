package it.pagopa.helpdeskcommands.mdcutilities

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RequestTracingUtilsTest {

    @BeforeEach
    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `withErrorMdc should populate MDC with default values when error is null`() {
        val result = RequestTracingUtils.withErrorMdc(error = null) {
            assertEquals(RequestTracingUtils.TracingEntry.ERROR_TYPE.defaultValue, MDC.get(RequestTracingUtils.TracingEntry.ERROR_TYPE.key))
            assertEquals(RequestTracingUtils.TracingEntry.ERROR_MESSAGE.defaultValue, MDC.get(RequestTracingUtils.TracingEntry.ERROR_MESSAGE.key))
            "block_result"
        }

        assertEquals("block_result", result)
        assertNull(MDC.get(RequestTracingUtils.TracingEntry.ERROR_TYPE.key))
        assertNull(MDC.get(RequestTracingUtils.TracingEntry.ERROR_MESSAGE.key))
    }

    @Test
    fun `withErrorMdc should populate MDC with exception details and custom attributes`() {
        val exception = IllegalArgumentException("Invalid input provided")
        val customAttributes = mapOf("custom.key" to "customValue", "another.key" to 123)

        RequestTracingUtils.withErrorMdc(error = exception, attributes = customAttributes) {
            assertEquals(IllegalArgumentException::class.java.name, MDC.get(RequestTracingUtils.TracingEntry.ERROR_TYPE.key))
            assertEquals("Invalid input provided", MDC.get(RequestTracingUtils.TracingEntry.ERROR_MESSAGE.key))
            assertEquals("customValue", MDC.get("custom.key"))
            assertEquals("123", MDC.get("another.key"))
        }

        assertNull(MDC.get(RequestTracingUtils.TracingEntry.ERROR_TYPE.key))
        assertNull(MDC.get("custom.key"))
    }

    @Test
    fun `withErrorMdc should handle exceptions without a message`() {
        val exception = NullPointerException()

        RequestTracingUtils.withErrorMdc(error = exception) {
            assertEquals(NullPointerException::class.java.name, MDC.get(RequestTracingUtils.TracingEntry.ERROR_TYPE.key))
            assertEquals(RequestTracingUtils.TracingEntry.ERROR_MESSAGE.defaultValue, MDC.get(RequestTracingUtils.TracingEntry.ERROR_MESSAGE.key))
        }
    }

    @Test
    fun `withContextDetailsMdc should use empty JSON fallback when details is null`() {
        val result = RequestTracingUtils.withContextDetailsMdc(details = null) {
            assertEquals("{}", MDC.get(RequestTracingUtils.CTX_DETAILS_KEY))
            "success"
        }

        assertEquals("success", result)
        assertNull(MDC.get(RequestTracingUtils.CTX_DETAILS_KEY))
    }

    @Test
    fun `withContextDetailsMdc should serialize map to JSON`() {
        val detailsMap = mapOf(
            "userId" to 123,
            "action" to "LOGIN"
        )

        val expectedJson = ObjectMapper().writeValueAsString(detailsMap)

        RequestTracingUtils.withContextDetailsMdc(details = detailsMap) {
            assertEquals(expectedJson, MDC.get(RequestTracingUtils.CTX_DETAILS_KEY))
        }
    }

    @Test
    fun `withContextDetailsMdc should fallback to empty JSON if serialization fails`() {
        class UnserializableObject {
            @Suppress("unused")
            val brokenField: String
                get() = throw RuntimeException("Serialization error triggered")
        }

        val badDetails = mapOf("badObject" to UnserializableObject())

        RequestTracingUtils.withContextDetailsMdc(details = badDetails) {
            assertEquals("{}", MDC.get(RequestTracingUtils.CTX_DETAILS_KEY))
        }
    }

    @Test
    fun `withContextDetailsMdc should populate MDC with additional attributes`() {
        val attributes = mapOf(RequestTracingUtils.TracingEntry.OPERATION_ID.key to "OP-123")

        RequestTracingUtils.withContextDetailsMdc(details = null, attributes = attributes) {
            assertEquals("{}", MDC.get(RequestTracingUtils.CTX_DETAILS_KEY))
            assertEquals("OP-123", MDC.get(RequestTracingUtils.TracingEntry.OPERATION_ID.key))
        }

        assertNull(MDC.get(RequestTracingUtils.TracingEntry.OPERATION_ID.key))
    }

    @Test
    fun `insertIntoMdcAndCleanup should clean up MDC even if the block throws an exception`() {
        val entries = mapOf("temp.key" to "tempValue")

        assertFailsWith<IllegalStateException>("Simulated failure") {
            RequestTracingUtils.insertIntoMdcAndCleanup(entries) {
                assertEquals("tempValue", MDC.get("temp.key"))
                throw IllegalStateException("Simulated failure")
            }
        }

        assertNull(MDC.get("temp.key"))
    }
}