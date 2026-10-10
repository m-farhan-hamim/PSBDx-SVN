package com.dev.svn.psbdx.ui

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.svn.psbdx.svn.WcViewModel
import kotlinx.coroutines.launch
import java.io.File

private const val SINGLE_FIELD_MAX_CHARS = 30_000
private const val CHUNK_LINES = 150

/**
 * Short files: one text field. Long files: the text is cut into blocks of [CHUNK_LINES] lines shown
 * in a lazy list, so only the visible blocks are laid out (a single huge text field gets slower with
 * every keystroke). Binary files and files over 5 MB are handed to Android's "Open with" instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: WcViewModel, path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    var text by remember(path) { mutableStateOf("") }
    var original by remember(path) { mutableStateOf("") }
    val chunks = remember(path) { mutableStateListOf<String>() }
    var chunked by remember(path) { mutableStateOf(false) }
    var chunkDirty by remember(path) { mutableStateOf(false) }
    var hasConflict by remember(path) { mutableStateOf(false) }
    var loaded by remember(path) { mutableStateOf(false) }
    var external by remember(path) { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = loaded && !external && (if (chunked) chunkDirty else text != original)

    fun openExternally() {
        if (!openWith(context, file)) Toast.makeText(context, "No app can open this file", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(path) {
        val t = vm.readText(file)
        if (t == null) {
            external = true
        } else {
            hasConflict = t.contains("<<<<<<<") && t.contains(">>>>>>>")
            if (t.length > SINGLE_FIELD_MAX_CHARS) {
                val parts = withContext(Dispatchers.Default) {
                    t.split("\n").chunked(CHUNK_LINES).map { it.joinToString("\n") }
                }
                chunks.addAll(parts)
                chunked = true
            } else {
                text = t
                original = t
            }
        }
        loaded = true
        if (external) openExternally() // binary / too large: go straight to the system chooser
    }
    BackHandler(dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name + if (dirty) " •" else "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { openExternally() }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open with")
                    }
                    IconButton(
                        enabled = dirty,
                        onClick = {
                            scope.launch {
                                val content = if (chunked) {
                                    withContext(Dispatchers.Default) { chunks.joinToString("\n") }
                                } else text
                                vm.writeText(file, content)
                                if (chunked) chunkDirty = false else original = content
                                vm.message = "Saved ${file.name}"
                            }
                        },
                    ) { Icon(Icons.Default.Save, "Save") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when {
                !loaded -> ShimmerList(rows = 4)
                external -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.InsertDriveFile, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This file is binary or larger than 5 MB, so it can't be edited in the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { openExternally() }) { Text("Open with…") }
                }
                else -> {
                    if (hasConflict) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Conflict markers found. Merge the sections by hand, save, then mark the file as resolved in Changes (Merged).",
                                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    val style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (chunked) {
                        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(12.dp)) {
                            items(count = chunks.size, key = { it }) { i ->
                                BasicTextField(
                                    value = chunks[i],
                                    onValueChange = { v ->
                                        if (v != chunks[i]) { chunks[i] = v; chunkDirty = true }
                                    },
                                    textStyle = style,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            textStyle = style,
                        )
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        ConfirmDialog("Discard changes?", "You have unsaved edits.", "Discard",
            onConfirm = { confirmDiscard = false; onBack() }, onDismiss = { confirmDiscard = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(vm: WcViewModel, path: String, onBack: () -> Unit) {
    val file = remember(path) { File(path) }
    var diff by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) { diff = vm.diff(file) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diff · ${file.name}", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        val d = diff
        if (d == null) {
            Box(Modifier.padding(pad)) { ShimmerList(rows = 5) }
        } else {
            val add = Color(0xFF16A34A)
            val del = Color(0xFFDC2626)
            val lines = remember(d) { d.lines() }
            LazyColumn(Modifier.fillMaxSize().padding(pad).horizontalScroll(rememberScrollState())) {
                itemsIndexed(lines) { _, line ->
                    val (bg, fg) = when {
                        line.startsWith("+++") || line.startsWith("---") -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
                        line.startsWith("+") -> add.copy(alpha = 0.16f) to MaterialTheme.colorScheme.onSurface
                        line.startsWith("-") -> del.copy(alpha = 0.16f) to MaterialTheme.colorScheme.onSurface
                        line.startsWith("@@") -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        line.ifEmpty { " " }, color = fg, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        softWrap = false,
                        modifier = Modifier.background(bg).padding(horizontal = 8.dp, vertical = 1.dp).widthIn(min = 360.dp),
                    )
                }
            }
        }
    }
}
