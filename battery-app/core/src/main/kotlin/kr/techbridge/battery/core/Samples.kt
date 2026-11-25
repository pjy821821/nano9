package kr.techbridge.battery.core

/**
 * 안드로이드 BatteryManager가 준 값을 그대로 담는 원시 샘플.
 * currentRaw는 BATTERY_PROPERTY_CURRENT_NOW 원값이다. 규격은 µA, 충전이 +이지만
 * 기기마다 mA로 주거나 부호가 반대인 경우가 있어 [Calibration]으로 정규화한다.
 */
data class RawSample(
    val timestampMs: Long,
    val levelPercent: Int,
    val voltageMv: Int,
    val currentRaw: Long,
    val temperatureDeciC: Int,
    val plugged: Boolean,
    val charging: Boolean,
    /** BATTERY_PROPERTY_CHARGE_COUNTER(µAh). 지원하지 않는 기기는 null. 부호 보정의 물리적 근거로 쓴다. */
    val chargeCounterUah: Long? = null,
)

/** 단위와 부호를 정규화한 샘플. currentUa는 µA, 충전이 +, 방전이 -. */
data class Sample(
    val timestampMs: Long,
    val levelPercent: Int,
    val voltageMv: Int,
    val currentUa: Long,
    val temperatureDeciC: Int,
    val plugged: Boolean,
    val charging: Boolean,
) {
    /** 배터리 측 전력(mW). mV × µA = nW, 1e6으로 나누면 mW. */
    val powerMw: Double get() = voltageMv.toDouble() * currentUa.toDouble() / 1_000_000.0

    val powerW: Double get() = powerMw / 1000.0
    val currentMa: Double get() = currentUa / 1000.0
    val voltageV: Double get() = voltageMv / 1000.0
    val temperatureC: Double get() = temperatureDeciC / 10.0
}

/** 기기별 전류 보정. [scaleToMicroAmps]는 1(µA) 또는 1000(mA로 보고하는 기기). */
data class Calibration(
    val scaleToMicroAmps: Int,
    val flipSign: Boolean,
) {
    fun normalize(raw: RawSample): Sample = Sample(
        timestampMs = raw.timestampMs,
        levelPercent = raw.levelPercent,
        voltageMv = raw.voltageMv,
        currentUa = normalizeCurrent(raw.currentRaw),
        temperatureDeciC = raw.temperatureDeciC,
        plugged = raw.plugged,
        charging = raw.charging,
    )

    fun normalizeCurrent(raw: Long): Long {
        val scaled = raw * scaleToMicroAmps
        return if (flipSign) -scaled else scaled
    }

    companion object {
        /** 안드로이드 규격 그대로(µA, 충전 +). 보정 전 기본값. */
        val SPEC = Calibration(scaleToMicroAmps = 1, flipSign = false)
    }
}
