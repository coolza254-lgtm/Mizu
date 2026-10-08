@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mizu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mizu.R
import java.util.Locale
import kotlinx.coroutines.delay

val MinTouch = 48.dp

/** 2100 -> "2,100" (Latin digits in every language). */
fun Int.grouped(): String = String.format(Locale.US, "%,d", this)

// ---------------------------------------------------------------------------------------------
// Press feel
// ---------------------------------------------------------------------------------------------

enum class HapticKind { NONE, TICK, CLICK, HEAVY, SUCCESS }

fun Haptics.play(kind: HapticKind) = when (kind) {
    HapticKind.NONE -> Unit
    HapticKind.TICK -> tick()
    HapticKind.CLICK -> click()
    HapticKind.HEAVY -> heavy()
    HapticKind.SUCCESS -> success()
}

/** Click that springs down slightly while pressed and vibrates. No ripple: the motion is the feedback. */
@Composable
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    haptic: HapticKind = HapticKind.CLICK,
    pressedScale: Float = 0.95f,
    onClick: () -> Unit,
): Modifier {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            haptics.play(haptic)
            onClick()
        }
}

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

/** White card with a hairline border and a soft blue shadow. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(20.dp),
    background: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val base = modifier
        .shadow(18.dp, shape, ambientColor = MizuColors.Water.copy(alpha = 0.10f), spotColor = MizuColors.Water.copy(alpha = 0.22f))
        .clip(shape)
        .background(background)
        .border(1.dp, MizuColors.Line, shape)
    Column(
        modifier = (if (onClick != null) base.bouncyClick(pressedScale = 0.97f, onClick = onClick) else base).padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp, tint: Color = MizuColors.WaterDeep, background: Color = MizuColors.Foam) {
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, background: Color = MizuColors.Foam, color: Color = MizuColors.WaterDeep, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(background).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    haptic: HapticKind = HapticKind.CLICK,
) {
    val shape = CircleShape
    Row(
        modifier
            .heightIn(min = 58.dp)
            .then(if (enabled) Modifier.shadow(14.dp, shape, spotColor = MizuColors.WaterDeep.copy(alpha = 0.45f)) else Modifier)
            .clip(shape)
            .background(if (enabled) MizuColors.ButtonGradient else Brush.linearGradient(listOf(MizuColors.Line, MizuColors.Line)))
            .bouncyClick(enabled = enabled, haptic = haptic, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) Color.White else MizuColors.InkFaint
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = MizuColors.Ink,
    haptic: HapticKind = HapticKind.CLICK,
) {
    Row(
        modifier
            .heightIn(min = 58.dp)
            .clip(CircleShape)
            .background(MizuColors.Foam)
            .bouncyClick(enabled = enabled, haptic = haptic, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) color else MizuColors.InkFaint
        if (icon != null) {
            Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .size(MinTouch)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, MizuColors.Line, CircleShape)
            .bouncyClick(enabled = enabled, haptic = HapticKind.TICK, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) MizuColors.Ink else MizuColors.InkFaint, modifier = Modifier.size(22.dp))
    }
}

/** Screen title row for pushed screens (weigh, export). */
@Composable
fun MizuTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Settings-style rows
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.bouncyClick(pressedScale = 0.98f, haptic = HapticKind.TICK, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, size = 38.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MizuColors.Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MizuColors.WaterDeep, textAlign = TextAlign.End)
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MizuColors.InkFaint, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(padding = PaddingValues(vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp).height(1.dp).background(MizuColors.Line))
}

@Composable
fun MizuSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.click()
            onCheckedChange(it)
        },
        colors = SwitchDefaults.colors(
            checkedTrackColor = MizuColors.WaterDeep,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = MizuColors.Line,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = MizuColors.Line,
        ),
    )
}

