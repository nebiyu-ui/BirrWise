package et.birrwise.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val plain = """{"secret":"1,234,567.89 ETB"}""".toByteArray(Charsets.UTF_8)
    private val passphrase = "correct horse battery".toCharArray()

    @Test
    fun anEncryptedBackupOpensWithTheSamePassphrase() {
        val sealed = BackupCrypto.encrypt(plain, passphrase)
        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertArrayEquals(plain, BackupCrypto.decrypt(sealed, passphrase))
    }

    @Test
    fun passphrasesWithOtherScriptsWork() {
        val amharic = "\u12a5\u1295\u12f0\u121d\u1295 \u12a0\u12f0\u122d\u12cd-pässwörd".toCharArray()
        val sealed = BackupCrypto.encrypt(plain, amharic)
        assertArrayEquals(plain, BackupCrypto.decrypt(sealed, amharic))
    }

    @Test
    fun aWrongPassphraseIsRejected() {
        val sealed = BackupCrypto.encrypt(plain, passphrase)
        assertThrows(WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(sealed, "not the passphrase".toCharArray())
        }
    }

    @Test
    fun aChangedFileIsRejected() {
        val sealed = BackupCrypto.encrypt(plain, passphrase)
        sealed[sealed.size - 5] = (sealed[sealed.size - 5].toInt() xor 0x01).toByte()
        assertThrows(WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(sealed, passphrase)
        }
    }

    @Test
    fun theSameDataEncryptsDifferentlyEachTime() {
        val first = BackupCrypto.encrypt(plain, passphrase)
        val second = BackupCrypto.encrypt(plain, passphrase)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, BackupCrypto.decrypt(second, passphrase))
    }

    @Test
    fun theFileDoesNotContainTheReadableText() {
        val sealed = String(BackupCrypto.encrypt(plain, passphrase), Charsets.ISO_8859_1)
        assertFalse(sealed.contains("ETB"))
        assertFalse(sealed.contains("secret"))
    }

    @Test
    fun plainDataIsNotTakenForEncryptedData() {
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertFalse(BackupCrypto.isEncrypted(ByteArray(0)))
        val error = assertThrows(BackupException::class.java) { BackupCrypto.decrypt(plain, passphrase) }
        assertEquals(BackupFailure.NOT_A_BACKUP, error.failure)
    }

    @Test
    fun aTruncatedFileIsReportedAsDamaged() {
        val sealed = BackupCrypto.encrypt(plain, passphrase)
        val error = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(sealed.copyOf(20), passphrase)
        }
        assertEquals(BackupFailure.CORRUPT, error.failure)
    }

    @Test
    fun aNewerFileVersionIsReportedAsUnsupported() {
        val sealed = BackupCrypto.encrypt(plain, passphrase)
        sealed[4] = 2
        val error = assertThrows(BackupException::class.java) { BackupCrypto.decrypt(sealed, passphrase) }
        assertEquals(BackupFailure.UNSUPPORTED_VERSION, error.failure)
        assertNotEquals(BackupFailure.CORRUPT, error.failure)
    }
}
