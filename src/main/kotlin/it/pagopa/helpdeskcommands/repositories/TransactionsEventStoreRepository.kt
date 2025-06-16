package it.pagopa.helpdeskcommands.repositories

import it.pagopa.ecommerce.commons.documents.BaseTransactionEvent
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

/** eCommerce Mongo event store repository */
@Repository
interface TransactionsEventStoreRepository<T> :
    ReactiveMongoRepository<BaseTransactionEvent<T>, String> {
    fun findByTransactionIdOrderByCreationDateAsc(
        idTransaction: String
    ): Flux<BaseTransactionEvent<T>>

    fun findByTransactionIdOrderByCreationDateDesc(
        idTransaction: String
    ): Flux<BaseTransactionEvent<T>>
}
