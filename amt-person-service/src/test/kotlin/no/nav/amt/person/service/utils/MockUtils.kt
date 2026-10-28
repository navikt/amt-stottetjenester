package no.nav.amt.person.service.utils

import io.mockk.every
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.util.function.Consumer

fun mockExecuteWithoutResult(transactionTemplate: TransactionTemplate) {
    every { transactionTemplate.executeWithoutResult(any<Consumer<TransactionStatus>>()) } answers {
        (firstArg() as Consumer<TransactionStatus>).accept(SimpleTransactionStatus())
    }
}

fun <T> mockExecute(
    transactionTemplate: TransactionTemplate,
    transactionStatus: TransactionStatus = SimpleTransactionStatus(),
) {
    every { transactionTemplate.execute<T>(any()) } answers {
        firstArg<TransactionCallback<T>>().doInTransaction(transactionStatus)
    }
}
