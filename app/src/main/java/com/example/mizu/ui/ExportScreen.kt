package com.example.mizu.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
fun ExportScreen(vm: MizuViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var from by remember { mutableStateOf(today) }
    var to by remember { mutableStateOf(today) }
    var picking by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    fun export(share: Boolean) {
        scope.launch {
            val csv = vm.buildCsv(from, to)
            val name = "mizu_${from.format(fmt)}_${to.format(fmt)}.csv"
            val bytes = csv.toByteArray(Charsets.UTF_8) // starts with the BOM, so Excel reads Thai correctly
            val ok = withContext(Dispatchers.IO) {
                if (share) writeAndShare(context, name, bytes) else saveToDownloads(context, name, bytes)
            }
            if (!share) {
                Toast.makeText(
                    context,
                    if (ok) context.getString(R.string.export_saved, name) else context.getString(R.string.export_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Scaffold(containerColor = Color.White, topBar = { MizuTopBar(stringResource(R.string.export_csv), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SoftCard {
                Text(stringResource(R.string.date_range), style = MaterialTheme.typography.titleMedium)
                Text("${from.format(fmt)}  →  ${to.format(fmt)}", style = MaterialTheme.typography.bodyLarge)
            }
            BigOutlinedButton(stringResource(R.string.pick_date_range), { picking = true }, Modifier.fillMaxWidth())
            BigButton(stringResource(R.string.share_csv), { export(share = true) }, Modifier.fillMaxWidth())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                BigButton(stringResource(R.string.save_to_downloads), { export(share = false) }, Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.export_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from.toPickerMillis(),
            initialSelectedEndDateMillis = to.toPickerMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis?.fromPickerMillis()
                        val end = state.selectedEndDateMillis?.fromPickerMillis() ?: start
                        if (start != null && end != null) {
                            from = start
                            to = end
                        }
                        picking = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(520.dp))
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
