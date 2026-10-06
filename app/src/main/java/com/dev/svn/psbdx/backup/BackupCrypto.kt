package com.dev.svn.psbdx.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup container: MAGIC(8) | salt(16) | iv(12) | AES-256-GCM ciphertext+tag.
 * Key = PBKDF2-HMAC-SHA256(passphrase, salt). Credentials never leave the device in clear text.
 */
object BackupCrypto {
    private val MAGIC = "PSBDXBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 210_000
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    /** Throws [IllegalArgumentException] for non-backup data and a GeneralSecurityException for a wrong passphrase. */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        require(blob.size > MAGIC.size + SALT_LEN + IV_LEN + 16) { "Not a PSBDx SVN backup file" }
        require(blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a PSBDx SVN backup file" }
        var o = MAGIC.size
        val salt = blob.copyOfRange(o, o + SALT_LEN); o += SALT_LEN
        val iv = blob.copyOfRange(o, o + IV_LEN); o += IV_LEN
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return cipher.doFinal(blob, o, blob.size - o)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }
}
