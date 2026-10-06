package com.dev.svn.psbdx.backup

import com.dev.svn.psbdx.PsbdxApp
import com.dev.svn.psbdx.data.SecureStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException

class BackupManager(private val app: PsbdxApp) {
    private val mutex = Mutex()
    private val drive by lazy { DriveClient(app.googleAuth, app.http) }

    // ---------- local (always available, no Google involved) ----------
    fun buildEncrypted(passphrase: String): ByteArray {
        val json = JSONObject()
            .put("format", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("repos", SecureStore.reposToJson(app.store.loadRepos()))
        return BackupCrypto.encrypt(json.toString().toByteArray(Charsets.UTF_8), passphrase.toCharArray())
    }

    /** Merges a backup into the local store (newer entry wins). Returns the number of entries read. */
    fun restoreFrom(blob: ByteArray, passphrase: String): Int {
        val plain = try {
            BackupCrypto.decrypt(blob, passphrase.toCharArray())
        } catch (e: java.security.GeneralSecurityException) {
            throw IOException("Wrong passphrase or corrupted backup")
        }
        val incoming = SecureStore.reposFromJson(JSONObject(String(plain, Charsets.UTF_8)).getJSONArray("repos"))
        val merged = app.store.loadRepos().associateBy { it.id }.toMutableMap()
        incoming.forEach { r ->
            val old = merged[r.id]
            if (old == null || r.updatedAt >= old.updatedAt) merged[r.id] = r
        }
        app.store.saveRepos(merged.values.sortedBy { it.alias.lowercase() })
        return incoming.size
    }

    // ---------- Google Drive ----------
    val driveReady: Boolean
        get() = app.googleAuth.isSignedIn && !app.store.backupPassphrase.isNullOrEmpty()

    suspend fun backupToDrive(): Result<Unit> = runCatching {
        mutex.withLock {
            val pass = app.store.backupPassphrase
                ?: throw IOException("Set a backup passphrase first")
            if (!app.googleAuth.isSignedIn) throw IOException("Google Drive is not linked")
            drive.upload(buildEncrypted(pass))
            app.store.lastBackupAt = System.currentTimeMillis()
        }
        Notifier.backupSucceeded(app, "Your repositories were backed up to Google Drive.")
    }

    suspend fun restoreFromDrive(passphrase: String): Result<Int> = runCatching {
        mutex.withLock {
            val blob = drive.download() ?: throw IOException("No backup found in Google Drive")
            restoreFrom(blob, passphrase)
        }
    }

    /**
     * Event-driven trigger (no periodic timers): called whenever a repository / credential is
     * added or updated. Silent no-op unless Drive auto-backup is fully set up.
     */
    fun onCredentialsChanged() {
        if (!app.store.driveAutoBackup || !driveReady) return
        app.appScope.launch { backupToDrive() }
    }
}
