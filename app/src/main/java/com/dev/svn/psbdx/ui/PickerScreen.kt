package com.dev.svn.psbdx.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dev.svn.psbdx.storage.StorageRoot
import com.dev.svn.psbdx.storage.StorageRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The app's own file browser (not Android's document picker).
 * foldersOnly: open a folder and press the tick to copy that folder into the repository.
 * otherwise: tick any number of files / folders, then press the tick.
 * A skeleton loader is shown while storage volumes or a folder are being read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(foldersOnly: Boolean, onCancel: () -> Unit, onConfirm: (List<File>) -> Unit) {
    val context = LocalContext.current
    var roots by remember { mutableStateOf<List<StorageRoot>?>(null) }
    var dir by remember { mutableStateOf<File?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var unreadable by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    // scanning /storage and canonicalising paths can be slow: keep it off the main thread
    LaunchedEffect(Unit) { roots = withContext(Dispatchers.IO) { StorageRoots.list(context) } }

    LaunchedEffect(dir, showHidden) {
        val d = dir
        entries = emptyList() // drop the previous folder immediately so the skeleton is what the user sees
        unreadable = false
        if (d == null) { loading = false; return@LaunchedEffect }
        loading = true
        val list = withContext(Dispatchers.IO) { d.listFiles() }
        unreadable = list == null
        entries = (list ?: emptyArray())
            .filter { (showHidden || !it.name.startsWith(".")) && (!foldersOnly || it.isDirectory) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        loading = false
    }

    val atRoot = dir != null && roots?.any { it.dir == dir } == true
    BackHandler {
        when {
            dir == null -> onCancel()
            atRoot -> dir = null
            else -> dir = dir?.parentFile
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (dir == null) "Select storage" else dir!!.name.ifEmpty { "/" }, maxLines = 1)
                        dir?.let { Text(it.path, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }
                },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") } },
                actions = {
                    if (dir != null) IconButton(onClick = { showHidden = !showHidden }) {
                        Icon(
                            if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            "Toggle hidden files",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (foldersOnly) {
                if (dir != null && !loading) FloatingActionButton(onClick = { onConfirm(listOf(dir!!)) }) {
                    Icon(Icons.Default.Check, "Copy this folder")
                }
            } else if (selected.isNotEmpty()) {
                FloatingActionButton(onClick = { onConfirm(selected.map { File(it) }) }) {
                    BadgedBox(badge = { Badge { Text("${selected.size}") } }) { Icon(Icons.Default.Check, "Upload selected") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading || roots == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            val rootList = roots
            if (dir == null) {
                if (rootList == null) {
                    ShimmerList(rows = 3)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(rootList, key = { it.dir.path }) { r ->
                            ListItem(
                                modifier = Modifier.clickable { dir = r.dir },
                                leadingContent = {
                                    Icon(if (r.removable) Icons.Default.SdCard else Icons.Default.PhoneAndroid, null)
                                },
                                headlineContent = { Text(r.name) },
                                supportingContent = { Text(r.dir.path) },
                            )
                        }
                    }
                }
            } else {
                Text(
                    if (foldersOnly) "Open the folder you want, then tap ✓ to copy it into the repository."
                    else "Tick files and folders, then tap ✓ to copy them into the repository.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (loading) {
                    ShimmerList(rows = 8)
                } else {
                    if (unreadable) Text("This folder can't be read.", Modifier.padding(16.dp))
                    else if (entries.isEmpty()) Text("Nothing here.", Modifier.padding(16.dp))
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        if (!atRoot) item(key = "..") {
                            ListItem(
                                modifier = Modifier.clickable { dir = dir?.parentFile },
                                leadingContent = { Icon(Icons.Default.ArrowUpward, null) },
                                headlineContent = { Text("..") },
                            )
                        }
                        items(entries, key = { it.absolutePath }) { f ->
                            val isDir = f.isDirectory
                            ListItem(
                                modifier = Modifier.clickable {
                                    if (isDir) dir = f
                                    else if (!foldersOnly) {
                                        if (!selected.remove(f.absolutePath)) selected.add(f.absolutePath)
                                    }
                                },
                                leadingContent = {
                                    Icon(if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile, null)
                                },
                                headlineContent = { Text(f.name, maxLines = 1) },
                                supportingContent = if (!isDir) {
                                    { Text(Formatter.formatShortFileSize(context, f.length())) }
                                } else null,
                                trailingContent = if (!foldersOnly) {
                                    {
                                        Checkbox(
                                            checked = f.absolutePath in selected,
                                            onCheckedChange = {
                                                if (!selected.remove(f.absolutePath)) selected.add(f.absolutePath)
                                            },
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}
