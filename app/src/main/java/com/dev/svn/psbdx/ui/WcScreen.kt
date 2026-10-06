package com.dev.svn.psbdx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dev.svn.psbdx.svn.*
import java.io.File
import java.text.DateFormat

private sealed interface WcDialog {
    data object NewFile : WcDialog
    data object NewFolder : WcDialog
    data object Commit : WcDialog
    data object RevertAll : WcDialog
    data class Rename(val row: FileRow) : WcDialog
    data class Delete(val row: FileRow) : WcDialog
    data class RevertOne(val file: File) : WcDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WcScreen(
    vm: WcViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDiff: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<WcDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    LaunchedEffect(tab, vm.checkedOut) { if (tab == 2 && vm.checkedOut) vm.loadLog() }
    BackHandler(tab == 0 && !vm.atRoot) { vm.up() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(vm.repo.alias, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.reload() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    if (vm.checkedOut) {
                        IconButton(onClick = { vm.update() }, enabled = !vm.busy) {
                            Icon(Icons.Default.CloudDownload, "Update")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Cleanup") },
                                    leadingIcon = { Icon(Icons.Default.CleaningServices, null) },
                                    onClick = { menuOpen = false; vm.cleanup() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Revert all changes") },
                                    leadingIcon = { Icon(Icons.Default.Undo, null) },
                                    onClick = { menuOpen = false; dialog = WcDialog.RevertAll },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (vm.checkedOut) NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Folder, null) }, label = { Text("Files") })
                NavigationBarItem(
                    tab == 1, { tab = 1 },
                    {
                        BadgedBox(badge = { if (vm.changes.isNotEmpty()) Badge { Text("${vm.changes.size}") } }) {
                            Icon(Icons.Default.Difference, null)
                        }
                    },
                    label = { Text("Changes") },
                )
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.History, null) }, label = { Text("History") })
            }
        },
        floatingActionButton = {
            if (vm.checkedOut && !vm.busy) when (tab) {
                0 -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(onClick = { dialog = WcDialog.NewFolder }) {
                        Icon(Icons.Default.CreateNewFolder, "New folder")
                    }
                    FloatingActionButton(onClick = { dialog = WcDialog.NewFile }) {
                        Icon(Icons.Default.NoteAdd, "New file")
                    }
                }
                1 -> if (vm.changes.any { it.state != ChangeState.UNVERSIONED }) ExtendedFloatingActionButton(
                    onClick = { dialog = WcDialog.Commit },
                    icon = { Icon(Icons.Default.Upload, null) },
                    text = { Text("Commit") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (vm.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (vm.progress.isNotEmpty()) {
                    Text(
                        vm.progress, maxLines = 1, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
            when {
                !vm.checkedOut -> NotCheckedOut(vm)
                tab == 0 -> FilesTab(vm, onEdit, onDiff, onMenu = { dialog = it })
                tab == 1 -> ChangesTab(vm, onEdit, onDiff, onRevert = { dialog = WcDialog.RevertOne(it) })
                else -> HistoryTab(vm)
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        WcDialog.NewFile -> TextInputDialog("New file", "File name", onConfirm = { vm.newFile(it); dialog = null }, onDismiss = { dialog = null })
        WcDialog.NewFolder -> TextInputDialog("New folder", "Folder name", onConfirm = { vm.newFolder(it); dialog = null }, onDismiss = { dialog = null })
        WcDialog.Commit -> TextInputDialog("Commit changes", "Commit message", confirmText = "Commit",
            onConfirm = { vm.commit(it); dialog = null; tab = 1 }, onDismiss = { dialog = null })
        WcDialog.RevertAll -> ConfirmDialog("Revert everything?", "All local modifications will be discarded.", "Revert",
            onConfirm = { vm.revert(listOf(vm.svn.wcDir)); dialog = null }, onDismiss = { dialog = null })
        is WcDialog.Rename -> TextInputDialog("Rename", "New name", d.row.file.name, "Rename",
            onConfirm = { vm.rename(d.row, it); dialog = null }, onDismiss = { dialog = null })
        is WcDialog.Delete -> ConfirmDialog("Delete ${d.row.file.name}?",
            if (d.row.state == ChangeState.UNVERSIONED) "The file will be removed permanently." else "It will be scheduled for deletion; commit to apply it on the server.",
            "Delete", onConfirm = { vm.delete(d.row); dialog = null }, onDismiss = { dialog = null })
        is WcDialog.RevertOne -> ConfirmDialog("Revert ${d.file.name}?", "Local changes to this item will be discarded.", "Revert",
            onConfirm = { vm.revert(listOf(d.file)); dialog = null }, onDismiss = { dialog = null })
    }
}

@Composable
private fun NotCheckedOut(vm: WcViewModel) {
    if (vm.busy) { ShimmerList(); return }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CloudDownload, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Not checked out yet", style = MaterialTheme.typography.titleMedium)
        Text(vm.repo.url, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.checkout() }) { Text("Checkout HEAD") }
    }
}

@Composable
private fun StateBadge(state: ChangeState) {
    val color = when (state) {
        ChangeState.MODIFIED -> Color(0xFF2563EB)
        ChangeState.ADDED -> Color(0xFF16A34A)
        ChangeState.DELETED, ChangeState.MISSING -> Color(0xFFDC2626)
        ChangeState.CONFLICTED -> Color(0xFFEA580C)
        ChangeState.REPLACED -> Color(0xFF9333EA)
        ChangeState.UNVERSIONED -> Color(0xFF6B7280)
    }
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
        Text(
            state.letter, color = color, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun FilesTab(vm: WcViewModel, onEdit: (String) -> Unit, onDiff: (String) -> Unit, onMenu: (WcDialog) -> Unit) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    val rel = vm.currentDir.relativeTo(vm.svn.wcDir).path
    Text(
        "/" + rel, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (vm.busy && vm.entries.isEmpty()) { ShimmerList(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (!vm.atRoot) item(key = "..") {
            ListItem(
                headlineContent = { Text("..") },
                leadingContent = { Icon(Icons.Default.ArrowUpward, null) },
                modifier = Modifier.clickable { vm.up() },
            )
        }
        if (vm.entries.isEmpty()) item(key = "empty") {
            Text("Empty folder", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(vm.entries, key = { it.file.absolutePath }) { row ->
            ListItem(
                modifier = Modifier.animateItem().clickable {
                    if (row.isDir) vm.openDir(row.file) else if (row.file.exists()) onEdit(row.file.absolutePath)
                },
                headlineContent = { Text(row.file.name, maxLines = 1) },
                supportingContent = row.state?.let { s -> { Text(s.label) } },
                leadingContent = {
                    Icon(if (row.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile, null)
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        row.state?.let { StateBadge(it) }
                        Box {
                            IconButton(onClick = { menuFor = row.file.absolutePath }) { Icon(Icons.Default.MoreVert, "Actions") }
                            DropdownMenu(
                                expanded = menuFor == row.file.absolutePath,
                                onDismissRequest = { menuFor = null },
                            ) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; onMenu(WcDialog.Rename(row)) })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuFor = null; onMenu(WcDialog.Delete(row)) })
                                if (row.state == ChangeState.UNVERSIONED) {
                                    DropdownMenuItem(text = { Text("Add to SVN") }, onClick = { menuFor = null; vm.addToSvn(row.file) })
                                }
                                if (row.state == ChangeState.MODIFIED || row.state == ChangeState.CONFLICTED) {
                                    DropdownMenuItem(text = { Text("View diff") }, onClick = { menuFor = null; onDiff(row.file.absolutePath) })
                                }
                                if (row.state != null && row.state != ChangeState.UNVERSIONED) {
                                    DropdownMenuItem(text = { Text("Revert") }, onClick = { menuFor = null; onMenu(WcDialog.RevertOne(row.file)) })
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ChangesTab(vm: WcViewModel, onEdit: (String) -> Unit, onDiff: (String) -> Unit, onRevert: (File) -> Unit) {
    if (vm.changes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = Color(0xFF16A34A))
                Text("Working copy is clean", Modifier.padding(top = 8.dp))
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (vm.changes.any { it.state == ChangeState.UNVERSIONED }) item(key = "addall") {
            FilledTonalButton(onClick = { vm.addAllUnversioned() }, modifier = Modifier.padding(16.dp, 8.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add all unversioned")
            }
        }
        items(vm.changes, key = { it.file.absolutePath }) { c ->
            ListItem(
                modifier = Modifier.animateItem().animateContentSize().clickable {
                    when (c.state) {
                        ChangeState.CONFLICTED -> onEdit(c.file.absolutePath)
                        ChangeState.UNVERSIONED, ChangeState.MISSING, ChangeState.DELETED -> {}
                        else -> onDiff(c.file.absolutePath)
                    }
                },
                leadingContent = { StateBadge(c.state) },
                headlineContent = { Text(c.relPath, maxLines = 2) },
                supportingContent = {
                    if (c.state == ChangeState.CONFLICTED) {
                        Column {
                            Text("Conflict — edit the file to merge by hand, or pick a side:")
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { vm.resolve(c.file, ResolveChoice.MINE) }) { Text("Mine") }
                                TextButton(onClick = { vm.resolve(c.file, ResolveChoice.THEIRS) }) { Text("Theirs") }
                                TextButton(onClick = { vm.resolve(c.file, ResolveChoice.MERGED) }) { Text("Merged") }
                            }
                        }
                    } else Text(c.state.label)
                },
                trailingContent = {
                    when (c.state) {
                        ChangeState.UNVERSIONED -> IconButton(onClick = { vm.addToSvn(c.file) }) { Icon(Icons.Default.Add, "Add") }
                        else -> IconButton(onClick = { onRevert(c.file) }) { Icon(Icons.Default.Undo, "Revert") }
                    }
                },
            )
        }
    }
}

@Composable
private fun HistoryTab(vm: WcViewModel) {
    var expanded by remember { mutableStateOf<Long?>(null) }
    if (vm.busy && vm.log.isEmpty()) { ShimmerList(); return }
    if (vm.log.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No history loaded") }
        return
    }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(vm.log, key = { it.revision }) { e ->
            OutlinedCard(
                Modifier.fillMaxWidth().animateContentSize().clickable {
                    expanded = if (expanded == e.revision) null else e.revision
                },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "r${e.revision}  ·  ${e.author.ifEmpty { "unknown" }}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        e.date?.let { fmt.format(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(e.message.ifBlank { "(no message)" }, maxLines = if (expanded == e.revision) Int.MAX_VALUE else 3)
                    if (expanded == e.revision && e.paths.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        e.paths.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
