package com.example.mizu.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mizu.R
import com.example.mizu.util.localizedWrapper

private enum class Tab { HOME, HISTORY, SETTINGS }

private enum class Overlay { NONE, WEIGH, EXPORT }

/** Clears the floating navigation bar at the bottom of scrolling screens. */
private val NavClearance = 128.dp

/**
 * @param startScreen debug-only deep link ("history", "settings", "weigh") used by the CI screenshot job.
 * @param demo debug-only: seed sample data.
 */
@Composable
fun MizuRoot(vm: MizuViewModel = viewModel(), startScreen: String? = null, demo: Boolean = false) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val base = LocalContext.current
    val haptics = remember(base) { Haptics(base.applicationContext) }

    LaunchedEffect(demo) { if (demo) vm.seedDemo() }

    MizuTheme {
        val s = settings
        if (s == null) {
            Box(Modifier.fillMaxSize().background(Color.White))
            return@MizuTheme
        }
        SideEffect { haptics.enabled = s.hapticsEnabled }
        // Wrap (not replace) the Activity context so strings follow the in-app language with no restart.
        val localized = remember(s.language, base) { base.localizedWrapper(s.language) }
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalHaptics provides haptics,
        ) {
            MizuShell(vm, startScreen)
        }
    }
}

@Composable
private fun MizuShell(vm: MizuViewModel, startScreen: String?) {
    var tab by rememberSaveable {
        mutableStateOf(
            when (startScreen) {
                "history" -> Tab.HISTORY
                "settings" -> Tab.SETTINGS
                else -> Tab.HOME
            },
        )
    }
    var overlay by rememberSaveable { mutableStateOf(if (startScreen == "weigh") Overlay.WEIGH else Overlay.NONE) }
    // Keep the last overlay so it can animate out.
    var shownOverlay by remember { mutableStateOf(overlay) }
    if (overlay != Overlay.NONE) shownOverlay = overlay
    val toast = remember { ToastState() }
    val haptics = LocalHaptics.current

    NotificationPermissionPrompt(vm)
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    BackHandler(enabled = overlay == Overlay.NONE && tab != Tab.HOME) { tab = Tab.HOME }

    Box(Modifier.fillMaxSize().background(MizuColors.BackdropGradient)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 24 }) togetherWith fadeOut(tween(160))
            },
            label = "tabs",
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) { current ->
            when (current) {
                Tab.HOME -> HomeScreen(vm, toast, onWeigh = { overlay = Overlay.WEIGH }, bottomPadding = NavClearance)
                Tab.HISTORY -> HistoryScreen(vm, bottomPadding = NavClearance)
                Tab.SETTINGS -> SettingsScreen(vm, onExport = { overlay = Overlay.EXPORT }, bottomPadding = NavClearance)
            }
        }

        FloatingNav(
            tab = tab,
            onSelect = {
                if (it != tab) {
                    haptics.tick()
                    tab = it
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
        )

        AnimatedVisibility(
            visible = overlay != Overlay.NONE,
            enter = slideInVertically(tween(340)) { it / 6 } + fadeIn(tween(260)),
            exit = slideOutVertically(tween(260)) { it / 6 } + fadeOut(tween(200)),
        ) {
            Box(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
                when (shownOverlay) {
                    Overlay.WEIGH -> WeighScreen(vm, toast, onBack = { overlay = Overlay.NONE })
                    Overlay.EXPORT -> ExportScreen(vm, toast, onBack = { overlay = Overlay.NONE })
                    Overlay.NONE -> Unit
                }
            }
        }

        ToastHost(
            toast,
            Modifier.navigationBarsPadding().padding(bottom = if (overlay == Overlay.NONE) 96.dp else 92.dp),
        )
    }
}

@Composable
private fun FloatingNav(tab: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(24.dp, CircleShape, ambientColor = MizuColors.Water.copy(alpha = 0.2f), spotColor = MizuColors.WaterDeep.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, MizuColors.Line, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Icons.Rounded.WaterDrop, stringResource(R.string.tab_home), tab == Tab.HOME) { onSelect(Tab.HOME) }
        NavItem(Icons.Rounded.BarChart, stringResource(R.string.tab_history), tab == Tab.HISTORY) { onSelect(Tab.HISTORY) }
        NavItem(Icons.Rounded.Settings, stringResource(R.string.tab_settings), tab == Tab.SETTINGS) { onSelect(Tab.SETTINGS) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(if (selected) MizuColors.Foam else Color.Transparent)
            .bouncyClick(haptic = HapticKind.NONE, pressedScale = 0.9f, onClick = onClick)
            .animateContentSize(tween(240))
            .padding(horizontal = if (selected) 20.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) MizuColors.WaterDeep else MizuColors.InkFaint, modifier = Modifier.size(24.dp))
        AnimatedVisibility(visible = selected, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MizuColors.Ink, modifier = Modifier.padding(start = 8.dp))
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
