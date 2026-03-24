package it.pagopa.helpdeskcommands.repositories.ecommercehistory

import it.pagopa.ecommerce.commons.documents.BaseTransactionView
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

/** eCommerce Mongo transaction view repository for history db */
@Repository
interface TransactionsViewHistoryRepository : ReactiveCrudRepository<BaseTransactionView, String> {
    fun findByTransactionId(transactionId: String?): Mono<BaseTransactionView?>
}
