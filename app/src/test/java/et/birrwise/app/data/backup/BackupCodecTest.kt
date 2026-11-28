package et.birrwise.app.data.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCodecTest {

    private val sample = BackupSamples.full()

    private fun failureOf(text: String): BackupFailure =
        assertThrows(BackupException::class.java) { BackupCodec.decode(text) }.failure

    private fun edited(change: (JSONObject) -> Unit): String {
        val root = JSONObject(BackupCodec.encode(sample))
        change(root)
        return root.toString()
    }

    @Test
    fun everyTableSurvivesAWriteAndRead() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun anEmptyBackupSurvives() {
        val empty = BackupData.empty(5L, "0.9.0")
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }

    @Test
    fun theLargestAmountsAndOtherScriptsSurvive() {
        val big = sample.copy(
            transactions = sample.transactions.map { it.copy(amountMinorUnits = Long.MAX_VALUE) },
            accounts = sample.accounts.map { it.copy(openingBalanceMinorUnits = Long.MIN_VALUE) }
        )
        assertEquals(big, BackupCodec.decode(BackupCodec.encode(big)))
        assertEquals("Cash \u1230\u120b\u121d", BackupCodec.decode(BackupCodec.encode(sample)).accounts[1].name)
    }

    @Test
    fun textThatIsNotJsonIsNotABackup() {
        assertEquals(BackupFailure.NOT_A_BACKUP, failureOf("hello"))
        assertEquals(BackupFailure.NOT_A_BACKUP, failureOf(""))
    }

    @Test
    fun jsonFromSomethingElseIsNotABackup() {
        assertEquals(BackupFailure.NOT_A_BACKUP, failureOf("""{"hello":1}"""))
        assertEquals(BackupFailure.NOT_A_BACKUP, failureOf(edited { it.put("format", "other-app") }))
    }

    @Test
    fun aNewerOrInvalidFormatVersionIsUnsupported() {
        assertEquals(BackupFailure.UNSUPPORTED_VERSION, failureOf(edited { it.put("formatVersion", 99) }))
        assertEquals(BackupFailure.UNSUPPORTED_VERSION, failureOf(edited { it.put("formatVersion", 0) }))
    }

    @Test
    fun missingOrBrokenPartsMeanTheBackupIsDamaged() {
        assertEquals(BackupFailure.CORRUPT, failureOf(edited { it.remove("transactions") }))
        assertEquals(
            BackupFailure.CORRUPT,
            failureOf(edited { it.getJSONArray("transactions").getJSONObject(0).remove("amountMinorUnits") })
        )
        assertEquals(
            BackupFailure.CORRUPT,
            failureOf(edited { it.getJSONArray("accounts").getJSONObject(0).put("accountType", "SPACESHIP") })
        )
        assertEquals(BackupFailure.CORRUPT, failureOf(edited { it.put("createdAt", "yesterday") }))
    }

    @Test
    fun pointingAtMissingRowsOrRepeatingIdsMeansTheBackupIsDamaged() {
        assertEquals(
            BackupFailure.CORRUPT,
            failureOf(edited { it.getJSONArray("transactions").getJSONObject(0).put("accountId", "nope") })
        )
        assertEquals(
            BackupFailure.CORRUPT,
            failureOf(edited {
                val accounts = it.getJSONArray("accounts")
                accounts.getJSONObject(1).put("id", accounts.getJSONObject(0).getString("id"))
            })
        )
    }
}
