package com.example.mizu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mizu.R

/** Two bars of a comparison: [value] is shown, [fraction] (0..1) is the bar length. */
private class Bar(val value: String, val fraction: Float)

private class Benefit(
    val badgeRes: Int,
    val titleRes: Int,
    val stat: String,
    val statLabelRes: Int,
    val whatRes: Int,
    val resultRes: Int,
    val caveatRes: Int,
    val source: String,
    /** 1 = preliminary, 2 = moderate, 3 = fairly strong (a pooled analysis of many trials). */
    val level: Int,
    val accent: Color,
    val soft: Color,
    val more: Bar? = null,
    val normal: Bar? = null,
)

private val Purple = Color(0xFF6B5BB5)
private val PurpleSoft = Color(0xFFF0EDFA)
private val Red = Color(0xFFC2504A)
private val RedSoft = Color(0xFFFBECEB)

private val BENEFITS = listOf(
    Benefit(
        R.string.ben1_badge, R.string.ben1_title, "−47%", R.string.ben1_stat_label, R.string.ben1_what, R.string.ben1_result,
        R.string.ben1_caveat, "Hooton et al., JAMA Internal Medicine, 2018", 2, MizuColors.WaterDeep, MizuColors.Foam,
        more = Bar("1.7", 0.53f), normal = Bar("3.2", 1f),
    ),
    Benefit(
        R.string.ben2_badge, R.string.ben2_title, "12%", R.string.ben2_stat_label, R.string.ben2_what, R.string.ben2_result,
        R.string.ben2_caveat, "Borghi et al., Journal of Urology, 1996 · AUA guideline", 2, MizuColors.Good, MizuColors.GoodSoft,
        more = Bar("12%", 0.44f), normal = Bar("27%", 1f),
    ),
    Benefit(
        R.string.ben3_badge, R.string.ben3_title, "+2 kg", R.string.ben3_stat_label, R.string.ben3_what, R.string.ben3_result,
        R.string.ben3_caveat, "Dennis et al., Obesity, 2010", 1, MizuColors.Warn, MizuColors.WarnSoft,
    ),
    Benefit(
        R.string.ben4_badge, R.string.ben4_title, "1.4%", R.string.ben4_stat_label, R.string.ben4_what, R.string.ben4_result,
        R.string.ben4_caveat, "Armstrong et al., Journal of Nutrition, 2012", 2, Purple, PurpleSoft,
    ),
    Benefit(
        R.string.ben5_badge, R.string.ben5_title, "2%", R.string.ben5_stat_label, R.string.ben5_what, R.string.ben5_result,
        R.string.ben5_caveat, "McCartney et al., Sports Medicine – Open, 2017 · Goulet, BJSM, 2013", 3, Red, RedSoft,
    ),
)

/** The fourth tab: research-backed benefits of drinking water, as tappable cards. */
@Composable
fun BenefitsScreen(bottomPadding: Dp) {
    var open by rememberSaveable { mutableStateOf(-1) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ben_title), style = MaterialTheme.typography.headlineMedium, color = MizuColors.Ink)
                Text(stringResource(R.string.ben_subtitle), style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
            }
        }
        itemsIndexed(BENEFITS) { index, benefit ->
            BenefitCard(benefit, expanded = open == index) { open = if (open == index) -1 else index }
        }
        item {
            Text(
                stringResource(R.string.ben_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MizuColors.InkSoft,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BenefitCard(b: Benefit, expanded: Boolean, onToggle: () -> Unit) {
    GlassCard(onClick = onToggle, padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(b.soft), contentAlignment = Alignment.Center) {
                Text(stringResource(b.badgeRes), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = b.accent, maxLines = 1)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(b.titleRes), style = MaterialTheme.typography.titleMedium, color = MizuColors.Ink)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(b.stat, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = b.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(b.statLabelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MizuColors.InkSoft,
                        modifier = Modifier.weight(1f).padding(bottom = 5.dp),
                    )
                }
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MizuColors.InkFaint)
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (b.more != null && b.normal != null) {
                    BarRow(stringResource(R.string.ben_bar_more), b.more, b.accent)
                    BarRow(stringResource(R.string.ben_bar_normal), b.normal, MizuColors.InkFaint.copy(alpha = 0.5f))
                }
                Block(stringResource(R.string.ben_what), stringResource(b.whatRes))
                Block(stringResource(R.string.ben_result), stringResource(b.resultRes))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MizuColors.WarnSoft).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.ben_caveat), style = MaterialTheme.typography.labelMedium, color = MizuColors.Warn)
                    Text(stringResource(b.caveatRes), style = MaterialTheme.typography.bodySmall, color = MizuColors.InkSoft)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.source, style = MaterialTheme.typography.labelSmall, color = MizuColors.InkFaint, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    val levelText = when (b.level) {
                        1 -> stringResource(R.string.ben_ev_low)
                        2 -> stringResource(R.string.ben_ev_mid)
                        else -> stringResource(R.string.ben_ev_high)
                    }
                    Pill(
                        stringResource(R.string.ben_evidence) + " " + "●".repeat(b.level) + "○".repeat(3 - b.level) + " " + levelText,
                        background = b.soft,
                        color = b.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun Block(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MizuColors.Ink)
    }
}

@Composable
private fun BarRow(label: String, bar: Bar, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MizuColors.Ink, modifier = Modifier.weight(1f))
            Text(bar.value, style = MaterialTheme.typography.labelLarge, color = MizuColors.Ink)
        }
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(MizuColors.Line)) {
            Box(Modifier.fillMaxWidth(bar.fraction).height(14.dp).clip(CircleShape).background(color))
        }
    }
}
