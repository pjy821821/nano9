package kr.techbridge.battery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kr.techbridge.battery.BatteryApp
import kr.techbridge.battery.R
import kr.techbridge.battery.core.Calibration
import kr.techbridge.battery.core.Sample
import kr.techbridge.battery.util.Format

/** 홈 화면 위젯. 서비스가 살아 있으면 실시간 샘플을, 아니면 BatteryManager를 직접 읽는다. */
class BatteryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = BatteryApp.container(context)
        val calibration = c.settings.current().calibration ?: Calibration.SPEC
        val sample = c.live.sample.value ?: c.batteryReader.read()?.let(calibration::normalize)
        val remaining = if (sample?.plugged == true) c.batteryReader.deviceInfo().chargeTimeRemainingMs else null
        provideContent {
            GlanceTheme { WidgetContent(context, sample, remaining) }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val LARGE = DpSize(250.dp, 110.dp)
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}

@Composable
private fun WidgetContent(context: Context, s: Sample?, remainingMs: Long?) {
    val size = LocalSize.current
    val charging = s?.plugged == true
    val accent = if (charging) ColorProvider(Color(0xFF146C32)) else GlanceTheme.colors.onSurface
    val stateText = when {
        s == null -> context.getString(R.string.unknown)
        charging && remainingMs != null -> context.getString(R.string.state_charging) + " · " + context.getString(R.string.time_to_full, Format.duration(remainingMs))
        charging -> context.getString(R.string.state_charging)
        else -> context.getString(R.string.state_discharging)
    }

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(14.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "${s?.levelPercent ?: "--"}%",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(10.dp))
            if (size.width >= BatteryWidget.LARGE.width && s != null) {
                Text(
                    text = "${Format.watts(s.powerMw)} W",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(text = stateText, style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        if (size.width >= BatteryWidget.LARGE.width && s != null) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "${Format.milliamps(s.currentUa)} mA · ${Format.volts(s.voltageMv)} V · ${Format.celsius(s.temperatureDeciC)} °C",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        LinearProgressIndicator(
            progress = (s?.levelPercent ?: 0) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = accent,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
    }
}
