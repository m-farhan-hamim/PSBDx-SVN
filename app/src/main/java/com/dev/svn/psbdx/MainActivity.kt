package com.dev.svn.psbdx

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.dev.svn.psbdx.security.ClipboardGuard
import com.dev.svn.psbdx.ui.AppViewModel
import com.dev.svn.psbdx.ui.PsbdxRoot

// FragmentActivity (a ComponentActivity subclass) is required by BiometricPrompt.
class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { PsbdxRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ClipboardGuard.clearIfExpired(this)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "com.dev.svn.psbdx" && data.path == "/oauth2redirect") vm.handleOAuth(data)
    }
}
