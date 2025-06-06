package it.pagopa.helpdeskcommands.controllers

import it.pagopa.helpdeskcommands.services.TransactionEventServiceInterface
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Debug controller for inspecting Spring beans and configuration
 *
 * TODO: Remove before production deployment
 */
@RestController
@RequestMapping("/debug")
class DebugController(
    private val applicationContext: ApplicationContext,
    private val environment: Environment,
    private val transactionEventService: TransactionEventServiceInterface
) {

    @GetMapping("/beans")
    fun getTransactionEventServiceBeans(): Map<String, Any> {
        val beans = applicationContext.getBeansOfType(TransactionEventServiceInterface::class.java)

        return mapOf(
            "activeService" to transactionEventService.javaClass.simpleName,
            "activeServiceBeanName" to getBeanNameForService(transactionEventService),
            "allTransactionEventServiceBeans" to beans.mapValues { it.value.javaClass.simpleName },
            "totalBeansFound" to beans.size
        )
    }

    @GetMapping("/profiles")
    fun getActiveProfiles(): Map<String, Any> {
        return mapOf(
            "activeProfiles" to environment.activeProfiles.toList(),
            "defaultProfiles" to environment.defaultProfiles.toList(),
            "graalEnabled" to environment.getProperty("spring.graal.enabled", "not-set"),
            "profilesEmpty" to environment.activeProfiles.isEmpty()
        )
    }

    @GetMapping("/properties")
    fun getRelevantProperties(): Map<String, Any> {
        return mapOf(
            "spring.graal.enabled" to environment.getProperty("spring.graal.enabled", "NOT_SET"),
            "graal.enabled" to environment.getProperty("graal.enabled", "NOT_SET"),
            "native.enabled" to environment.getProperty("native.enabled", "NOT_SET"),
            "allPropertiesWithGraal" to environment.getProperty("spring.graal.enabled", "MISSING"),
            "systemProperty" to System.getProperty("spring.graal.enabled", "NOT_IN_SYSTEM_PROPS")
        )
    }

    @GetMapping("/health")
    fun getHealthInfo(): Map<String, String> {
        return mapOf(
            "status" to "UP",
            "activeService" to transactionEventService.javaClass.simpleName,
            "timestamp" to System.currentTimeMillis().toString()
        )
    }

    @GetMapping("/service-details")
    fun getServiceDetails(): Map<String, Any> {
        return mapOf(
            "serviceName" to transactionEventService.javaClass.simpleName,
            "servicePackage" to transactionEventService.javaClass.`package`.name,
            "beanName" to getBeanNameForService(transactionEventService),
            "serviceToString" to transactionEventService.toString()
        )
    }

    private fun getBeanNameForService(service: TransactionEventServiceInterface): String {
        val beans = applicationContext.getBeansOfType(TransactionEventServiceInterface::class.java)
        return beans.entries.firstOrNull { it.value == service }?.key ?: "unknown"
    }
}
