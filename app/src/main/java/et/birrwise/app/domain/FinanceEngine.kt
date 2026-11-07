package et.birrwise.app.domain

import java.time.Instant

enum class WarningType {
    REVERSAL_MISSING_LINK,
    REVERSAL_ORIGINAL_NOT_FOUND,
    REVERSAL_OF_REVERSAL,
    CURRENCY_MISMATCH
}

/** A transaction the engine could not apply. It is reported, never silently dropped. */
data class EngineWarning(val transactionId: String, val type: WarningType)

data class AccountBalance(
    val balance: Money,
    val countedTransactions: Int,
    val pendingTransactionsExcluded: Int,
    val warnings: List<EngineWarning>
)

data class PeriodSummary(
    val period: ReportingPeriod,
    val income: Money,
    val expenses: Money,
    val netCashFlow: Money,
    val includedTransactions: Int,
    val pendingTransactionsExcluded: Int,
    val excludedFromReports: Int,
    val unpairedTransferLegs: Int,
    val warnings: List<EngineWarning>
)

data class CategorySpend(val categoryId: String?, val amount: Money)

/**
 * The single place where transactions turn into balances and report totals.
 * The rules are written down in docs/FINANCIAL_RULES.md. Every function is a pure function of its
 * inputs, so every screen that uses it agrees with every other screen.
 */
object FinanceEngine {

    private data class Effect(val balance: Long, val income: Long, val expense: Long)

    private sealed interface Resolution {
        class Ok(val effect: Effect, val categoryId: String?) : Resolution
        class Failed(val type: WarningType) : Resolution
    }

    private fun baseEffect(type: TransactionType, amount: Long): Effect = when (type) {
        TransactionType.INCOME -> Effect(balance = amount, income = amount, expense = 0L)
        TransactionType.EXPENSE -> Effect(balance = -amount, income = 0L, expense = amount)
        TransactionType.FEE -> Effect(balance = -amount, income = 0L, expense = amount)
        TransactionType.REFUND -> Effect(balance = amount, income = 0L, expense = -amount)
        TransactionType.TRANSFER_IN -> Effect(balance = amount, income = 0L, expense = 0L)
        TransactionType.TRANSFER_OUT -> Effect(balance = -amount, income = 0L, expense = 0L)
        TransactionType.REVERSAL -> throw IllegalStateException("Reversals are resolved separately")
    }

    private fun resolve(tx: Transaction, byId: Map<String, Transaction>): Resolution {
        if (tx.type != TransactionType.REVERSAL) {
            return Resolution.Ok(baseEffect(tx.type, tx.amount.minorUnits), tx.categoryId)
        }
        val originalId = tx.reversesTransactionId
            ?: return Resolution.Failed(WarningType.REVERSAL_MISSING_LINK)
        val original = byId[originalId]
            ?: return Resolution.Failed(WarningType.REVERSAL_ORIGINAL_NOT_FOUND)
        if (original.type == TransactionType.REVERSAL) {
            return Resolution.Failed(WarningType.REVERSAL_OF_REVERSAL)
        }
        // The reversal undoes the original's effect for the reversal's own amount (so partial
        // reversals work) and counts in the original's category.
        val undone = baseEffect(original.type, tx.amount.minorUnits)
        return Resolution.Ok(
            Effect(balance = -undone.balance, income = -undone.income, expense = -undone.expense),
            original.categoryId
        )
    }

    /**
     * The tracked balance of [account]: opening balance plus the effect of every confirmed
     * transaction on that account that falls on or after the opening balance date, up to [asOf]
     * when given. This is a calculated balance, not a verified bank balance.
     */
    fun accountBalance(
        account: Account,
        transactions: List<Transaction>,
        asOf: Instant? = null
    ): AccountBalance {
        val byId = transactions.associateBy { it.id }
        val openingDate = account.openingBalanceDate
        var balance = account.openingBalance.minorUnits
        var counted = 0
        var pending = 0
        val warnings = mutableListOf<EngineWarning>()

        for (tx in transactions) {
            if (tx.accountId != account.id) continue
            if (openingDate != null && tx.occurredAt < openingDate) continue
            if (asOf != null && tx.occurredAt > asOf) continue
            if (tx.status != TransactionStatus.CONFIRMED) {
                pending++
                continue
            }
            if (tx.amount.currencyCode != account.currencyCode) {
                warnings += EngineWarning(tx.id, WarningType.CURRENCY_MISMATCH)
                continue
            }
            when (val resolution = resolve(tx, byId)) {
                is Resolution.Ok -> {
                    balance = Math.addExact(balance, resolution.effect.balance)
                    counted++
                }
                is Resolution.Failed -> warnings += EngineWarning(tx.id, resolution.type)
            }
        }
        return AccountBalance(Money(balance, account.currencyCode), counted, pending, warnings)
    }

