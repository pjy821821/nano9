package kr.techbridge.battery.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.techbridge.battery.R
import kr.techbridge.battery.data.battery.RemainingKind
import kr.techbridge.battery.ui.components.BatteryRing
import kr.techbridge.battery.ui.components.Card
import kr.techbridge.battery.ui.components.LineChart
import kr.techbridge.battery.ui.components.Series
import kr.techbridge.battery.ui.components.ValueChip
import kr.techbridge.battery.ui.theme.StateColors
import kr.techbridge.battery.util.Format

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenChart: () -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val sample = state.sample
    val session = state.session
    val sessionId = state.sessionId
    val recent = state.recent
    val remaining = state.remaining
    val chargerName = state.chargerName

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings)) }
        }

        ChargerCard(sessionId = sessionId, chargerName = chargerName, plugged = sample?.plugged == true, onRename = vm::nameCharger)

        val charging = sample?.plugged == true
        val low = !charging && (sample?.levelPercent ?: 100) <= 20
        val ringColor = when {
            charging -> StateColors.charging()
            low -> StateColors.low()
            else -> MaterialTheme.colorScheme.onSurface
        }
        val stateLabel = when {
            sample == null -> stringResource(R.string.unknown)
            charging && sample.levelPercent >= 100 -> stringResource(R.string.state_full)
            charging && !sample.charging -> stringResource(R.string.state_not_charging)
            charging && sample.currentUa < 0 -> stringResource(R.string.state_deficit)
            charging -> stringResource(R.string.state_charging)
            low -> stringResource(R.string.state_low)
            else -> stringResource(R.string.state_discharging)
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            BatteryRing(
                levelPercent = sample?.levelPercent ?: 0,
                color = ringColor,
                stateLabel = stateLabel,
                subLabel = remaining?.let {
                    stringResource(if (it.kind == RemainingKind.TO_FULL) R.string.time_to_full else R.string.time_to_empty, Format.duration(it.ms))
                },
            )
        }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(sample?.let { Format.watts(kotlin.math.abs(it.powerMw)) } ?: "--", fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text("W", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(stringResource(R.string.power_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueChip("전류", sample?.let { "${Format.milliamps(it.currentUa)} mA" } ?: "--")
                ValueChip("전압", sample?.let { "${Format.volts(it.voltageMv)} V" } ?: "--")
                ValueChip("온도", sample?.let { "${Format.celsius(it.temperatureDeciC)} °C" } ?: "--")
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), onClick = onOpenChart) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val s = session
                    Text(
                        if (s != null) "${stringResource(R.string.current_session)} · ${Format.time(s.startMs)}부터 · ${s.startLevel}% → ${s.endLevel}%"
                        else stringResource(R.string.no_session),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val pts = recent.map { Offset(it.timestampMs.toFloat(), it.powerW.toFloat().coerceAtLeast(0f)) }
                val yMax = (pts.maxOfOrNull { it.y } ?: 10f).coerceAtLeast(5f) * 1.15f
                LineChart(
                    series = listOf(Series(pts, MaterialTheme.colorScheme.onSurface, fill = true)),
                    yMin = 0f, yMax = yMax, gridLines = 0,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
                session?.let { s ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("평균 ${Format.watts(s.avgPowerMw)} W", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("최대 ${Format.watts(s.peakPowerMw)} W", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("온도 최고 ${Format.celsius(s.peakTemperatureDeciC)} °C", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 연결된 충전기 카드. 2차에서 보조배터리 자동 인식 카드로 교체되는 자리. */
@Composable
private fun ChargerCard(sessionId: Long?, chargerName: String?, plugged: Boolean, onRename: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(chargerName) { mutableStateOf(chargerName ?: "") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Bolt, contentDescription = null) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.connected_charger), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        !plugged -> "—"
                        chargerName != null -> chargerName
                        else -> stringResource(R.string.charger_unnamed)
                    },
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                )
            }
            if (plugged && sessionId != null) {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.rename)) }
            }
        }
    }

    if (editing && sessionId != null) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true, label = { Text(stringResource(R.string.connected_charger)) }) },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    onRename(draft)
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("취소") } },
        )
    }
}
