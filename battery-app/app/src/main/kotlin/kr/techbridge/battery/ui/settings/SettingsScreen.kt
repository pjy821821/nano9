package kr.techbridge.battery.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.techbridge.battery.R
import kr.techbridge.battery.core.Calibration
import kr.techbridge.battery.data.battery.HealthLabel
import kr.techbridge.battery.data.settings.ThemeMode
import kr.techbridge.battery.ui.components.Card
import kr.techbridge.battery.ui.components.SectionLabel
import kr.techbridge.battery.util.Format
import java.util.Locale

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val batteryOptIgnored by vm.batteryOptimizationIgnored.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 시스템 설정에서 돌아오면 제외 여부를 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        vm.refreshBatteryOptimization()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))

        SectionLabel(stringResource(R.string.settings_notifications))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_charge_limit), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${settings.chargeLimitPercent}%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Switch(checked = settings.chargeLimitEnabled, onCheckedChange = vm::setChargeLimitEnabled, modifier = Modifier.padding(start = 12.dp))
                    }
                    var sliderValue by remember(settings.chargeLimitPercent) { mutableStateOf(settings.chargeLimitPercent.toFloat()) }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { vm.setChargeLimit((sliderValue / 5).toInt() * 5) },
                        valueRange = 50f..100f, steps = 9,
                        enabled = settings.chargeLimitEnabled,
                    )
                    Text(stringResource(R.string.settings_charge_limit_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                SwitchRow(stringResource(R.string.settings_temp_warn), "${Format.celsius(settings.tempWarnDeciC)} °C 이상이면 알림", settings.tempWarnEnabled, vm::setTempWarnEnabled)
                HorizontalDivider()
                SwitchRow(stringResource(R.string.settings_ongoing), "전력·잔량·남은 시간을 알림에 표시", settings.ongoingNotification, vm::setOngoingNotification)
                HorizontalDivider()
                SwitchRow(stringResource(R.string.settings_monitoring), stringResource(R.string.settings_monitoring_desc), settings.monitoringEnabled, vm::setMonitoringEnabled)
                if (settings.monitoringEnabled) {
                    HorizontalDivider()
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_battery_opt), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(if (batteryOptIgnored) R.string.settings_battery_opt_ok else R.string.settings_battery_opt_warn),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (batteryOptIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                        if (!batteryOptIgnored) {
                            // 권한이 필요 없는 시스템 목록 화면. REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 권한은 Play 정책상 선언하지 않는다.
                            OutlinedButton(onClick = { context.startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                                Text(stringResource(R.string.settings_open_system))
                            }
                        }
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_measurement))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_calibration), style = MaterialTheme.typography.bodyLarge)
                        Text(calibrationLabel(settings.calibration, settings.calibrationCheckedMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = vm::recalibrate) { Text(stringResource(R.string.settings_recalibrate)) }
                }
                HorizontalDivider()
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_design_capacity), style = MaterialTheme.typography.bodyLarge)
                        Text("건강도 추정의 기준값. 기기 사양서의 정격 용량을 입력", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var capacity by remember(settings.designCapacityMah) { mutableStateOf(settings.designCapacityMah?.toString() ?: "") }
                    OutlinedTextField(
                        value = capacity,
                        onValueChange = { v -> capacity = v.filter { it.isDigit() }.take(6); vm.setDesignCapacity(capacity.toIntOrNull()) },
                        singleLine = true, modifier = Modifier.width(110.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_display))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    val modes = ThemeMode.entries
                    val labels = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark, R.string.theme_amoled)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, mode ->
                            // 네 칸에 "AMOLED"를 넣으려면 체크 아이콘(선택은 배경색으로 구분)과 기본 좌우 여백 12dp를 줄여야 한다.
                            // 그러지 않으면 선택 시 두 줄로 꺾이거나 마지막 글자가 잘린다(실기기 캡처로 확인).
                            SegmentedButton(
                                selected = settings.theme == mode,
                                onClick = { vm.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                                icon = {},
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                            ) { Text(stringResource(labels[i]), maxLines = 1, softWrap = false) }
                        }
                    }
                }
                HorizontalDivider()
                SwitchRow(stringResource(R.string.settings_dynamic_color), "월페이퍼 색을 따라갑니다", settings.dynamicColor, vm::setDynamicColor)
            }
        }

        SectionLabel(stringResource(R.string.settings_device))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoCell(stringResource(R.string.device_cycles), device?.cycleCount?.let { "${it}회" } ?: stringResource(R.string.unknown), Modifier.weight(1f))
                    InfoCell(stringResource(R.string.device_health_os), device?.healthLabel?.let { healthLabel(it) } ?: stringResource(R.string.unknown), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoCell(
                        stringResource(R.string.device_health_est),
                        health?.let { h ->
                            h.healthPercent?.let { p -> String.format(Locale.US, "약 %.0f%% (%.0f~%.0f, 세션 %d개)", p, h.healthLowPercent, h.healthHighPercent, h.sessionsUsed) }
                                ?: String.format(Locale.US, "약 %,.0f mAh (세션 %d개)", h.capacityMah, h.sessionsUsed)
                        } ?: "세션 3개 이상 필요",
                        Modifier.weight(1f),
                    )
                    InfoCell(stringResource(R.string.device_technology), device?.technology ?: stringResource(R.string.unknown), Modifier.weight(1f))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Pro · 한 번만 결제", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("광고 없음은 기본. Pro는 무제한 기록 보관, CSV 내보내기, 위젯 테마. (결제 연동은 다음 단계)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier.padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun calibrationLabel(c: Calibration?, checkedMs: Long?): String {
    if (c == null) return "보정 전 · 규격값(µA, 충전 +) 사용 중. 충전하면 자동 보정됩니다."
    val unit = if (c.scaleToMicroAmps == 1) "µA" else "mA"
    val sign = if (c.flipSign) "부호 반전" else "충전 +"
    val when_ = checkedMs?.let { " · ${Format.date(it)}" } ?: ""
    return "자동 ($unit, $sign)$when_"
}

@Composable
private fun healthLabel(h: HealthLabel): String = when (h) {
    HealthLabel.GOOD -> "양호"
    HealthLabel.OVERHEAT -> "과열"
    HealthLabel.DEAD -> "수명 종료"
    HealthLabel.OVER_VOLTAGE -> "과전압"
    HealthLabel.FAILURE -> "이상"
    HealthLabel.COLD -> "저온"
    HealthLabel.UNKNOWN -> stringResource(R.string.unknown)
}
