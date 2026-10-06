package com.dev.svn.psbdx.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.svn.psbdx.data.SvnRepo
import com.dev.svn.psbdx.security.BiometricGate
import com.dev.svn.psbdx.security.ClipboardGuard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReposScreen(vm: AppViewModel, onOpen: (SvnRepo) -> Unit, onSettings: () -> Unit) {
    val repos by vm.repos.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SvnRepo?>(null) }
    var deleting by remember { mutableStateOf<SvnRepo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PSBDx SVN") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showEditor = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add repository") },
            )
        },
    ) { pad ->
        if (repos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Folder, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("No repositories yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Save a repository bookmark, then check it out to start working.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(repos, key = { it.id }) { repo ->
                    RepoCard(
                        repo = repo, vm = vm,
                        modifier = Modifier.animateItem(),
                        onOpen = { onOpen(repo) },
                        onEdit = { editing = repo; showEditor = true },
                        onDelete = { deleting = repo },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showEditor) {
        RepoDialog(
            existing = editing,
            onSave = { alias, url, user, pass ->
                vm.saveRepo(alias, url, user, pass, editing)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
    deleting?.let { repo ->
        ConfirmDialog(
            title = "Delete \"${repo.alias}\"?",
            text = "This removes the bookmark, its saved credentials and the local working copy. The server is not touched.",
            confirmText = "Delete",
            onConfirm = { vm.deleteRepo(repo); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RepoCard(
    repo: SvnRepo,
    vm: AppViewModel,
    modifier: Modifier,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var revealed by remember { mutableStateOf(false) }

    // A revealed password hides itself again after 30 seconds.
    LaunchedEffect(revealed) {
        if (revealed) { delay(30_000); revealed = false }
    }

    fun gated(action: () -> Unit) = BiometricGate.authenticate(
        activity = activity,
        title = "Unlock saved password",
        subtitle = repo.alias,
        onSuccess = action,
        onFailure = { vm.toast(it) },
    )

    ElevatedCard(modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Text(repo.alias, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            CredRow("URL", repo.url) {
                IconButton(onClick = { ClipboardGuard.copy(context, "Repository URL", repo.url); vm.toast("URL copied") }) {
                    Icon(Icons.Default.ContentCopy, "Copy URL")
                }
            }
            CredRow("User", repo.username.ifEmpty { "(anonymous)" }) {
                IconButton(
                    enabled = repo.username.isNotEmpty(),
                    onClick = { ClipboardGuard.copy(context, "Username", repo.username); vm.toast("Username copied") },
                ) { Icon(Icons.Default.ContentCopy, "Copy username") }
            }
            CredRow("Password", if (revealed) repo.password.ifEmpty { "(none)" } else "••••••••") {
                IconButton(onClick = { if (revealed) revealed = false else gated { revealed = true } }) {
                    Icon(
                        if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (revealed) "Hide password" else "Show password",
                    )
                }
                IconButton(
                    enabled = repo.password.isNotEmpty(),
                    onClick = {
                        gated {
                            ClipboardGuard.copySecret(context, repo.password)
                            vm.toast("Password copied — clipboard clears in 30 s")
                        }
                    },
                ) { Icon(Icons.Default.ContentCopy, "Copy password") }
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onOpen) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }
}

@Composable
private fun CredRow(label: String, value: String, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
private fun RepoDialog(
    existing: SvnRepo?,
    onSave: (alias: String, url: String, user: String, pass: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var alias by remember { mutableStateOf(existing?.alias.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var user by remember { mutableStateOf(existing?.username.orEmpty()) }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val validUrl = Regex("^(https?|svn)://\\S+$").matches(url.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add repository" else "Edit repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(alias, { alias = it }, label = { Text("Alias") }, singleLine = true)
                OutlinedTextField(
                    url, { url = it }, label = { Text("Repository URL") }, singleLine = true,
                    isError = url.isNotEmpty() && !validUrl,
                    supportingText = { if (url.isNotEmpty() && !validUrl) Text("Use http://, https:// or svn://") },
                )
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    pass, { pass = it },
                    label = { Text(if (existing == null) "Password" else "Password (blank = keep current)") },
                    singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle")
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = alias.isNotBlank() && validUrl,
                onClick = { onSave(alias.trim(), url.trim(), user.trim(), pass) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
