package com.dev.svn.psbdx.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dev.svn.psbdx.PsbdxApp
import com.dev.svn.psbdx.svn.WcViewModel

sealed interface Screen {
    data object Repos : Screen
    data object Settings : Screen
    data class Wc(val repoId: String) : Screen
    data class Editor(val repoId: String, val path: String) : Screen
    data class Diff(val repoId: String, val path: String) : Screen
}

@Composable
fun PsbdxRoot(vm: AppViewModel) {
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val repos by vm.repos.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf<Screen>(Screen.Repos) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(msg) {
        msg?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    PsbdxTheme(mode) {
        Surface {
            BackHandler(stack.size > 1) { stack.removeAt(stack.lastIndex) }
            FirstRunFlow(vm)

            Box {
                AnimatedContent(
                    targetState = stack.last(),
                    transitionSpec = {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                    },
                    label = "nav",
                ) { screen ->
                    when (screen) {
                        Screen.Repos -> ReposScreen(
                            vm,
                            onOpen = { stack.add(Screen.Wc(it.id)) },
                            onSettings = { stack.add(Screen.Settings) },
                        )
                        Screen.Settings -> SettingsScreen(vm, onBack = { stack.removeAt(stack.lastIndex) })
                        is Screen.Wc -> WithWc(screen.repoId, repos) { wvm ->
                            WcScreen(
                                wvm,
                                onBack = { stack.removeAt(stack.lastIndex) },
                                onEdit = { stack.add(Screen.Editor(screen.repoId, it)) },
                                onDiff = { stack.add(Screen.Diff(screen.repoId, it)) },
                            )
                        }
                        is Screen.Editor -> WithWc(screen.repoId, repos) { wvm ->
                            EditorScreen(wvm, screen.path) { stack.removeAt(stack.lastIndex) }
                        }
                        is Screen.Diff -> WithWc(screen.repoId, repos) { wvm ->
                            DiffScreen(wvm, screen.path) { stack.removeAt(stack.lastIndex) }
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun WithWc(
    repoId: String,
    repos: List<com.dev.svn.psbdx.data.SvnRepo>,
    content: @Composable (WcViewModel) -> Unit,
) {
    val repo = repos.firstOrNull { it.id == repoId } ?: return
    val wvm: WcViewModel = viewModel(
        key = "wc-$repoId",
        factory = viewModelFactory { initializer { WcViewModel(repo) } },
    )
    content(wvm)
}

/**
 * First launch: (1) modal asking about Google Drive auto-backup, (2) backup passphrase,
 * (3) POST_NOTIFICATIONS request (Android 13+), (4) browser sign-in if requested.
 */
@Composable
private fun FirstRunFlow(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val app = PsbdxApp.instance
    var step by remember { mutableIntStateOf(if (app.store.firstRunDone) 0 else 1) }
    var signInAfterPermission by remember { mutableStateOf(false) }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (signInAfterPermission) { signInAfterPermission = false; vm.startLink(activity) }
    }

    fun finish(signIn: Boolean) {
        app.store.firstRunDone = true
        step = 0
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        signInAfterPermission = signIn
        if (needsPermission) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (signIn) { signInAfterPermission = false; vm.startLink(activity) }
    }

    // Builds without Google credentials skip the Drive question but still ask for notifications.
    LaunchedEffect(step) { if (step == 1 && !vm.driveConfigured) finish(false) }

    if (step == 1 && vm.driveConfigured) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Enable Google Drive auto-backup?") },
            text = {
                Text(
                    "Your saved repositories are backed up (encrypted with a passphrase you choose) to a " +
                        "private app folder in your Google Drive every time you add or change one. " +
                        "You can use local backup instead, or change this later in Settings.",
                )
            },
            confirmButton = { TextButton(onClick = { step = 2 }) { Text("Enable") } },
            dismissButton = { TextButton(onClick = { finish(false) }) { Text("Not now") } },
        )
    }
    if (step == 2) {
        TextInputDialog(
            title = "Choose a backup passphrase",
            label = "Passphrase",
            confirmText = "Continue",
            password = true,
            onConfirm = { vm.setPassphrase(it); vm.setAutoBackup(true); finish(true) },
            onDismiss = { finish(false) },
        )
    }
}
