package com.example.mizu.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mizu.R
import com.example.mizu.util.localized

private enum class Tab { HOME, HISTORY, SETTINGS }

private enum class Overlay { NONE, WEIGH, EXPORT }

@Composable
fun MizuRoot(vm: MizuViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val base = LocalContext.current

    MizuTheme {
        val s = settings
        if (s == null) {
            Surface(Modifier.fillMaxSize(), color = Color.White) {}
            return@MizuTheme
        }
        // Re-resolve resources whenever the language changes: no activity restart needed.
        val localized = remember(s.language, base) { base.localized(s.language) }
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
        ) {
            MizuScaffold(vm)
        }
    }
}

@Composable
private fun MizuScaffold(vm: MizuViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    NotificationPermissionPrompt(vm)

    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    BackHandler(enabled = overlay == Overlay.NONE && tab != Tab.HOME) { tab = Tab.HOME }

    when (overlay) {
        Overlay.WEIGH -> WeighScreen(vm, onBack = { overlay = Overlay.NONE })
        Overlay.EXPORT -> ExportScreen(vm, onBack = { overlay = Overlay.NONE })
        Overlay.NONE -> Scaffold(
            containerColor = Color.White,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    )
                    NavigationBarItem(
                        selected = tab == Tab.HOME, onClick = { tab = Tab.HOME }, colors = colors,
                        icon = { Icon(Icons.Filled.Home, null) }, label = { Text(stringResource(R.string.tab_home)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.HISTORY, onClick = { tab = Tab.HISTORY }, colors = colors,
                        icon = { Icon(Icons.Filled.BarChart, null) }, label = { Text(stringResource(R.string.tab_history)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SETTINGS, onClick = { tab = Tab.SETTINGS }, colors = colors,
                        icon = { Icon(Icons.Filled.Settings, null) }, label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(vm, onWeigh = { overlay = Overlay.WEIGH })
                    Tab.HISTORY -> HistoryScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm, onExport = { overlay = Overlay.EXPORT })
                }
            }
        }
    }
}

/** Explains why notifications are needed, once, then asks for POST_NOTIFICATIONS on Android 13+. */
@Composable
private fun NotificationPermissionPrompt(vm: MizuViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val prompted by vm.notificationPrompted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (prompted == false && !granted) {
        ConfirmDialog(
            title = stringResource(R.string.notif_permission_title),
            message = stringResource(R.string.notif_permission_message),
            confirmText = stringResource(R.string.allow),
            dismissText = stringResource(R.string.not_now),
            onConfirm = {
                vm.markNotificationPrompted()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { vm.markNotificationPrompted() },
        )
    }
}
