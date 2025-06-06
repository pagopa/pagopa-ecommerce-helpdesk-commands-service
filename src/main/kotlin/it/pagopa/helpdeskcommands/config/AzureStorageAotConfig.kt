package it.pagopa.helpdeskcommands.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

/** Configuration to register Azure Storage Queue proxy classes for GraalVM native compilation */
@Configuration @ImportRuntimeHints(AzureStorageRuntimeHints::class) class AzureStorageAotConfig

/** Runtime hints for Azure Storage Queue proxy registration */
class AzureStorageRuntimeHints : org.springframework.aot.hint.RuntimeHintsRegistrar {

    override fun registerHints(
        hints: org.springframework.aot.hint.RuntimeHints,
        classLoader: ClassLoader?
    ) {
        // Register Azure Storage Queue service proxies
        hints
            .proxies()
            .registerJdkProxy(
                com.azure.storage.queue.implementation.ServicesImpl.ServicesService::class.java
            )
        hints
            .proxies()
            .registerJdkProxy(
                com.azure.storage.queue.implementation.QueuesImpl.QueuesService::class.java
            )
        hints
            .proxies()
            .registerJdkProxy(
                com.azure.storage.queue.implementation.MessagesImpl.MessagesService::class.java
            )
        hints
            .proxies()
            .registerJdkProxy(
                com.azure.storage.queue.implementation.MessageIdsImpl.MessageIdsService::class.java
            )
    }
}
