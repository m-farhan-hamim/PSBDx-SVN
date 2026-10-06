package com.dev.svn.psbdx.data

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * All sensitive state (repo credentials, backup passphrase, OAuth tokens) lives in
 * EncryptedSharedPreferences backed by the Android KeyStore.
 */
class SecureStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "psbdx_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // ---------- repositories ----------
    fun loadRepos(): List<SvnRepo> {
        val raw = prefs.getString(KEY_REPOS, null) ?: return emptyList()
        return runCatching { reposFromJson(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    fun saveRepos(list: List<SvnRepo>) {
        prefs.edit { putString(KEY_REPOS, reposToJson(list).toString()) }
    }

    // ---------- settings ----------
    var themeMode: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(v) = prefs.edit { putString(KEY_THEME, v) }

    var firstRunDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, false)
        set(v) = prefs.edit { putBoolean(KEY_FIRST_RUN, v) }

    var driveAutoBackup: Boolean
        get() = prefs.getBoolean(KEY_DRIVE_AUTO, false)
        set(v) = prefs.edit { putBoolean(KEY_DRIVE_AUTO, v) }

    var backupPassphrase: String?
        get() = prefs.getString(KEY_PASSPHRASE, null)
        set(v) = prefs.edit { if (v == null) remove(KEY_PASSPHRASE) else putString(KEY_PASSPHRASE, v) }

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(v) = prefs.edit { putLong(KEY_LAST_BACKUP, v) }

    // ---------- OAuth ----------
    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(v) = prefs.edit { if (v == null) remove(KEY_REFRESH) else putString(KEY_REFRESH, v) }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(v) = prefs.edit { if (v == null) remove(KEY_ACCESS) else putString(KEY_ACCESS, v) }

    var accessExpiryMs: Long
        get() = prefs.getLong(KEY_ACCESS_EXP, 0L)
        set(v) = prefs.edit { putLong(KEY_ACCESS_EXP, v) }

    var pendingVerifier: String?
        get() = prefs.getString(KEY_PENDING_VERIFIER, null)
        set(v) = prefs.edit { if (v == null) remove(KEY_PENDING_VERIFIER) else putString(KEY_PENDING_VERIFIER, v) }

    var pendingState: String?
        get() = prefs.getString(KEY_PENDING_STATE, null)
        set(v) = prefs.edit { if (v == null) remove(KEY_PENDING_STATE) else putString(KEY_PENDING_STATE, v) }

    companion object {
        private const val KEY_REPOS = "repos"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FIRST_RUN = "first_run_done"
        private const val KEY_DRIVE_AUTO = "drive_auto_backup"
        private const val KEY_PASSPHRASE = "backup_passphrase"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_REFRESH = "oauth_refresh"
        private const val KEY_ACCESS = "oauth_access"
        private const val KEY_ACCESS_EXP = "oauth_access_exp"
        private const val KEY_PENDING_VERIFIER = "oauth_pending_verifier"
        private const val KEY_PENDING_STATE = "oauth_pending_state"

        fun reposToJson(list: List<SvnRepo>): JSONArray = JSONArray().also { arr ->
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id).put("alias", it.alias).put("url", it.url)
                        .put("username", it.username).put("password", it.password)
                        .put("updatedAt", it.updatedAt)
                )
            }
        }

        fun reposFromJson(arr: JSONArray): List<SvnRepo> = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SvnRepo(
                id = o.getString("id"),
                alias = o.optString("alias"),
                url = o.optString("url"),
                username = o.optString("username"),
                password = o.optString("password"),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }
}
