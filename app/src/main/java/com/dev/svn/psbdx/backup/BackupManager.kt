package com.dev.svn.psbdx.backup

import com.dev.svn.psbdx.PsbdxApp
import com.dev.svn.psbdx.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class BackupManager(private val app: PsbdxApp) {
    private val mutex = Mutex()
    private val drive by lazy { DriveClient(app.googleAuth, app.http) }
    private var workingCopyJob: Job? = null

    // ---------- local (always available, no Google involved) ----------
    /** Repositories + credentials + uncommitted working-copy edits, gzipped then AES-256-GCM encrypted. */
    fun buildEncrypted(passphrase: String): ByteArray {
        val repos = app.store.loadRepos()
        val json = JSONObject()
            .put("format", 2)
            .put("createdAt", System.currentTimeMillis())
            .put("repos", SecureStore.reposToJson(repos))
            .put("workingCopies", WorkingCopyBackup.collect(app, repos))
        return BackupCrypto.encrypt(gzip(json.toString().toByteArray(Charsets.UTF_8)), passphrase.toCharArray())
    }

    /** Merges a backup into the local store (newer entry wins). Returns the number of repositories read. */
    fun restoreFrom(blob: ByteArray, passphrase: String): Int {
        val decrypted = try {
            BackupCrypto.decrypt(blob, passphrase.toCharArray())
        } catch (e: java.security.GeneralSecurityException) {
            throw IOException("Wrong passphrase or corrupted backup")
        }
        // format 1 backups are plain JSON, format 2 is gzipped JSON
        val plain = if (decrypted.size > 2 && decrypted[0] == 0x1f.toByte() && decrypted[1] == 0x8b.toByte()) {
            gunzip(decrypted)
        } else decrypted
        val root = JSONObject(String(plain, Charsets.UTF_8))
        val incoming = SecureStore.reposFromJson(root.getJSONArray("repos"))
        val merged = app.store.loadRepos().associateBy { it.id }.toMutableMap()
        incoming.forEach { r ->
            val old = merged[r.id]
            if (old == null || r.updatedAt >= old.updatedAt) merged[r.id] = r
        }
        app.store.saveRepos(merged.values.sortedBy { it.alias.lowercase() })

        // Keep uncommitted edits for repositories that have no working copy on this device yet.
        // Repositories that are already checked out keep their own (newer) local state untouched.
        val snapshots = root.optJSONArray("workingCopies")
        if (snapshots != null) {
            for (i in 0 until snapshots.length()) {
                val snap = snapshots.getJSONObject(i)
                val id = snap.optString("repoId")
                if (id in merged && !File(File(app.workingCopiesDir, id), ".svn").isDirectory) {
                    PendingChanges.save(app, id, snap)
                }
            }
        }
        return incoming.size
    }

    /** Repositories whose uncommitted edits wait to be re-applied after checkout. */
    fun pendingCount(): Int = PendingChanges.count(app)

    // ---------- Google Drive ----------
    val driveReady: Boolean
        get() = app.googleAuth.isSignedIn && !app.store.backupPassphrase.isNullOrEmpty()

    suspend fun backupToDrive(notify: Boolean = true): Result<Unit> = runCatching {
        mutex.withLock {
            val pass = app.store.backupPassphrase
                ?: throw IOException("Set a backup passphrase first")
            if (!app.googleAuth.isSignedIn) throw IOException("Google Drive is not linked")
            // key derivation + working-copy scan are heavy: never on the main thread
            val bytes = withContext(Dispatchers.IO) { buildEncrypted(pass) }
            drive.upload(bytes)
            app.store.lastBackupAt = System.currentTimeMillis()
        }
        if (notify) Notifier.backupSucceeded(app, "Your repositories were backed up to Google Drive.")
    }

    suspend fun restoreFromDrive(passphrase: String): Result<Int> = runCatching {
        mutex.withLock {
            val blob = drive.download() ?: throw IOException("No backup found in Google Drive")
            withContext(Dispatchers.IO) { restoreFrom(blob, passphrase) }
        }
    }

    /** Newest backup in Drive (null = none), used right after linking an account. */
    suspend fun checkRemote(): Result<RemoteBackup?> = runCatching {
        mutex.withLock { drive.findBackup() }
    }

    /** Permanently removes the existing Drive backup(s). */
    suspend fun deleteRemote(): Result<Unit> = runCatching {
        mutex.withLock { drive.deleteAll() }
    }

    /**
     * Event-driven trigger (no periodic timers): called whenever a repository / credential is
     * added or updated. Silent no-op unless Drive auto-backup is fully set up.
     */
    fun onCredentialsChanged() {
        if (!app.store.driveAutoBackup || !driveReady) return
        app.appScope.launch { backupToDrive() }
    }

    /**
     * Called after files in a working copy change (save, upload, paste, delete, commit...).
     * Waits until edits stop for a few seconds, then backs up silently (no notification spam).
     */
    @Synchronized
    fun onWorkingCopyChanged() {
        if (!app.store.driveAutoBackup || !driveReady) return
        workingCopyJob?.cancel()
        workingCopyJob = app.appScope.launch {
            delay(15_000)
            backupToDrive(notify = false)
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(data.inputStream()).use { it.readBytes() }
}
