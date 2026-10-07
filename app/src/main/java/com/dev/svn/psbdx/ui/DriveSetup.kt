package com.dev.svn.psbdx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

/** Where the "link Google Drive" wizard currently is. */
sealed interface DriveSetup {
    data object Idle : DriveSetup
    data object Checking : DriveSetup
    data class Found(val modifiedAt: Long, val busy: Boolean = false, val error: String? = null) : DriveSetup
    data class NewPassphrase(
        val hadExisting: Boolean,
        val busy: Boolean = false,
        val error: String? = null,
    ) : DriveSetup
}

private const val MIN_PASSPHRASE = 8

@Composable
fun DriveSetupHost(vm: AppViewModel) {
    val state by vm.driveSetup.collectAsStateWithLifecycle()
    when (val s = state) {
        DriveSetup.Idle -> {}
        DriveSetup.Checking -> CheckingScreen(onCancel = vm::cancelDriveSetup)
        is DriveSetup.Found -> FoundDialog(s, vm)
        is DriveSetup.NewPassphrase -> NewPassphraseDialog(s, vm)
    }
}

@Composable
private fun CheckingScreen(onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    Surface(
        modifier = Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {},
        ),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Text("Checking for existing backups…", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "You're signed in to Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun FoundDialog(s: DriveSetup.Found, vm: AppViewModel) {
    var pass by remember { mutableStateOf("") }
    var confirmForgot by remember { mutableStateOf(false) }
    val whenText = if (s.modifiedAt > 0) {
        "from " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.modifiedAt))
    } else "from an earlier date"

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Existing backup found") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A backup $whenText was found in your Google Drive. Enter its passphrase to restore your repositories.")
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Backup passphrase") },
                    singleLine = true, enabled = !s.busy, isError = s.error != null,
                    supportingText = { s.error?.let { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { confirmForgot = true }, enabled = !s.busy) {
                    Text("I forgot my passphrase")
                }
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = pass.isNotEmpty() && !s.busy, onClick = { vm.submitExistingPassphrase(pass) }) {
                Text("Restore")
            }
        },
        dismissButton = { TextButton(enabled = !s.busy, onClick = vm::cancelDriveSetup) { Text("Cancel") } },
    )

    if (confirmForgot) {
        ConfirmDialog(
            title = "Delete the existing backup?",
            text = "This permanently deletes the backup in your Google Drive. You'll choose a new passphrase and a " +
                "fresh backup is created from the repositories on this device. The old backup can't be recovered.",
            confirmText = "Delete and start over",
            onConfirm = { confirmForgot = false; vm.forgotPassphrase() },
            onDismiss = { confirmForgot = false },
        )
    }
}

@Composable
private fun NewPassphraseDialog(s: DriveSetup.NewPassphrase, vm: AppViewModel) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val tooShort = p1.isNotEmpty() && p1.length < MIN_PASSPHRASE
    val mismatch = p2.isNotEmpty() && p1 != p2
    val valid = p1.length >= MIN_PASSPHRASE && p1 == p2

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Create a backup passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (s.hadExisting) "The old backup was deleted. Choose a new passphrase; a fresh backup of the " +
                        "repositories on this device will be created."
                    else "Choose a passphrase to encrypt your backups. You'll need it to restore on another " +
                        "device and it can't be recovered.",
                )
                OutlinedTextField(
                    value = p1, onValueChange = { p1 = it }, label = { Text("New passphrase") },
                    singleLine = true, enabled = !s.busy, isError = tooShort,
                    supportingText = { if (tooShort) Text("At least $MIN_PASSPHRASE characters") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = p2, onValueChange = { p2 = it }, label = { Text("Repeat passphrase") },
                    singleLine = true, enabled = !s.busy, isError = mismatch || s.error != null,
                    supportingText = {
                        if (mismatch) Text("Passphrases don't match") else s.error?.let { Text(it) }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !s.busy, onClick = { vm.submitNewPassphrase(p1) }) { Text("Create backup") }
        },
        dismissButton = { TextButton(enabled = !s.busy, onClick = vm::cancelDriveSetup) { Text("Cancel") } },
    )
}
