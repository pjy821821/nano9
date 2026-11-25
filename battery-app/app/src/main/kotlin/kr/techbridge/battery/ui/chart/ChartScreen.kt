package kr.techbridge.battery.ui.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
fun ChartScreen(vm: ChartViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val shown = state.shown
    val latest = state.latest
    val session = state.session
    val rangeIndex = state.rangeIndex

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_chart), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (latest?.plugged == true) {
                Text(stringResource(R.string.state_charging), style = MaterialTheme.typography.labelMedium, color = StateColors.charging(), fontWeight = FontWeight.Medium)
            }
        }
        session?.let { s ->
            Text("${stringResource(R.string.current_session)} · ${Format.time(s.startMs)} 시작 · ${s.startLevel}% → ${s.endLevel}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChartViewModel.RANGE_LABELS.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = rangeIndex == i,
                    onClick = { vm.setRange(i) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ChartViewModel.RANGE_LABELS.size),
                ) { Text(label) }
            }
        }

        val onSurface = MaterialTheme.colorScheme.onSurface
        val secondary = MaterialTheme.colorScheme.onSurfaceVariant

        // 전력
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("전력", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(latest?.let { "${Format.watts(kotlin.math.abs(it.powerMw))} W" } ?: "--", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                val pts = shown.map { Offset(it.timestampMs.toFloat(), it.powerW.toFloat().coerceAtLeast(0f)) }
                val yMax = ((pts.maxOfOrNull { it.y } ?: 10f) * 1.15f).coerceAtLeast(5f)
                LineChart(series = listOf(Series(pts, onSurface, fill = true)), yMin = 0f, yMax = yMax, modifier = Modifier.fillMaxWidth().height(150.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0 W", style = MaterialTheme.typography.labelSmall, color = secondary)
                    Text("${Format.watts((yMax * 1000).toDouble())} W", style = MaterialTheme.typography.labelSmall, color = secondary)
                }
            }
        }

        // 전류·전압: 전류는 A, 전압은 V를 같은 0~6 축에 놓는다.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("전류 · 전압", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(latest?.let { "${Format.milliamps(it.currentUa)} mA  ${Format.volts(it.voltageMv)} V" } ?: "--", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                val amps = shown.map { Offset(it.timestampMs.toFloat(), (kotlin.math.abs(it.currentUa) / 1_000_000f)) }
                val volts = shown.map { Offset(it.timestampMs.toFloat(), it.voltageMv / 1000f) }
                LineChart(
                    series = listOf(Series(amps, onSurface), Series(volts, secondary, dashed = true)),
                    yMin = 0f, yMax = 6f, modifier = Modifier.fillMaxWidth().height(80.dp),
                )
                Text("실선 전류(A) · 점선 전압(V)", style = MaterialTheme.typography.labelSmall, color = secondary)
            }
        }

        // 온도
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("온도", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    val peak = shown.maxOfOrNull { it.temperatureDeciC }
                    Text("최고 ${peak?.let { Format.celsius(it) } ?: "--"} °C", style = MaterialTheme.typography.labelSmall, color = secondary)
                }
                val temps = shown.map { Offset(it.timestampMs.toFloat(), it.temperatureDeciC / 10f) }
                LineChart(series = listOf(Series(temps, StateColors.warning())), yMin = 20f, yMax = 45f, gridLines = 0, modifier = Modifier.width(120.dp).height(36.dp))
                Text(latest?.let { "${Format.celsius(it.temperatureDeciC)} °C" } ?: "--", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        session?.let { s ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("평균 전력", Format.watts(s.avgPowerMw), "W", Modifier.weight(1f))
                Stat("최대 전력", Format.watts(s.peakPowerMw), "W", Modifier.weight(1f))
                Stat("들어간 양", Format.mah(s.chargedMicroAmpHours), "mAh", Modifier.weight(1f))
            }
        }
    }
}
