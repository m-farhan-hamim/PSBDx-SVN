package com.dev.svn.psbdx

import android.app.Application
import com.dev.svn.psbdx.backup.BackupManager
import com.dev.svn.psbdx.backup.GoogleAuth
import com.dev.svn.psbdx.backup.Notifier
import com.dev.svn.psbdx.data.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl
import java.io.File
import java.util.concurrent.TimeUnit

class PsbdxApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    val store: SecureStore by lazy { SecureStore(this) }
    val googleAuth: GoogleAuth by lazy { GoogleAuth(this, store, http) }
    val backup: BackupManager by lazy { BackupManager(this) }

    /** Root directory that holds every working copy. */
    val workingCopiesDir: File
        get() = File(getExternalFilesDir(null) ?: filesDir, "working-copies").apply { mkdirs() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // SVNKit looks for ~/.subversion; point it somewhere writable on Android.
        System.setProperty("user.home", filesDir.absolutePath)
        System.setProperty("svnkit.library.gnome-keyring.enabled", "false")
        DAVRepositoryFactory.setup()
        SVNRepositoryFactoryImpl.setup()
        Notifier.ensureChannel(this)
    }

    companion object {
        lateinit var instance: PsbdxApp
            private set
    }
}
