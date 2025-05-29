package it.pagopa.helpdeskcommands.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("azurestorage.queues")
data class QueueConfig(
    val storageConnectionString: String,
    val transactionRefundQueueName: String,
    val transactionNotificationRequestedQueueName: String
)
