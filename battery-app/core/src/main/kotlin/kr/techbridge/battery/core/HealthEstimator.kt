package kr.techbridge.battery.core

/**
 * 충전 세션에서 배터리 용량과 건강도를 추정한다.
 *
 * 각 세션의 impliedCapacity(들어간 mAh ÷ 잔량 변화 비율)를 모아 중앙값을 용량으로,
 * 사분위 범위를 불확실성으로 쓴다. 잔량 변화가 [minGainPercent] 미만인 세션은
 * 잔량 % 양자화 오차가 커서 제외한다(AccuBattery도 60% 이상 세션만 쓴다).
 *
 * 결과는 반드시 "추정치, ±범위, 세션 N개"로 표시한다. OS가 건강도를 주는 기기에서는 그 값을 우선한다.
 */
object HealthEstimator {
    const val DEFAULT_MIN_GAIN_PERCENT = 60
    const val MIN_SESSIONS = 3

    data class Estimate(
        val capacityMah: Double,
        val capacityLowMah: Double,
        val capacityHighMah: Double,
        val sessionsUsed: Int,
        /** 설계 용량을 알 때만 채워진다. */
        val healthPercent: Double?,
        val healthLowPercent: Double?,
        val healthHighPercent: Double?,
    )

    fun estimate(
        sessions: List<ChargingSession>,
        designCapacityMah: Int?,
        minGainPercent: Int = DEFAULT_MIN_GAIN_PERCENT,
    ): Estimate? {
        val caps = sessions
            .filter { it.completed && it.levelGain >= minGainPercent }
            .mapNotNull { it.impliedCapacityMah }
            .filter { it.isFinite() && it > 0 }
            .sorted()
        if (caps.size < MIN_SESSIONS) return null

        val mid = percentile(caps, 0.5)
        val low = percentile(caps, 0.25)
        val high = percentile(caps, 0.75)
        val toPct: (Double) -> Double? = { c -> designCapacityMah?.takeIf { it > 0 }?.let { c / it * 100.0 } }
        return Estimate(
            capacityMah = mid,
            capacityLowMah = low,
            capacityHighMah = high,
            sessionsUsed = caps.size,
            healthPercent = toPct(mid),
            healthLowPercent = toPct(low),
            healthHighPercent = toPct(high),
        )
    }

    internal fun percentile(sorted: List<Double>, p: Double): Double {
        require(sorted.isNotEmpty())
        if (sorted.size == 1) return sorted[0]
        val pos = p * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        val frac = pos - lo
        return sorted[lo] * (1 - frac) + sorted[hi] * frac
    }
}
