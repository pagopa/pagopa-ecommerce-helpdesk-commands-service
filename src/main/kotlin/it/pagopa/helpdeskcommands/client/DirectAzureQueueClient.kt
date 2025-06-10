package it.pagopa.helpdeskcommands.client

import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class DirectAzureQueueClient {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.builder().build()

    // working solution for native compile/run
    fun sendMessageWithStorageKey(
        queueUrl: String,
        queueName: String,
        message: String,
        storageAccount: String,
        storageKey: String
    ): Mono<String> {
        return try {
            logger.info("Sending message via direct HTTP with Storage Account Key authentication")

            val encodedMessage = Base64.getEncoder().encodeToString(message.toByteArray())
            val xmlBody =
                """<?xml version="1.0" encoding="utf-8"?>
<QueueMessage>
    <MessageText>$encodedMessage</MessageText>
</QueueMessage>"""
                    .trimIndent()

            val timestamp = generateRfc1123Timestamp()
            val authHeader =
                generateAuthorizationHeader(
                    method = "POST",
                    contentType = "application/xml",
                    timestamp = timestamp,
                    queueName = queueName,
                    storageAccount = storageAccount,
                    storageKey = storageKey
                )

            val fullUrl = "$queueUrl/messages"

            webClient
                .post()
                .uri(fullUrl)
                .header("Authorization", authHeader)
                .header("x-ms-date", timestamp)
                .header("x-ms-version", "2017-04-17")
                .header("Content-Type", "application/xml")
                .header("Content-Length", xmlBody.toByteArray().size.toString())
                .header("User-Agent", "helpdesk-commands-service/1.0")
                .bodyValue(xmlBody)
                .retrieve()
                .bodyToMono(String::class.java)
                .doOnSuccess { response -> logger.info("Direct HTTP message sent successfully") }
                .doOnError { error ->
                    logger.error(
                        "Direct HTTP message send failed with Storage Account Key: ${error.message}",
                        error
                    )
                }
        } catch (e: Exception) {
            logger.error("Direct HTTP client error with Storage Account Key: ${e.message}", e)
            Mono.error(e)
        }
    }

    /** Generates RFC 1123 formatted timestamp for Azure Storage requests */
    private fun generateRfc1123Timestamp(): String {
        return ZonedDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH))
    }

    /**
     * Generates Azure Storage SharedKeyLite authorization header Format: SharedKeyLite
     * {account}:{signature}
     */
    private fun generateAuthorizationHeader(
        method: String,
        contentType: String,
        timestamp: String,
        queueName: String,
        storageAccount: String,
        storageKey: String
    ): String {
        try {
            // build canonical string for SharedKeyLite
            // format: METHOD\n\nCONTENT-TYPE\n\nCANONICALIZED-HEADERS\nCANONICALIZED-RESOURCE
            val canonicalizedHeaders = "x-ms-date:$timestamp\nx-ms-version:2017-04-17\n"
            val canonicalizedResource = "/$storageAccount/$queueName/messages"

            val stringToSign =
                "$method\n\n$contentType\n\n$canonicalizedHeaders$canonicalizedResource"

            val keyBytes = Base64.getDecoder().decode(storageKey)
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(keyBytes, "HmacSHA256")
            mac.init(secretKey)

            val signatureBytes = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
            val signature = Base64.getEncoder().encodeToString(signatureBytes)

            return "SharedKeyLite $storageAccount:$signature"
        } catch (e: NoSuchAlgorithmException) {
            logger.error("HMAC-SHA256 algorithm not available: ${e.message}")
            throw IllegalStateException("HMAC-SHA256 not available", e)
        } catch (e: InvalidKeyException) {
            logger.error("Invalid storage key: ${e.message}")
            throw IllegalArgumentException("Invalid storage account key", e)
        } catch (e: Exception) {
            logger.error("Authorization header generation failed: ${e.message}", e)
            throw IllegalStateException("Failed to generate authorization header", e)
        }
    }

    /** Extracts storage account and key from azure storage connection string */
    data class StorageCredentials(val accountName: String, val accountKey: String)

    fun parseConnectionString(connectionString: String): StorageCredentials {
        try {
            val parts =
                connectionString.split(";").associate { part ->
                    val keyValue = part.split("=", limit = 2)
                    if (keyValue.size == 2) keyValue[0] to keyValue[1] else keyValue[0] to ""
                }

            val accountName =
                parts["AccountName"]
                    ?: throw IllegalArgumentException("AccountName not found in connection string")
            val accountKey =
                parts["AccountKey"]
                    ?: throw IllegalArgumentException("AccountKey not found in connection string")

            return StorageCredentials(accountName, accountKey)
        } catch (e: Exception) {
            logger.error("Failed to parse connection string: ${e.message}")
            throw IllegalArgumentException("Invalid Azure Storage connection string", e)
        }
    }
}
