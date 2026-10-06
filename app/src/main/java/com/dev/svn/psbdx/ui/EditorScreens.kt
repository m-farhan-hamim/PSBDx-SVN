package com.dev.svn.psbdx.ui

import androidx.activity.compose.BackHandler
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: WcViewModel, path: String, onBack: () -> Unit) {
    val file = remember(path) { File(path) }
    var text by remember(path) { mutableStateOf("") }
    var original by remember(path) { mutableStateOf("") }
    var loaded by remember(path) { mutableStateOf(false) }
    var binary by remember(path) { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = loaded && !binary && text != original

    LaunchedEffect(path) {
        val t = vm.readText(file)
        if (t == null) binary = true else { text = t; original = t }
        loaded = true
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
                    IconButton(
                        enabled = dirty,
                        onClick = {
                            scope.launch {
                                vm.writeText(file, text)
                                original = text
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
                binary -> Text(
                    "This file is binary or larger than 1 MB and can't be edited here.",
                    Modifier.padding(16.dp),
                )
                else -> {
                    if (text.contains("<<<<<<<") && text.contains(">>>>>>>")) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Conflict markers found. Merge the sections by hand, save, then mark the file as resolved in Changes (Merged).",
                                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    )
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
