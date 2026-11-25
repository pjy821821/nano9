package kr.techbridge.battery.ui.sessions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.techbridge.battery.R
import kr.techbridge.battery.data.db.ChargerEntity
import kr.techbridge.battery.data.db.SessionEntity
import kr.techbridge.battery.ui.components.Card
import kr.techbridge.battery.ui.components.SectionLabel
import kr.techbridge.battery.ui.theme.StateColors
import kr.techbridge.battery.util.Format
import java.time.LocalDate

@Composable
fun SessionsScreen(vm: SessionsViewModel, onOpenSession: (Long) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val sessions = state.sessions
    val chargerNames = state.chargerNames
    val thisWeek = state.thisWeek
    val today = state.today
    val grouped = state.byDate

    val context = LocalContext.current
    // 파일 만들기 대화상자(저장소 접근 프레임워크). 권한이 필요 없고 사용자가 위치를 고른다.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportSessions(uri)
    }
    LaunchedEffect(Unit) { vm.events.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_sessions), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (sessions.isNotEmpty()) {
                    IconButton(onClick = { exportLauncher.launch("battery-sessions-${LocalDate.now()}.csv") }) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.export_csv))
                    }
                }
            }
        }
        item { WeekSummary(thisWeek) }
        if (sessions.isEmpty()) {
            item {
                Text(stringResource(R.string.sessions_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
            }
        }
        grouped.forEach { (date, list) ->
            item(key = "h-$date") {
                SectionLabel(
                    when (date) {
                        today -> "오늘"
                        today.minusDays(1) -> "어제"
                        else -> Format.date(list.first().startMs)
                    },
                )
            }
            items(list, key = { it.id }) { s -> SessionRow(s, chargerNames[s.chargerId], onClick = { onOpenSession(s.id) }) }
        }
    }
}

@Composable
private fun WeekSummary(sessions: List<SessionEntity>) {
    val count = sessions.size
    val avgPower = sessions.filter { it.avgPowerMw > 0 }.map { it.avgPowerMw }.average().takeIf { !it.isNaN() } ?: 0.0
    val total = sessions.sumOf { it.chargedMicroAmpHours }
    val byDay = (0..6).map { d -> sessions.filter { Format.localDate(it.startMs).dayOfWeek.value == d + 1 }.sumOf { it.chargedMicroAmpHours } }
    val max = byDay.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val todayIdx = LocalDate.now().dayOfWeek.value - 1

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.sessions_this_week), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryCell(stringResource(R.string.sessions_count), "${count}회", Modifier.weight(1f))
                SummaryCell(stringResource(R.string.sessions_avg_power), "${Format.watts(avgPower)} W", Modifier.weight(1f))
                SummaryCell(stringResource(R.string.sessions_total), "${Format.mah(total)} mAh", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                byDay.forEachIndexed { i, v ->
                    val frac = (v / max).toFloat().coerceIn(0.08f, 1f)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(frac).clip(RoundedCornerShape(3.dp))
                            .background(if (i == todayIdx) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SessionRow(s: SessionEntity, chargerName: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.size(width = 48.dp, height = 44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(Format.time(s.startMs), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(if (s.completed) Format.duration(s.endMs - s.startMs) else "진행/중단", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(chargerName ?: stringResource(R.string.charger_unnamed), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("${Format.mah(s.chargedMicroAmpHours)} mAh", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("${s.startLevel}% → ${s.endLevel}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("평균 ${Format.watts(s.avgPowerMw)} W · 최대 ${Format.watts(s.peakPowerMw)} W", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LevelBar(s.startLevel, s.endLevel)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 시작 %에서 끝 %까지 채운 구간 막대. */
@Composable
private fun LevelBar(from: Int, to: Int) {
    val charging = StateColors.charging()
    Row(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (from > 0) Box(Modifier.weight(from.toFloat()))
        if (to > from) Box(Modifier.weight((to - from).toFloat()).fillMaxHeight().background(charging))
        if (to < 100) Box(Modifier.weight((100 - to).toFloat()))
    }
}

@Suppress("unused")
private fun ChargerEntity.label() = name
