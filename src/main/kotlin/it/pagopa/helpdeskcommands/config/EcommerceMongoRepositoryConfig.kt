package it.pagopa.helpdeskcommands.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories

@Configuration
@EnableReactiveMongoRepositories(
    basePackages = ["it.pagopa.helpdeskcommands.repositories.ecommerce"],
    reactiveMongoTemplateRef = "ecommerceReactiveMongoTemplate"
)
class EcommerceMongoRepositoryConfig {}
