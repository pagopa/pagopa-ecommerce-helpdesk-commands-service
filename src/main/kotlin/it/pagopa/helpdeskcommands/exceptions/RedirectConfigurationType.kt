package it.pagopa.helpdeskcommands.exceptions

/**
 * Enumeration containing possible types of Redirect configuration. Each enum
 * variant maps to a different configuration property. These are kept separate
 * to separate secrets from common configurations.
 */
enum class RedirectConfigurationType(val configurationType: String) {
    /**
     * Configuration for PSP API keys
     */
    API_KEYS("API_KEYS"),

    /**
     * Configuration for URLs for the Redirect PSP API
     */
    BACKEND_URLS("BACKEND_URLS"),

    /**
     * Configuration for PSPs logos
     */
    LOGOS("LOGOS");
    
    companion object {
        
        fun fromConfigurationType(configurationType: String): RedirectConfigurationType = 
            entries.find { configurationType == it.configurationType }
                ?: throw IllegalArgumentException(
                    "Invalid configuration type: '$configurationType"
                )
    }
}