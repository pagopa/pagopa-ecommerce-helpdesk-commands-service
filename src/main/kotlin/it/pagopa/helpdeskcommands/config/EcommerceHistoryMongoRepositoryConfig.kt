package it.pagopa.helpdeskcommands.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories

@Configuration
@EnableReactiveMongoRepositories(
    basePackages = ["it.pagopa.helpdeskcommands.repositories.ecommercehistory"],
    reactiveMongoTemplateRef = "ecommerceHistoryReactiveMongoTemplate"
)
class EcommerceHistoryMongoRepositoryConfig {}
