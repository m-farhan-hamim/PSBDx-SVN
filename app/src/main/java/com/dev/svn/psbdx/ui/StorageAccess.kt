package com.dev.svn.psbdx.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dev.svn.psbdx.storage.StoragePermission

/**
 * Returns a gate: `gate { action }` runs [action] right away when file access is granted,
 * otherwise explains why it is needed, sends the user to the permission screen
 * ("Allow access to manage all files" on Android 11+) and runs [action] once it was granted.
 */
@Composable
fun rememberStorageAccess(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending
        pending = null
        if (action != null && StoragePermission.hasAccess(context)) action()
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && Build.VERSION.SDK_INT >= 30) {
                val action = pending
                if (action != null && !showDialog && StoragePermission.hasAccess(context)) {
                    pending = null
                    action()
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; pending = null },
            title = { Text("Allow access to files") },
            text = {
                Text(
                    "To browse your device and external storage (SD card, USB drive) and copy files into " +
                        "a repository, PSBDx SVN needs permission to manage files on this device. " +
                        "Files are only read when you select them.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    if (Build.VERSION.SDK_INT >= 30) context.startActivity(StoragePermission.settingsIntent(context))
                    else legacyLauncher.launch(StoragePermission.legacyPermissions)
                }) { Text("Grant access") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false; pending = null }) { Text("Not now") } },
        )
    }

    return remember {
        { action: () -> Unit ->
            if (StoragePermission.hasAccess(context)) action()
            else { pending = action; showDialog = true }
        }
    }
}
