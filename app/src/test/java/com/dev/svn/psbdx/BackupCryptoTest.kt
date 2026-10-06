package com.dev.svn.psbdx

import com.dev.svn.psbdx.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    private val plain = """{"repos":[{"alias":"demo","password":"s3cret"}]}""".toByteArray()

    @Test
    fun roundTrip() {
        val blob = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertFalse(String(blob).contains("s3cret"))
        assertArrayEquals(plain, BackupCrypto.decrypt(blob, "correct horse".toCharArray()))
    }

    @Test
    fun wrongPassphraseFails() {
        val blob = BackupCrypto.encrypt(plain, "right".toCharArray())
        try {
            BackupCrypto.decrypt(blob, "wrong".toCharArray())
            fail("expected failure")
        } catch (_: java.security.GeneralSecurityException) {
        }
    }

    @Test
    fun rejectsGarbage() {
        try {
            BackupCrypto.decrypt(ByteArray(80), "x".toCharArray())
            fail("expected failure")
        } catch (_: IllegalArgumentException) {
        }
    }
}
