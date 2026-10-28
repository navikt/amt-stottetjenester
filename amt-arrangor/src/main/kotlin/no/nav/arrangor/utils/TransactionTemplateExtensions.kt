package no.nav.arrangor.utils

import org.springframework.transaction.support.TransactionTemplate

internal fun <T : Any> TransactionTemplate.executeInTransactionAndRequireResult(block: () -> T): T = checkNotNull(execute { block() }) {
    "Transaksjonen returnerte ikke et resultat"
}
