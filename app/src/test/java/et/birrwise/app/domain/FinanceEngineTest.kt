package et.birrwise.app.domain

import et.birrwise.app.domain.TransactionStatus.PENDING_REVIEW
import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.TransactionType.FEE
import et.birrwise.app.domain.TransactionType.INCOME
import et.birrwise.app.domain.TransactionType.REFUND
import et.birrwise.app.domain.TransactionType.REVERSAL
import et.birrwise.app.domain.TransactionType.TRANSFER_IN
import et.birrwise.app.domain.TransactionType.TRANSFER_OUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class FinanceEngineTest {

    private val january = ReportingPeriods.month(YearMonth.of(2026, 1), ZoneOffset.UTC)

    private fun balance(account: Account, transactions: List<Transaction>): Money =
        FinanceEngine.accountBalance(account, transactions).balance

    private fun summary(transactions: List<Transaction>, accountIds: Set<String>? = null): PeriodSummary =
        FinanceEngine.periodSummary(ETB, transactions, january, accountIds)

    // ---- Balances -------------------------------------------------------------------------

    @Test
    fun incomeIncreasesOnlyItsOwnAccount() {
        val transactions = listOf(tx("t1", "A", INCOME, 50_000L))
        assertEquals(etb(1500), balance(account("A", openingBirr = 1000), transactions))
        assertEquals(etb(0), balance(account("B"), transactions))
    }

    @Test
    fun expenseDecreasesTheBalance() {
        val transactions = listOf(tx("t1", "A", EXPENSE, 20_050L))
        assertEquals(etb(799, 50), balance(account("A", openingBirr = 1000), transactions))
    }

    @Test
    fun transferKeepsTheCombinedBalanceAndIsNotIncomeOrExpense() {
        val a = account("A", openingBirr = 1000)
        val b = account("B", openingBirr = 500)
        val transactions = listOf(
            tx("out", "A", TRANSFER_OUT, 30_000L, transferGroupId = "g1"),
            tx("in", "B", TRANSFER_IN, 30_000L, transferGroupId = "g1")
        )
        val balanceA = balance(a, transactions)
        val balanceB = balance(b, transactions)
        assertEquals(etb(700), balanceA)
        assertEquals(etb(800), balanceB)
        assertEquals(etb(1500), balanceA + balanceB)

        val result = summary(transactions)
        assertEquals(etb(0), result.income)
        assertEquals(etb(0), result.expenses)
        assertEquals(0, result.unpairedTransferLegs)
    }

    @Test
    fun transferFeeIsRecordedSeparately() {
        val a = account("A", openingBirr = 1000)
        val b = account("B", openingBirr = 500)
        val transactions = listOf(
            tx("out", "A", TRANSFER_OUT, 30_000L, transferGroupId = "g1"),
            tx("in", "B", TRANSFER_IN, 30_000L, transferGroupId = "g1"),
            tx("fee", "A", FEE, 1_000L)
        )
        assertEquals(etb(690), balance(a, transactions))
        assertEquals(etb(800), balance(b, transactions))
        // The combined balance drops by the fee only.
        assertEquals(etb(1490), balance(a, transactions) + balance(b, transactions))
        assertEquals(etb(10), summary(transactions).expenses)
    }

    @Test
    fun refundReducesSpendingInsteadOfCountingAsIncome() {
        val transactions = listOf(
            tx("e1", "A", EXPENSE, 10_000L, categoryId = "food"),
            tx("r1", "A", REFUND, 4_000L, categoryId = "food")
        )
        assertEquals(etb(940), balance(account("A", openingBirr = 1000), transactions))
        val result = summary(transactions)
        assertEquals(etb(0), result.income)
        assertEquals(etb(60), result.expenses)
        assertEquals(
            listOf(CategorySpend("food", etb(60))),
            FinanceEngine.categorySpending(ETB, transactions, january)
        )
    }

    // ---- Reversals ------------------------------------------------------------------------

    @Test
    fun reversedExpenseIsNotCountedTwice() {
        val transactions = listOf(
            tx("e1", "A", EXPENSE, 10_000L, categoryId = "food"),
            tx("rev", "A", REVERSAL, 10_000L, reversesTransactionId = "e1")
        )
        assertEquals(etb(1000), balance(account("A", openingBirr = 1000), transactions))
        assertEquals(etb(0), summary(transactions).expenses)
        assertTrue(FinanceEngine.categorySpending(ETB, transactions, january).isEmpty())
    }

    @Test
    fun reversedIncomeIsRemovedFromIncomeAndBalance() {
        val transactions = listOf(
            tx("i1", "A", INCOME, 20_000L),
            tx("rev", "A", REVERSAL, 20_000L, reversesTransactionId = "i1")
        )
        assertEquals(etb(1000), balance(account("A", openingBirr = 1000), transactions))
        assertEquals(etb(0), summary(transactions).income)
    }

    @Test
    fun partialReversalUndoesOnlyItsOwnAmount() {
        val transactions = listOf(
            tx("e1", "A", EXPENSE, 10_000L),
            tx("rev", "A", REVERSAL, 3_000L, reversesTransactionId = "e1")
        )
        assertEquals(etb(930), balance(account("A", openingBirr = 1000), transactions))
        assertEquals(etb(70), summary(transactions).expenses)
    }

    @Test
    fun reversalWithoutAValidOriginalIsReportedNotApplied() {
        val a = account("A", openingBirr = 1000)
        val transactions = listOf(
            tx("noLink", "A", REVERSAL, 1_000L),
            tx("missing", "A", REVERSAL, 1_000L, reversesTransactionId = "ghost"),
            tx("e1", "A", EXPENSE, 5_000L),
            tx("r1", "A", REVERSAL, 5_000L, reversesTransactionId = "e1"),
            tx("r2", "A", REVERSAL, 5_000L, reversesTransactionId = "r1")
        )
        val result = FinanceEngine.accountBalance(a, transactions)
        assertEquals(etb(1000), result.balance)
        assertEquals(
            listOf(
                EngineWarning("noLink", WarningType.REVERSAL_MISSING_LINK),
                EngineWarning("missing", WarningType.REVERSAL_ORIGINAL_NOT_FOUND),
                EngineWarning("r2", WarningType.REVERSAL_OF_REVERSAL)
            ),
            result.warnings
        )
    }

    // ---- Opening balance and time ---------------------------------------------------------

    @Test
    fun openingBalanceDoesNotDoubleCountEarlierActivity() {
        val a = account("A", openingBirr = 1000, openingDate = at("2026-02-01T00:00:00Z"))
        val transactions = listOf(
            tx("before", "A", INCOME, 50_000L, occurredAt = at("2026-01-15T10:00:00Z")),
            tx("onDate", "A", EXPENSE, 10_000L, occurredAt = at("2026-02-01T00:00:00Z")),
            tx("after", "A", INCOME, 5_000L, occurredAt = at("2026-02-10T10:00:00Z"))
        )
        val result = FinanceEngine.accountBalance(a, transactions)
        assertEquals(etb(950), result.balance)
        assertEquals(2, result.countedTransactions)
    }

    @Test
    fun withoutAnOpeningDateEveryTransactionCounts() {
        val a = account("A", openingBirr = 1000)
        val transactions = listOf(
            tx("before", "A", INCOME, 50_000L, occurredAt = at("2026-01-15T10:00:00Z")),
            tx("onDate", "A", EXPENSE, 10_000L, occurredAt = at("2026-02-01T00:00:00Z")),
            tx("after", "A", INCOME, 5_000L, occurredAt = at("2026-02-10T10:00:00Z"))
        )
        assertEquals(etb(1450), balance(a, transactions))
    }

    @Test
    fun balanceCanBeCalculatedAsOfAMoment() {
        val a = account("A", openingBirr = 1000)
        val transactions = listOf(
            tx("t1", "A", INCOME, 10_000L, occurredAt = at("2026-01-10T00:00:00Z")),
            tx("t2", "A", EXPENSE, 3_000L, occurredAt = at("2026-01-20T00:00:00Z"))
        )
        val asOf = at("2026-01-15T00:00:00Z")
        assertEquals(etb(1100), FinanceEngine.accountBalance(a, transactions, asOf).balance)
        assertEquals(etb(1070), FinanceEngine.accountBalance(a, transactions).balance)
    }

    @Test
    fun monthBoundariesFollowTheLocalTimeZone() {
        val addis = ZoneId.of("Africa/Addis_Ababa")
        val october = ReportingPeriods.month(YearMonth.of(2026, 10), addis)
        val november = ReportingPeriods.month(YearMonth.of(2026, 11), addis)
        val transactions = listOf(
            tx("lastOfOctober", "A", EXPENSE, 10_000L, occurredAt = at("2026-10-31T20:59:59Z")),
            tx("firstOfNovember", "A", EXPENSE, 20_000L, occurredAt = at("2026-10-31T21:00:00Z"))
        )
        assertEquals(etb(100), FinanceEngine.periodSummary(ETB, transactions, october).expenses)
        assertEquals(etb(200), FinanceEngine.periodSummary(ETB, transactions, november).expenses)
    }

    // ---- Statuses, exclusions, and warnings ----------------------------------------------

    @Test
    fun pendingTransactionsAreLeftOutAndCounted() {
        val transactions = listOf(tx("p1", "A", INCOME, 50_000L, status = PENDING_REVIEW))
        val balanceResult = FinanceEngine.accountBalance(account("A", openingBirr = 1000), transactions)
        assertEquals(etb(1000), balanceResult.balance)
        assertEquals(1, balanceResult.pendingTransactionsExcluded)

        val result = summary(transactions)
        assertEquals(etb(0), result.income)
        assertEquals(1, result.pendingTransactionsExcluded)
    }

    @Test
    fun transactionsExcludedFromReportsStillMoveTheBalance() {
        val transactions = listOf(tx("x1", "A", EXPENSE, 10_000L, excluded = true))
        assertEquals(etb(900), balance(account("A", openingBirr = 1000), transactions))
        val result = summary(transactions)
        assertEquals(etb(0), result.expenses)
        assertEquals(1, result.excludedFromReports)
    }

    @Test
    fun unpairedTransferLegsAreFlagged() {
        val transactions = listOf(
            tx("noGroup", "A", TRANSFER_OUT, 10_000L),
            tx("lonely", "A", TRANSFER_OUT, 20_000L, transferGroupId = "g9")
        )
        assertEquals(
            setOf("noGroup", "lonely"),
            FinanceEngine.unpairedTransferLegs(transactions)
        )
        val result = summary(transactions)
        assertEquals(2, result.unpairedTransferLegs)
        assertEquals(etb(0), result.expenses)
        // The money still left the account.
        assertEquals(etb(700), balance(account("A", openingBirr = 1000), transactions))
    }

    @Test
    fun foreignCurrencyTransactionIsReportedNotMixedIn() {
        val a = account("A", openingBirr = 1000)
        val transactions = listOf(tx("usd", "A", INCOME, 5_000L, currency = "USD"))
        val result = FinanceEngine.accountBalance(a, transactions)
        assertEquals(etb(1000), result.balance)
        assertEquals(listOf(EngineWarning("usd", WarningType.CURRENCY_MISMATCH)), result.warnings)
    }

    @Test
    fun otherAccountsAreIgnoredWhenCalculatingABalance() {
        val transactions = listOf(tx("t1", "B", EXPENSE, 99_900L))
        assertEquals(etb(1000), balance(account("A", openingBirr = 1000), transactions))
    }

    // ---- Summaries ------------------------------------------------------------------------

    @Test
    fun summaryCanBeLimitedToSomeAccounts() {
        val transactions = listOf(
            tx("a1", "A", INCOME, 10_000L),
            tx("b1", "B", INCOME, 20_000L)
        )
        assertEquals(etb(100), summary(transactions, setOf("A")).income)
        assertEquals(etb(300), summary(transactions).income)
    }

    @Test
    fun summaryIgnoresOtherPeriodsAndCurrencies() {
        val transactions = listOf(
            tx("inJan", "A", INCOME, 10_000L, occurredAt = at("2026-01-31T23:59:59Z")),
            tx("inFeb", "A", INCOME, 20_000L, occurredAt = at("2026-02-01T00:00:00Z")),
            tx("usd", "A", INCOME, 30_000L, currency = "USD")
        )
        val result = summary(transactions)
        assertEquals(etb(100), result.income)
        assertEquals(1, result.includedTransactions)
        assertEquals(etb(100), result.netCashFlow)
    }

    @Test
    fun netCashFlowIsIncomeMinusExpenses() {
        val transactions = listOf(
            tx("i1", "A", INCOME, 100_000L),
            tx("e1", "A", EXPENSE, 25_000L),
            tx("f1", "A", FEE, 50L)
        )
        val result = summary(transactions)
        assertEquals(etb(1000), result.income)
        assertEquals(etb(250, 50), result.expenses)
        assertEquals(etb(749, 50), result.netCashFlow)
    }

    @Test
    fun categorySpendingIsSortedLargestFirstWithUncategorizedLast() {
        val transactions = listOf(
            tx("e1", "A", EXPENSE, 10_000L, categoryId = "transport"),
            tx("e2", "A", EXPENSE, 30_000L, categoryId = "food"),
            tx("e3", "A", EXPENSE, 10_000L, categoryId = "airtime"),
            tx("e4", "A", EXPENSE, 5_000L)
        )
        assertEquals(
            listOf(
                CategorySpend("food", etb(300)),
                CategorySpend("airtime", etb(100)),
                CategorySpend("transport", etb(100)),
                CategorySpend(null, etb(50))
            ),
            FinanceEngine.categorySpending(ETB, transactions, january)
        )
    }

    // ---- Overflow -------------------------------------------------------------------------

    @Test
    fun balanceOverflowFailsLoudly() {
        val a = Account("A", "Huge", ETB, Money(Long.MAX_VALUE, ETB))
        val transactions = listOf(tx("t1", "A", INCOME, 1L))
        assertThrows(ArithmeticException::class.java) {
            FinanceEngine.accountBalance(a, transactions)
        }
    }
}
