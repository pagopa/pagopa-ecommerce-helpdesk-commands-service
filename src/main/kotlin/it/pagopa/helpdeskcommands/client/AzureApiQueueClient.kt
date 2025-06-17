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
class AzureApiQueueClient {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.builder().build()

    // https://learn.microsoft.com/en-us/rest/api/storageservices/previous-azure-storage-service-versions
    private val apiVersion = "2021-02-12"

    // working solution for native compile/run
    fun sendMessageWithStorageKey(
        queueUrl: String,
        queueName: String,
        message: String,
        storageAccount: String,
        storageKey: String
    ): Mono<String> {
        logger.info("Sending message via direct HTTP with Storage Account Key authentication")

        val xmlBody =
            "<?xml version='1.0' encoding='utf-8'?><QueueMessage><MessageText>$message</MessageText></QueueMessage>"

        return generateRfc1123TimestampMono()
            .flatMap { timestamp ->
                generateAuthorizationHeader(
                        method = "POST",
                        contentType = "application/xml",
                        contentLength = xmlBody.toByteArray(Charsets.UTF_8).size.toString(),
                        timestamp = timestamp,
                        queueName = queueName,
                        storageAccount = storageAccount,
                        storageKey = storageKey
                    )
                    .map { authHeader -> timestamp to authHeader }
            }
            .flatMap { (timestamp, authHeader) ->
                val fullUrl = "$queueUrl/messages"

                webClient
                    .post()
                    .uri(fullUrl)
                    .header("Authorization", authHeader)
                    .header("x-ms-date", timestamp)
                    .header("x-ms-version", apiVersion)
                    .header("Content-Type", "application/xml")
                    .header("Content-Length", xmlBody.toByteArray(Charsets.UTF_8).size.toString())
                    .header("User-Agent", "helpdesk-commands-service/1.0")
                    .bodyValue(xmlBody)
                    .retrieve()
                    .bodyToMono(String::class.java)
            }
            .doOnSuccess { response -> logger.info("Direct HTTP message sent successfully") }
            .doOnError { error ->
                logger.error(
                    "Direct HTTP message send failed with Storage Account Key: ${error.message}",
                    error
                )
            }
    }

    /** Generates RFC 1123 formatted timestamp for Azure Storage requests */
    private fun generateRfc1123Timestamp(): String {
        return ZonedDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH))
    }

    /** Reactive version of generateRfc1123Timestamp */
    private fun generateRfc1123TimestampMono(): Mono<String> {
        return Mono.fromCallable { generateRfc1123Timestamp() }
    }

    /**
     * Generates Azure Storage SharedKey authorization header Format: SharedKey
     * {account}:{signature}
     */
    private fun generateAuthorizationHeader(
        method: String,
        contentType: String,
        contentLength: String,
        timestamp: String,
        queueName: String,
        storageAccount: String,
        storageKey: String
    ): Mono<String> {
        return Mono.fromCallable {
                // build canonical string for SharedKey
                // format:
                // VERB\nContent-Encoding\nContent-Language\nContent-Length\nContent-MD5\nContent-Type\nDate\nIf-Modified-Since\nIf-Match\nIf-None-Match\nIf-Unmodified-Since\nRange\nCanonicalizedHeaders\nCanonicalizedResource
                val canonicalizedHeaders = "x-ms-date:$timestamp\nx-ms-version:$apiVersion"
                val canonicalizedResource = "/$storageAccount/$queueName/messages"

                val stringToSign =
                    "$method\n" + // VERB
                    "\n" + // Content-Encoding (empty)
                        "\n" + // Content-Language (empty)
                        "$contentLength\n" + // Content-Length
                        "\n" + // Content-MD5 (empty)
                        "$contentType\n" + // Content-Type
                        "\n" + // Date (empty because we use x-ms-date)
                        "\n" + // If-Modified-Since (empty)
                        "\n" + // If-Match (empty)
                        "\n" + // If-None-Match (empty)
                        "\n" + // If-Unmodified-Since (empty)
                        "\n" + // Range (empty)
                        canonicalizedHeaders +
                        "\n" + // CanonicalizedHeaders + newline
                        canonicalizedResource // CanonicalizedResource

                val keyBytes = Base64.getDecoder().decode(storageKey)
                val mac = Mac.getInstance("HmacSHA256")
                val secretKey = SecretKeySpec(keyBytes, "HmacSHA256")
                mac.init(secretKey)

                val signatureBytes = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
                val signature = Base64.getEncoder().encodeToString(signatureBytes)

                "SharedKey $storageAccount:$signature"
            }
            .onErrorMap { e ->
                when (e) {
                    is NoSuchAlgorithmException -> {
                        logger.error("HMAC-SHA256 algorithm not available: ${e.message}")
                        IllegalStateException("HMAC-SHA256 not available", e)
                    }
                    is InvalidKeyException -> {
                        logger.error("Invalid storage key: ${e.message}")
                        IllegalArgumentException("Invalid storage account key", e)
                    }
                    else -> {
                        logger.error("Authorization header generation failed: ${e.message}", e)
                        IllegalStateException("Failed to generate authorization header", e)
                    }
                }
            }
    }

    /** Extracts storage account and key from azure storage connection string */
    data class StorageCredentials(val accountName: String, val accountKey: String)

    fun parseConnectionString(connectionString: String): Mono<StorageCredentials> {
        return Mono.fromCallable {
                val parts =
                    connectionString.split(";").associate { part ->
                        val keyValue = part.split("=", limit = 2)
                        if (keyValue.size == 2) keyValue[0] to keyValue[1] else keyValue[0] to ""
                    }

                val accountName =
                    parts["AccountName"]
                        ?: throw IllegalArgumentException(
                            "AccountName not found in connection string"
                        )
                val accountKey =
                    parts["AccountKey"]
                        ?: throw IllegalArgumentException(
                            "AccountKey not found in connection string"
                        )

                StorageCredentials(accountName, accountKey)
            }
            .onErrorMap { e ->
                logger.error("Failed to parse connection string: ${e.message}")
                IllegalArgumentException("Invalid Azure Storage connection string", e)
            }
    }
}
