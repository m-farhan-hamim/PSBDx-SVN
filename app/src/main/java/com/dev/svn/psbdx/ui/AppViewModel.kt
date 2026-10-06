package com.dev.svn.psbdx.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev.svn.psbdx.PsbdxApp
import com.dev.svn.psbdx.data.SvnRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PsbdxApp

    val repos = MutableStateFlow(app.store.loadRepos())
    val themeMode = MutableStateFlow(app.store.themeMode)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val driveLinked = MutableStateFlow(app.googleAuth.isSignedIn)
    val autoBackup = MutableStateFlow(app.store.driveAutoBackup)
    val hasPassphrase = MutableStateFlow(!app.store.backupPassphrase.isNullOrEmpty())
    val lastBackup = MutableStateFlow(app.store.lastBackupAt)

    /** False when the build ships without Google client credentials: Drive UI is disabled. */
    val driveConfigured: Boolean get() = app.googleAuth.isConfigured
    val storedPassphrase: String get() = app.store.backupPassphrase.orEmpty()

    fun toast(text: String) { message.value = text }
    fun consumeMessage() { message.value = null }

    // ---------- repositories ----------
    fun saveRepo(alias: String, url: String, user: String, pass: String, existing: SvnRepo?) {
        val repo = SvnRepo(
            id = existing?.id ?: UUID.randomUUID().toString(),
            alias = alias, url = url, username = user,
            password = if (pass.isEmpty() && existing != null) existing.password else pass,
            updatedAt = System.currentTimeMillis(),
        )
        persist((repos.value.filter { it.id != repo.id } + repo).sortedBy { it.alias.lowercase() })
    }

    fun deleteRepo(repo: SvnRepo) {
        viewModelScope.launch(Dispatchers.IO) {
            File(app.workingCopiesDir, repo.id).deleteRecursively()
        }
        persist(repos.value.filter { it.id != repo.id })
    }

    private fun persist(list: List<SvnRepo>) {
        app.store.saveRepos(list)
        repos.value = list
        app.backup.onCredentialsChanged() // immediate auto-backup trigger (no timers)
    }

    // ---------- settings ----------
    fun setTheme(mode: String) { app.store.themeMode = mode; themeMode.value = mode }
    fun setPassphrase(p: String) { app.store.backupPassphrase = p; hasPassphrase.value = true }
    fun setAutoBackup(on: Boolean) { app.store.driveAutoBackup = on; autoBackup.value = on }

    // ---------- Google Drive ----------
    fun startLink(activity: android.app.Activity) = app.googleAuth.startSignIn(activity)

    fun handleOAuth(uri: Uri) {
        viewModelScope.launch {
            app.googleAuth.handleRedirect(uri)
                .onSuccess {
                    driveLinked.value = true
                    message.value = "Google Drive linked"
                    if (app.store.driveAutoBackup && app.backup.driveReady) backupNow()
                }
                .onFailure { message.value = it.message ?: "Sign-in failed" }
        }
    }

    fun unlink() {
        viewModelScope.launch {
            app.googleAuth.signOut()
            driveLinked.value = false
            message.value = "Google Drive unlinked"
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            busy.value = true
            app.backup.backupToDrive()
                .onSuccess { lastBackup.value = app.store.lastBackupAt }
                .onFailure { message.value = it.message ?: "Backup failed" }
            busy.value = false
        }
    }

    fun restoreFromDrive(passphrase: String) {
        viewModelScope.launch {
            busy.value = true
            app.backup.restoreFromDrive(passphrase)
                .onSuccess { repos.value = app.store.loadRepos(); message.value = "Restored $it repositories" }
                .onFailure { message.value = it.message ?: "Restore failed" }
            busy.value = false
        }
    }

    // ---------- local backup (works with no Google account at all) ----------
    fun exportLocal(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = app.backup.buildEncrypted(passphrase)
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw IOException("Cannot write file")
                }
            }.onSuccess {
                message.value = "Backup file saved"
                com.dev.svn.psbdx.backup.Notifier.backupSucceeded(app, "Encrypted backup file saved.")
            }.onFailure { message.value = it.message ?: "Export failed" }
            busy.value = false
        }
    }

    fun importLocal(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("Cannot read file")
                    app.backup.restoreFrom(bytes, passphrase)
                }
            }.onSuccess {
                repos.value = app.store.loadRepos()
                message.value = "Restored $it repositories"
            }.onFailure { message.value = it.message ?: "Import failed" }
            busy.value = false
        }
    }
}