/** A sliding pill selector (day/week/month, goal mode...). */
@Composable
fun <T> PillSelector(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MizuColors.Mist)
            .border(1.dp, MizuColors.Line, CircleShape)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val bg by animateFloatAsState(if (isSelected) 1f else 0f, label = "pill")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = bg))
                    .bouncyClick(haptic = HapticKind.TICK, pressedScale = 0.97f) { if (!isSelected) onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MizuColors.Ink else MizuColors.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Number entry
// ---------------------------------------------------------------------------------------------

/** Large custom keypad: big round keys, a tick on every press. */
@Composable
fun NumberPad(onDigit: (Int) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier, keyHeight: Dp = 60.dp) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    Box(Modifier.weight(1f).height(keyHeight), contentAlignment = Alignment.Center) {
                        when (key) {
                            -1 -> Unit
                            -2 -> Box(
                                Modifier.fillMaxWidth().height(keyHeight).clip(RoundedCornerShape(20.dp))
                                    .bouncyClick(haptic = HapticKind.TICK, onClick = onBackspace),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.delete), tint = MizuColors.InkSoft)
                            }
                            else -> Box(
                                Modifier.fillMaxWidth().height(keyHeight).clip(RoundedCornerShape(20.dp))
                                    .background(MizuColors.Mist)
                                    .bouncyClick(haptic = HapticKind.TICK) { onDigit(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(key.toString(), style = MaterialTheme.typography.headlineSmall, color = MizuColors.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Appends a digit to a number string, respecting [maxDigits] and dropping leading zeros. */
fun String.pushDigit(d: Int, maxDigits: Int): String =
    if (length >= maxDigits) this else (if (this == "0") "" else this) + d

@Composable
fun MizuSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(MizuColors.Line))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * Bottom sheet for a whole number: big read-out, optional quick chips, keypad, save.
 * Used for manual add, editing a log and every numeric setting.
 */
@Composable
fun NumberSheet(
    title: String,
    initial: Int?,
    range: IntRange,
    unit: String,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    presets: List<Int> = emptyList(),
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    val value = text.toIntOrNull()
    val valid = value != null && value in range
    val maxDigits = range.last.toString().length
    val haptics = LocalHaptics.current
    MizuSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(
                if (text.isEmpty()) "0" else (value ?: 0).grouped(),
                style = MaterialTheme.typography.displayLarge,
                color = if (text.isEmpty()) MizuColors.InkFaint else MizuColors.Ink,
            )
            Text(" $unit", style = MaterialTheme.typography.titleMedium, color = MizuColors.InkSoft, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (text.isNotEmpty() && !valid) {
            Text(
                stringResource(R.string.value_range, range.first.grouped(), range.last.grouped(), unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MizuColors.Danger,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        if (presets.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                presets.forEach { p ->
                    Pill(
                        "+$p",
                        Modifier.bouncyClick(haptic = HapticKind.TICK) {
                            text = ((value ?: 0) + p).coerceAtMost(range.last).toString()
                        },
                    )
                }
            }
        }
        NumberPad(
            onDigit = { d -> text = text.pushDigit(d, maxDigits) },
            onBackspace = { text = text.dropLast(1) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onDelete != null) {
                SoftButton(stringResource(R.string.delete), { onDelete() }, color = MizuColors.Danger, haptic = HapticKind.HEAVY)
            }
            PrimaryButton(
                stringResource(R.string.save),
                onClick = {
                    if (valid) onSave(value!!) else haptics.heavy()
                },
                enabled = valid,
                modifier = Modifier.weight(1f),
                haptic = HapticKind.SUCCESS,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = Color.White,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge, color = MizuColors.InkSoft) },
        confirmButton = { PrimaryButton(confirmText, onConfirm) },
        dismissButton = { SoftButton(dismissText, onDismiss, haptic = HapticKind.TICK) },
    )
}

// ---------------------------------------------------------------------------------------------
// Toast with optional undo
// ---------------------------------------------------------------------------------------------

data class ToastData(val message: String, val actionLabel: String? = null, val action: (() -> Unit)? = null, val id: Long = System.nanoTime())

@Stable
class ToastState {
    var current by mutableStateOf<ToastData?>(null)
        private set

    fun show(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        current = ToastData(message, actionLabel, action)
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun BoxScope.ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val data = state.current
    LaunchedEffect(data?.id) {
        if (data != null) {
            delay(3800)
            state.dismiss()
        }
    }
    var shown by remember { mutableStateOf<ToastData?>(null) }
    if (data != null) shown = data
    AnimatedVisibility(
        visible = data != null,
        modifier = modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it } + fadeIn() + scaleIn(initialScale = 0.9f),
        exit = slideOutVertically { it } + fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        val t = shown ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .shadow(20.dp, CircleShape, spotColor = MizuColors.Ink.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(MizuColors.Ink)
                .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t.message, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(end = 12.dp))
            if (t.actionLabel != null && t.action != null) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .bouncyClick(haptic = HapticKind.TICK) {
                            t.action?.invoke()
                            state.dismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(t.actionLabel, style = MaterialTheme.typography.labelLarge, color = MizuColors.Aqua)
                }
            } else {
                Spacer(Modifier.width(14.dp))
            }
        }
    }
}
