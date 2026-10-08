package com.example.mizu.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mizu.R
import com.example.mizu.core.WeighCalculator
import com.example.mizu.core.WeighOutcome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeighScreen(vm: MizuViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val allBottles by vm.bottles.collectAsStateWithLifecycle()
    val s = settings ?: return
    val bottles = allBottles.filter { it.isActive }
    val context = LocalContext.current

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var weightText by rememberSaveable { mutableStateOf("") }
    var askRefill by remember { mutableStateOf<WeighOutcome.AskRefill?>(null) }

    val bottle = bottles.firstOrNull { it.id == selectedId } ?: bottles.firstOrNull()
    val weight = weightText.toIntOrNull()
    val validWeight = WeighCalculator.isValidWeight(weight)
    val empty = bottle?.let { s.emptyWeightOf(it) }

    fun toast(resId: Int, vararg args: Any) = Toast.makeText(context, context.getString(resId, *args), Toast.LENGTH_SHORT).show()

    fun finish(outcome: WeighOutcome) {
        val b = bottle ?: return
        when (outcome) {
            is WeighOutcome.SetBaseline -> toast(R.string.weigh_saved_baseline, outcome.waterG)
            is WeighOutcome.Drink -> toast(R.string.weigh_saved_drink, outcome.amountMl)
            is WeighOutcome.AskRefill -> toast(R.string.weigh_saved_baseline, outcome.newWaterG)
            WeighOutcome.NoChange -> toast(R.string.weigh_no_change)
            WeighOutcome.Invalid -> return
        }
        vm.commitWeigh(b.id, outcome)
        weightText = ""
    }

    Scaffold(
        containerColor = Color.White,
        topBar = { MizuTopBar(stringResource(R.string.weigh_title), onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (bottle == null) {
                Text(stringResource(R.string.no_bottles), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            SectionTitle(stringResource(R.string.select_bottle))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bottles.forEach { b ->
                    FilterChip(
                        selected = b.id == bottle.id,
                        onClick = { selectedId = b.id },
                        label = { Text(b.name, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
            }

            SoftCard {
                Text(stringResource(R.string.empty_weight_used, empty ?: 0), style = MaterialTheme.typography.bodyLarge)
                Text(
                    bottle.currentWaterG?.let { stringResource(R.string.last_water, it) } ?: stringResource(R.string.no_baseline_yet),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            NumberField(
                label = stringResource(R.string.total_weight),
                value = weightText,
                onValueChange = { weightText = it },
                suffix = stringResource(R.string.unit_g),
                maxDigits = 5,
                isError = weightText.isNotEmpty() && !validWeight,
                modifier = Modifier.fillMaxWidth(),
            )
            if (weightText.isNotEmpty() && !validWeight) {
                Text(stringResource(R.string.weight_range_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            val outcome = if (validWeight && weight != null) vm.evaluateWeigh(bottle, weight) else null
            if (outcome != null && weight != null && empty != null) {
                Text(
                    stringResource(R.string.water_in_bottle, WeighCalculator.waterG(weight, empty)),
                    style = MaterialTheme.typography.titleMedium,
                )
                val preview = when (outcome) {
                    is WeighOutcome.Drink -> stringResource(R.string.preview_drink, outcome.amountMl)
                    is WeighOutcome.SetBaseline -> stringResource(R.string.preview_baseline)
                    is WeighOutcome.AskRefill -> stringResource(R.string.preview_refill)
                    WeighOutcome.NoChange -> stringResource(R.string.weigh_no_change)
                    WeighOutcome.Invalid -> ""
                }
                Text(preview, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
            }

            BigButton(
                text = stringResource(R.string.save),
                enabled = outcome != null && outcome != WeighOutcome.NoChange,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when (outcome) {
                        is WeighOutcome.AskRefill -> askRefill = outcome
                        null -> Unit
                        else -> finish(outcome)
                    }
                },
            )
            // Explicit baseline: the bottle was refilled / weighed for the first time. Never logs anything.
            BigOutlinedButton(
                text = stringResource(R.string.set_as_baseline),
                enabled = validWeight && weight != null && empty != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (weight != null && empty != null) finish(WeighOutcome.SetBaseline(WeighCalculator.waterG(weight, empty)))
                },
            )
        }
    }

    askRefill?.let { refill ->
        ConfirmDialog(
            title = stringResource(R.string.refill_title),
            message = stringResource(R.string.refill_message),
            confirmText = stringResource(R.string.refill_yes),
            dismissText = stringResource(R.string.refill_no),
            onConfirm = { askRefill = null; finish(refill) },
            onDismiss = { askRefill = null },
        )
    }
}
