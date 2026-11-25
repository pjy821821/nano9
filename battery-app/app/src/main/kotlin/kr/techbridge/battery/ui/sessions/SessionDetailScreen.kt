package kr.techbridge.battery.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.techbridge.battery.R
import kr.techbridge.battery.ui.components.Card
import kr.techbridge.battery.ui.components.LineChart
import kr.techbridge.battery.ui.components.Series
import kr.techbridge.battery.ui.components.Stat
import kr.techbridge.battery.ui.theme.StateColors
import kr.techbridge.battery.util.Format

@Composable
fun SessionDetailScreen(vm: SessionDetailViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val samples = state.samples
    val chargerName = state.chargerName
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var draft by remember(chargerName) { mutableStateOf(chargerName ?: "") }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    val s = state.session ?: return

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Column(modifier = Modifier.weight(1f)) {
                Text(chargerName ?: stringResource(R.string.charger_unnamed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${Format.date(s.startMs)} ${Format.time(s.startMs)} ~ ${Format.time(s.endMs)} · ${Format.duration(s.endMs - s.startMs)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!s.completed) {
            Text(stringResource(R.string.session_incomplete), style = MaterialTheme.typography.bodySmall, color = StateColors.warning())
        }
        if (chargerName == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("이 충전기에 이름을 붙이면 다음부터 같은 충전기끼리 묶어 비교합니다.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { renaming = true }) { Text("이름 붙이기") }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.session_charged), Format.mah(s.chargedMicroAmpHours), "mAh", Modifier.weight(1f))
            Stat(stringResource(R.string.session_level), "${s.startLevel} → ${s.endLevel}", "%", Modifier.weight(1f))
            Stat(stringResource(R.string.session_duration), Format.duration(s.endMs - s.startMs), null, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.sessions_avg_power), Format.watts(s.avgPowerMw), "W", Modifier.weight(1f))
            Stat(stringResource(R.string.session_peak_power), Format.watts(s.peakPowerMw), "W", Modifier.weight(1f))
            Stat(stringResource(R.string.session_peak_temp), Format.celsius(s.peakTemperatureDeciC), "°C", Modifier.weight(1f))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("전력과 잔량", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val power = samples.map { Offset(it.timestampMs.toFloat(), (it.powerMw / 1000.0).toFloat().coerceAtLeast(0f)) }
                val peakW = ((power.maxOfOrNull { it.y } ?: 10f) * 1.15f).coerceAtLeast(5f)
                // 잔량은 0~100을 전력 축 0~peakW에 맞춰 겹친다.
                val level = samples.map { Offset(it.timestampMs.toFloat(), it.levelPercent / 100f * peakW) }
                LineChart(
                    series = listOf(Series(power, MaterialTheme.colorScheme.onSurface, fill = true), Series(level, StateColors.charging())),
                    yMin = 0f, yMax = peakW, modifier = Modifier.fillMaxWidth().height(130.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Format.time(s.startMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("실선 전력 · 초록 잔량", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Format.time(s.endMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { renaming = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.rename)) }
            OutlinedButton(onClick = { deleting = true }, modifier = Modifier.weight(1f)) { Text("세션 삭제", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    vm.nameCharger(draft)
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("취소") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("세션 삭제") },
            text = { Text("이 세션과 샘플이 삭제됩니다. 되돌릴 수 없습니다.") },
            confirmButton = { TextButton(onClick = { deleting = false; vm.delete() }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("취소") } },
        )
    }
}
