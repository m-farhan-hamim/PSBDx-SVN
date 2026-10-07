package com.dev.svn.psbdx.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.svn.psbdx.BuildConfig
import java.text.DateFormat
import java.util.Date

private enum class Ask { PASSPHRASE, EXPORT, IMPORT, RESTORE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val linked by vm.driveLinked.collectAsStateWithLifecycle()
    val auto by vm.autoBackup.collectAsStateWithLifecycle()
    val hasPass by vm.hasPassphrase.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val last by vm.lastBackup.collectAsStateWithLifecycle()

    var ask by remember { mutableStateOf<Ask?>(null) }
    var pendingPass by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) vm.exportLocal(uri, pendingPass) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importLocal(uri, pendingPass) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Section("Appearance") {
                listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(theme == key, role = Role.RadioButton) { vm.setTheme(key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == key, onClick = null)
                        Text(label, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
                    }
                }
            }

            Section("Backup passphrase") {
                Text(
                    "Backups are encrypted with this passphrase (AES-256-GCM) before they leave the app. " +
                        "Without it a backup cannot be restored.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { ask = Ask.PASSPHRASE }) {
                    Text(if (hasPass) "Change passphrase" else "Set passphrase")
                }
            }

            Section("Local backup") {
                Text("Works without any Google account.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ask = Ask.EXPORT }, enabled = !busy) { Text("Export file") }
                    OutlinedButton(onClick = { ask = Ask.IMPORT }, enabled = !busy) { Text("Import file") }
                }
            }

            Section("Google Drive") {
                if (!vm.driveConfigured) {
                    Text(
                        "Google Drive sync is unavailable in this build (no Google client ID was configured). " +
                            "Local backup above still works.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        if (linked) "Linked — stores one encrypted file in this app's private Drive folder."
                        else "Not linked. Sign-in happens in your browser (OAuth 2.0 + PKCE); no Google Play Services needed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-backup on changes")
                            Text(
                                "Runs right after a repository is added or edited.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = auto,
                            onCheckedChange = { on ->
                                when {
                                    !on -> vm.setAutoBackup(false)
                                    !linked -> vm.startLink(activity) // sign in first, then the setup flow runs
                                    !hasPass -> vm.startDriveSetup()
                                    else -> vm.setAutoBackup(true)
                                }
                            },
                        )
                    }
                    if (last > 0) {
                        Text(
                            "Last backup: " + DateFormat.getDateTimeInstance().format(Date(last)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (linked) OutlinedButton(onClick = { vm.unlink() }) { Text("Unlink") }
                        else Button(onClick = { vm.startLink(activity) }) { Text("Link Google Drive") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (hasPass) vm.backupNow() else vm.startDriveSetup() },
                            enabled = linked && !busy,
                        ) { Text("Backup Now") }
                        OutlinedButton(onClick = { ask = Ask.RESTORE }, enabled = linked && !busy) { Text("Restore") }
                    }
                }
            }

            Section("Support") {
                Text(
                    "Found a bug or have an idea? Open an issue on GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/m-farhan-hamim/PSBDx-SVN/issues"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                ) { Text("Contact support") }
            }

            Section("About") {
                Text("PSBDx SVN ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEV_BUILD) "  (developer build)" else "")
                Text(
                    "Passwords are kept in EncryptedSharedPreferences (Android Keystore) and are only shown or " +
                        "copied after biometric / device-credential authentication.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    when (ask) {
        Ask.PASSPHRASE -> TextInputDialog(
            "Backup passphrase", "Passphrase", vm.storedPassphrase, "Save", password = true,
            onConfirm = { vm.setPassphrase(it); ask = null }, onDismiss = { ask = null },
        )
        Ask.EXPORT -> TextInputDialog(
            "Encrypt backup", "Passphrase", vm.storedPassphrase, "Choose file", password = true,
            onConfirm = { pendingPass = it; ask = null; exportLauncher.launch("psbdx-svn-backup.enc") },
            onDismiss = { ask = null },
        )
        Ask.IMPORT -> TextInputDialog(
            "Decrypt backup", "Passphrase", vm.storedPassphrase, "Choose file", password = true,
            onConfirm = { pendingPass = it; ask = null; importLauncher.launch(arrayOf("*/*")) },
            onDismiss = { ask = null },
        )
        Ask.RESTORE -> TextInputDialog(
            "Restore from Drive", "Passphrase", vm.storedPassphrase, "Restore", password = true,
            onConfirm = { vm.restoreFromDrive(it); ask = null }, onDismiss = { ask = null },
        )
        null -> {}
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
