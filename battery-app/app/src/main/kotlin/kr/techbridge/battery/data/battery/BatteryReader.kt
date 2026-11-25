package kr.techbridge.battery.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kr.techbridge.battery.core.RawSample

/** OS가 일반 앱에 주는 기기 배터리 정보. 사이클 수는 Android 14 이상에서 제조사가 채워줄 때만 유효하다. */
data class DeviceBatteryInfo(
    val cycleCount: Int?,
    val healthLabel: HealthLabel,
    val technology: String?,
    val chargeCounterUah: Long?,
    val chargeTimeRemainingMs: Long?,
)

enum class HealthLabel { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD, UNKNOWN }

/** BatteryManager를 [RawSample]로 바꾼다. 단위·부호 보정은 하지 않는다(core의 Calibration이 담당). */
class BatteryReader(private val context: Context) {
    private val bm: BatteryManager = context.getSystemService(BatteryManager::class.java)

    /** ACTION_BATTERY_CHANGED 스티키 인텐트를 읽어 샘플을 만든다. 배터리 정보가 없으면 null. */
    fun read(nowMs: Long = System.currentTimeMillis()): RawSample? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        return fromIntent(intent, nowMs)
    }

    fun fromIntent(intent: Intent, nowMs: Long = System.currentTimeMillis()): RawSample {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
        val percent = if (level >= 0) (level * 100 / scale) else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // getLongProperty는 지원하지 않으면 Long.MIN_VALUE를 돌려준다.
        val currentRaw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Long.MIN_VALUE } ?: 0L
        // 충전 카운터(µAh)는 부호 보정의 근거다. 삼성은 약 3.6 mAh 눈금으로 갱신한다.
        val chargeCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it != Long.MIN_VALUE && it > 0L }

        return RawSample(
            timestampMs = nowMs,
            levelPercent = percent.coerceIn(0, 100),
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            currentRaw = currentRaw,
            temperatureDeciC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
            plugged = plugged,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            chargeCounterUah = chargeCounter,
        )
    }

    fun isFull(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_FULL
    }

    fun deviceInfo(): DeviceBatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val cycles = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it > 0 }
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> HealthLabel.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> HealthLabel.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> HealthLabel.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> HealthLabel.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> HealthLabel.FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> HealthLabel.COLD
            else -> HealthLabel.UNKNOWN
        }
        val remaining = bm.computeChargeTimeRemaining().takeIf { it >= 0 }
        val chargeCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeIf { it != Long.MIN_VALUE }
        return DeviceBatteryInfo(
            cycleCount = cycles,
            healthLabel = health,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            chargeCounterUah = chargeCounter,
            chargeTimeRemainingMs = remaining,
        )
    }
}
