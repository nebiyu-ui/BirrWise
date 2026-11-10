package et.birrwise.app.data

import android.database.sqlite.SQLiteException
import et.birrwise.app.domain.FinanceEngine
import et.birrwise.app.domain.TransactionType.FEE
import et.birrwise.app.domain.TransactionType.TRANSFER_IN
import et.birrwise.app.domain.TransactionType.TRANSFER_OUT
import et.birrwise.app.domain.account
import et.birrwise.app.domain.etb
import et.birrwise.app.domain.tx
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoomTransferFeeTest : DatabaseTestBase() {

    private val accounts by lazy { RoomAccountRepository(db, clock) }
    private val transactions by lazy { RoomTransactionRepository(db, clock) }

    private suspend fun seed() {
        accounts.createAccount(account("A1", openingBirr = 1000))
        accounts.createAccount(account("A2", openingBirr = 500))
    }

    private val out = tx("out", "A1", TRANSFER_OUT, 30_000L, transferGroupId = "g1")
    private val into = tx("in", "A2", TRANSFER_IN, 30_000L, transferGroupId = "g1")

    @Test
    fun aTransferWithAFeeIsSavedAsThreeEntries() = runBlocking<Unit> {
        seed()
        transactions.recordTransfer(out, into, tx("fee", "A1", FEE, 1_000L))

        val saved = transactions.observeTransactions().first().transactions
        assertEquals(3, saved.size)
        val a1 = FinanceEngine.accountBalance(accounts.getAccount("A1")!!, saved).balance
        val a2 = FinanceEngine.accountBalance(accounts.getAccount("A2")!!, saved).balance
        assertEquals(etb(690), a1)
        assertEquals(etb(800), a2)
        // The combined balance dropped by the fee only.
        assertEquals(etb(1490), a1 + a2)
    }

    @Test
    fun theFeeMustBeAFeeChargedToTheSendingAccount() = runBlocking<Unit> {
        seed()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.recordTransfer(out, into, tx("fee", "A2", FEE, 1_000L)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactions.recordTransfer(
                    out,
                    into,
                    tx("fee", "A1", et.birrwise.app.domain.TransactionType.EXPENSE, 1_000L)
                )
            }
        }
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun aBadFeeRollsBackTheWholeTransfer() = runBlocking<Unit> {
        seed()
        val badFee = tx("fee", "A1", FEE, 1_000L, categoryId = "missing-category")
        assertThrows(SQLiteException::class.java) {
            runBlocking { transactions.recordTransfer(out, into, badFee) }
        }
        assertEquals(0, db.transactionDao().count())
    }
}
