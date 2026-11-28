package et.birrwise.app.data.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Protects a backup with a passphrase. The key comes from the passphrase with PBKDF2 (HMAC-SHA256,
 * a random salt, many rounds) and the data is sealed with AES-256-GCM, which also detects any change
 * to the file. The passphrase is never stored. Without it the backup cannot be opened.
 *
 * File layout: "BWBK", one version byte, 16 bytes of salt, 12 bytes of nonce, then the sealed data.
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf(
        'B'.code.toByte(), 'W'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte()
    )
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ITERATIONS = 210_000
    private const val HEADER_BYTES = 4 + 1 + SALT_BYTES + NONCE_BYTES

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
        val sealed = cipher.doFinal(plain)
        return MAGIC + byteArrayOf(VERSION) + salt + nonce + sealed
    }

    /**
     * Opens an encrypted backup. Throws [WrongPassphraseException] if the passphrase is wrong or the
     * file was changed, and [BackupException] if the file is not a valid encrypted backup.
     */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        if (!isEncrypted(blob)) throw BackupException(BackupFailure.NOT_A_BACKUP)
        if (blob.size <= HEADER_BYTES + TAG_BITS / 8) throw BackupException(BackupFailure.CORRUPT)
        if (blob[MAGIC.size] != VERSION) throw BackupException(BackupFailure.UNSUPPORTED_VERSION)

        val saltStart = MAGIC.size + 1
        val salt = blob.copyOfRange(saltStart, saltStart + SALT_BYTES)
        val nonce = blob.copyOfRange(saltStart + SALT_BYTES, HEADER_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
        return try {
            cipher.doFinal(blob, HEADER_BYTES, blob.size - HEADER_BYTES)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
