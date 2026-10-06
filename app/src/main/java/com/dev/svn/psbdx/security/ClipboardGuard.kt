package com.dev.svn.psbdx.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/** Copies text; secrets are flagged sensitive and wiped from the clipboard after 30 seconds. */
object ClipboardGuard {
    const val CLEAR_DELAY_MS = 30_000L
    private const val SECRET_LABEL = "PSBDx SVN password"

    private val handler = Handler(Looper.getMainLooper())
    private var clearAtMs = 0L

    fun copy(context: Context, label: String, text: String) {
        manager(context).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun copySecret(context: Context, text: String) {
        val app = context.applicationContext
        val clip = ClipData.newPlainText(SECRET_LABEL, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        manager(app).setPrimaryClip(clip)
        clearAtMs = System.currentTimeMillis() + CLEAR_DELAY_MS
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ clear(app) }, CLEAR_DELAY_MS)
    }

    /** Safety net for when the process was frozen while the timer should have fired. */
    fun clearIfExpired(context: Context) {
        if (clearAtMs != 0L && System.currentTimeMillis() >= clearAtMs) clear(context.applicationContext)
    }

    private fun clear(context: Context) {
        clearAtMs = 0L
        val cm = manager(context)
        val label = cm.primaryClipDescription?.label?.toString()
        if (label != SECRET_LABEL) return // user copied something else meanwhile
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