    /**
     * Income, expenses, and net cash flow for one currency in one period, optionally limited to
     * some accounts. Transfers are never income or expenses. Pending transactions and transactions
     * marked as excluded from reports are left out and counted so the screen can say so.
     */
    fun periodSummary(
        currencyCode: String,
        transactions: List<Transaction>,
        period: ReportingPeriod,
        accountIds: Set<String>? = null
    ): PeriodSummary {
        val collected = collect(currencyCode, transactions, period, accountIds)
        var income = 0L
        var expenses = 0L
        for ((_, resolved) in collected.rows) {
            income = Math.addExact(income, resolved.effect.income)
            expenses = Math.addExact(expenses, resolved.effect.expense)
        }
        val unpaired = unpairedTransferLegs(transactions)
        val unpairedInScope = collected.rows.count { (tx, _) -> tx.id in unpaired }
        return PeriodSummary(
            period = period,
            income = Money(income, currencyCode),
            expenses = Money(expenses, currencyCode),
            netCashFlow = Money(Math.subtractExact(income, expenses), currencyCode),
            includedTransactions = collected.rows.size,
            pendingTransactionsExcluded = collected.pending,
            excludedFromReports = collected.excluded,
            unpairedTransferLegs = unpairedInScope,
            warnings = collected.warnings
        )
    }

    /** Net spending per category (null means uncategorized), largest first. Zero totals are omitted. */
    fun categorySpending(
        currencyCode: String,
        transactions: List<Transaction>,
        period: ReportingPeriod,
        accountIds: Set<String>? = null
    ): List<CategorySpend> {
        val collected = collect(currencyCode, transactions, period, accountIds)
        val totals = LinkedHashMap<String?, Long>()
        for ((_, resolved) in collected.rows) {
            if (resolved.effect.expense == 0L) continue
            val key = resolved.categoryId
            totals[key] = Math.addExact(totals[key] ?: 0L, resolved.effect.expense)
        }
        return totals
            .filterValues { it != 0L }
            .map { (categoryId, amount) -> CategorySpend(categoryId, Money(amount, currencyCode)) }
            .sortedWith(
                compareByDescending<CategorySpend> { it.amount.minorUnits }
                    .thenBy { it.categoryId ?: "" }
            )
    }

    /**
     * Ids of transfer legs that have no partner: the leg has no group, or its group lacks either
     * the outgoing or the incoming side. Such money left or entered the tracked accounts without a
     * matching movement, so screens should ask the user to review it.
     */
    fun unpairedTransferLegs(transactions: List<Transaction>): Set<String> {
        val legs = transactions.filter {
            it.type == TransactionType.TRANSFER_IN || it.type == TransactionType.TRANSFER_OUT
        }
        val byGroup = legs.filter { it.transferGroupId != null }.groupBy { it.transferGroupId }
        val unpaired = mutableSetOf<String>()
        for (leg in legs) {
            val group = leg.transferGroupId
            if (group == null) {
                unpaired += leg.id
                continue
            }
            val sameGroup = byGroup[group].orEmpty()
            val hasIn = sameGroup.any { it.type == TransactionType.TRANSFER_IN }
            val hasOut = sameGroup.any { it.type == TransactionType.TRANSFER_OUT }
            if (!(hasIn && hasOut)) unpaired += leg.id
        }
        return unpaired
    }

    private class Collected(
        val rows: List<Pair<Transaction, Resolution.Ok>>,
        val pending: Int,
        val excluded: Int,
        val warnings: List<EngineWarning>
    )

    private fun collect(
        currencyCode: String,
        transactions: List<Transaction>,
        period: ReportingPeriod,
        accountIds: Set<String>?
    ): Collected {
        val byId = transactions.associateBy { it.id }
        val rows = mutableListOf<Pair<Transaction, Resolution.Ok>>()
        val warnings = mutableListOf<EngineWarning>()
        var pending = 0
        var excluded = 0

        for (tx in transactions) {
            if (accountIds != null && tx.accountId !in accountIds) continue
            if (tx.amount.currencyCode != currencyCode) continue
            if (tx.occurredAt !in period) continue
            if (tx.status != TransactionStatus.CONFIRMED) {
                pending++
                continue
            }
            if (tx.isExcludedFromReports) {
                excluded++
                continue
            }
            when (val resolution = resolve(tx, byId)) {
                is Resolution.Ok -> rows += (tx to resolution)
                is Resolution.Failed -> warnings += EngineWarning(tx.id, resolution.type)
            }
        }
        return Collected(rows, pending, excluded, warnings)
    }
}
