package et.birrwise.app.domain

import java.time.Instant

/** An account together with its calculated (tracked) balance and a few facts for list screens. */
data class AccountSummary(
    val account: Account,
    val balance: Money,
    val countedTransactions: Int,
    val pendingTransactionsExcluded: Int,
    val warningCount: Int,
    val lastActivityAt: Instant?
)

object AccountSummaries {

    fun build(accounts: List<Account>, transactions: List<Transaction>): List<AccountSummary> =
        accounts.map { account ->
            val result = FinanceEngine.accountBalance(account, transactions)
            AccountSummary(
                account = account,
                balance = result.balance,
                countedTransactions = result.countedTransactions,
                pendingTransactionsExcluded = result.pendingTransactionsExcluded,
                warningCount = result.warnings.size,
                lastActivityAt = transactions
                    .filter { it.accountId == account.id }
                    .maxOfOrNull { it.occurredAt }
            )
        }

    /** Tracked balance per currency across active (not archived) accounts, sorted by currency code. */
    fun totalsByCurrency(summaries: List<AccountSummary>): List<Money> =
        summaries
            .filter { !it.account.archived }
            .groupBy { it.balance.currencyCode }
            .entries
            .sortedBy { it.key }
            .map { (currency, items) ->
                items.fold(Money.zero(currency)) { total, item -> total + item.balance }
            }
}
