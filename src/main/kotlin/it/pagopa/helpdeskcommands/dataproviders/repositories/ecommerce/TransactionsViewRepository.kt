package it.pagopa.helpdeskcommands.dataproviders.repositories.ecommerce

import it.pagopa.ecommerce.commons.documents.BaseTransactionView
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** eCommerce Mongo transaction view repository */
@Repository
interface TransactionsViewRepository : ReactiveCrudRepository<BaseTransactionView, String> {
    @Query(value = "{ 'transactionId': ?0 }", sort = "{ 'creationDate': 1 }")
    fun findByTransactionIdOrderByCreationDateAsc(transactionId: String): Flux<BaseTransactionView>
    fun findByTransactionId(transactionId: String?): Mono<BaseTransactionView?>
}
