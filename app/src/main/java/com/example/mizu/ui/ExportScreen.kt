package com.example.mizu.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mizu.util.toLocale
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.mizu.R
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(vm: MizuViewModel, toast: ToastState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val logs by vm.logs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locale = (settings?.language ?: com.example.mizu.core.AppLanguage.TH).toLocale()
    val today = remember { LocalDate.now() }
    var from by remember { mutableStateOf(today) }
    var to by remember { mutableStateOf(today) }
    var preset by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    val iso = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val pretty = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    val unit = stringResource(R.string.unit_ml)

    val inRange = logs.filter { val d = it.timestamp.toLocalDate(); !d.isBefore(from) && !d.isAfter(to) }
    val savedText = stringResource(R.string.export_saved)
    val failedText = stringResource(R.string.export_failed)

    fun export(share: Boolean) {
        scope.launch {
            val csv = vm.buildCsv(from, to)
            val name = "mizu_${from.format(iso)}_${to.format(iso)}.csv"
            val bytes = csv.toByteArray(Charsets.UTF_8) // starts with the BOM, so Excel reads Thai correctly
            val ok = withContext(Dispatchers.IO) {
                if (share) writeAndShare(context, name, bytes) else saveToDownloads(context, name, bytes)
            }
            if (ok) haptics.success() else haptics.heavy()
            if (!share || !ok) toast.show(if (ok) String.format(savedText, name) else failedText)
        }
    }

    Column(Modifier.fillMaxSize()) {
        MizuTopBar(stringResource(R.string.export_csv), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PillSelector(
                options = listOf(
                    0 to stringResource(R.string.range_today),
                    7 to stringResource(R.string.range_7_days),
                    30 to stringResource(R.string.range_30_days),
                ),
                selected = preset,
                onSelect = { days ->
                    preset = days
                    to = today
                    from = if (days == 0) today else today.minusDays(days - 1L)
                },
            )
            GlassCard(onClick = { picking = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.DateRange)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(stringResource(R.string.date_range), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft)
                        Text(
                            if (from == to) from.format(pretty) else "${from.format(pretty)} – ${to.format(pretty)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(stringResource(R.string.change), style = MaterialTheme.typography.labelLarge, color = MizuColors.WaterDeep)
                }
            }
            GlassCard {
                Text(stringResource(R.string.export_preview), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(inRange.size.toString(), style = MaterialTheme.typography.displayMedium)
                    Text(
                        " ${stringResource(R.string.entries)} · ${inRange.sumOf { it.amountMl }.grouped()} $unit",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MizuColors.InkSoft,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(stringResource(R.string.export_hint), style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
            }
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(stringResource(R.string.share_csv), { export(share = true) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SoftButton(stringResource(R.string.save_to_downloads), { export(share = false) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.FileDownload)
            }
        }
    }

    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from.toPickerMillis(),
            initialSelectedEndDateMillis = to.toPickerMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            colors = DatePickerDefaults.colors(containerColor = Color.White),
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis?.fromPickerMillis()
                        val end = state.selectedEndDateMillis?.fromPickerMillis() ?: start
                        if (start != null && end != null) {
                            from = start
                            to = end
                            preset = -1
                            haptics.click()
                        }
                        picking = false
                    },
                ) { Text(stringResource(R.string.save), color = MizuColors.WaterDeep) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel), color = MizuColors.InkSoft) } },
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.height(520.dp),
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = MizuColors.WaterDeep,
                    dayInSelectionRangeContainerColor = MizuColors.Foam,
                    todayDateBorderColor = MizuColors.Water,
                ),
            )
        }
    }
}

private fun writeAndShare(context: Context, name: String, bytes: ByteArray): Boolean = try {
    val dir = File(context.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val file = File(dir, name).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

/** Android 10+ only: MediaStore needs no storage permission. Older versions use the share sheet. */
private fun saveToDownloads(context: Context, name: String, bytes: ByteArray): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        false
    } else {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
    }
} catch (_: Exception) {
    false
}
