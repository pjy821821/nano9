package kr.techbridge.battery.core

import kotlin.math.abs

/**
 * 전류 단위(µA/mA)와 부호 규약을 샘플에서 추론한다.
 *
 * 단위:
 * - 규격은 µA다. 그러나 일부 삼성 기기는 mA로 보고한다.
 * - 충전 중 전류는 잔량 95% 미만이면 거의 항상 100 mA 이상이다. 따라서 충전 샘플의
 *   중앙값 크기가 [MICROAMP_FLOOR_CHARGING] 미만이면 mA 단위로 본다.
 * - 충전 샘플이 없으면 방전 샘플로 추론한다. 방전은 mA로 보고할 때 5,000을 넘기 어렵다.
 *
 * 부호(규격은 충전 +, 일부 샤오미 기기는 충전 중에도 음수):
 * 1. 충전 카운터(µAh)가 있으면 그 증감이 물리적 진실이다. 카운터가 늘었는데 원전류 적분이 음수면 반전.
 *    "충전 중" 상태라도 PC USB(≤0.5 A)처럼 공급이 소비보다 작으면 순전류는 실제로 음수이므로,
 *    상태값만 믿고 부호를 정하면 Z Flip6에서 그랬듯 정상 기기를 반전으로 오판한다(2026-11-25 실기기 확인).
 * 2. 카운터가 없으면 비연결(방전) 샘플을 본다. 뽑힌 상태의 전류는 반드시 음수여야 하므로 과반이 양수면 반전.
 * 3. 그것도 없으면 충전 샘플을 보되, 크기가 [STRONG_CHARGE_UA] 이상일 때만 과반 부호를 믿는다.
 *    약한 충전은 위의 이유로 부호를 판단할 수 없어 null을 돌려주고 다음 샘플을 기다린다.
 */
object CurrentCalibrator {
    const val MIN_SAMPLES = 5
    const val MICROAMP_FLOOR_CHARGING = 20_000L
    const val MICROAMP_FLOOR_DISCHARGING = 5_000L
    /** 충전 샘플만으로 부호를 정할 때 요구하는 최소 크기(µA, 단위 보정 후). 벽 충전기는 보통 1 A를 넘고 PC USB는 넘지 못한다. */
    const val STRONG_CHARGE_UA = 1_000_000L
    /** 충전 카운터 변화가 이보다 작으면 잡음·해상도 한계로 보고 부호 판정에 쓰지 않는다. 삼성은 한 눈금이 약 3,580 µAh다. */
    const val MIN_COUNTER_DELTA_UAH = 2_000L
    private const val TRICKLE_LEVEL = 95

    /** 추론에 충분한 샘플이 없으면 null. 호출자는 그동안 [Calibration.SPEC]을 쓴다. */
    fun infer(samples: List<RawSample>): Calibration? {
        val scale = inferScale(samples) ?: return null
        val flip = inferFlip(samples, scale) ?: return null
        return Calibration(scale, flip)
    }

    internal fun inferScale(samples: List<RawSample>): Int? {
        val charging = chargingSamples(samples)
        val discharging = dischargingSamples(samples)
        return when {
            charging.size >= MIN_SAMPLES ->
                if (median(charging.map { abs(it.currentRaw) }) < MICROAMP_FLOOR_CHARGING) 1000 else 1
            discharging.size >= MIN_SAMPLES ->
                if (median(discharging.map { abs(it.currentRaw) }) < MICROAMP_FLOOR_DISCHARGING) 1000 else 1
            else -> null
        }
    }

    internal fun inferFlip(samples: List<RawSample>, scale: Int): Boolean? {
        flipFromChargeCounter(samples)?.let { return it }

        val discharging = dischargingSamples(samples)
        if (discharging.size >= MIN_SAMPLES) {
            val positives = discharging.count { it.currentRaw > 0 }
            return positives * 2 > discharging.size
        }

        val charging = chargingSamples(samples)
        if (charging.size >= MIN_SAMPLES) {
            val medianUa = median(charging.map { abs(it.currentRaw) }) * scale
            if (medianUa < STRONG_CHARGE_UA) return null
            val negatives = charging.count { it.currentRaw < 0 }
            return negatives * 2 > charging.size
        }
        return null
    }

    /**
     * 충전 카운터 변화량의 부호와 원전류의 시간 적분 부호를 비교한다.
     * 둘이 다르면 기기가 부호를 반대로 보고하는 것이다. 판단 근거가 부족하면 null.
     */
    internal fun flipFromChargeCounter(samples: List<RawSample>): Boolean? {
        val withCounter = samples.filter { it.chargeCounterUah != null }.sortedBy { it.timestampMs }
        if (withCounter.size < 2) return null
        val delta = withCounter.last().chargeCounterUah!! - withCounter.first().chargeCounterUah!!
        if (abs(delta) < MIN_COUNTER_DELTA_UAH) return null

        var integral = 0.0
        for (i in 1 until withCounter.size) {
            val dtMs = (withCounter[i].timestampMs - withCounter[i - 1].timestampMs).coerceAtLeast(0)
            integral += withCounter[i].currentRaw.toDouble() * dtMs
        }
        if (integral == 0.0) return null
        return (delta > 0) != (integral > 0)
    }

    private fun chargingSamples(samples: List<RawSample>) = samples.filter {
        it.plugged && it.charging && it.currentRaw != 0L && it.levelPercent < TRICKLE_LEVEL
    }

    private fun dischargingSamples(samples: List<RawSample>) = samples.filter { !it.plugged && it.currentRaw != 0L }

    internal fun median(values: List<Long>): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
