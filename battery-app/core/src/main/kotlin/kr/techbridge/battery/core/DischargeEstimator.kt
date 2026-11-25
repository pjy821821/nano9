package kr.techbridge.battery.core

/**
 * 뺀 상태에서 방전까지 남은 시간을 최근 샘플로 추정한다. OS가 방전 예측을 주지 않을 때의 대안.
 *
 * - 최근 [WINDOW_MS] 안의 뺀 상태·방전 중(전류 음수) 샘플만 쓴다.
 * - 샘플이 [MIN_SAMPLES]개 미만이거나 처음과 끝 사이가 [MIN_SPAN_MS] 미만이면 근거가 부족해 null.
 * - 남은 전하는 충전 카운터(µAh, 연료 게이지가 셈)가 있으면 그것, 없으면 설계 용량 × 잔량%.
 * - 남은 시간 = 남은 전하 / 평균 방전 전류. 화면이 꺼진 대기 소비와 켠 소비가 섞인 평균이므로 "약"으로 표시한다.
 */
object DischargeEstimator {
    const val MIN_SAMPLES = 3
    const val MIN_SPAN_MS = 2 * 60_000L
    const val WINDOW_MS = 30 * 60_000L

    fun estimateRemainingMs(samples: List<Sample>, designCapacityMah: Int?, nowMs: Long): Long? {
        val window = samples.filter { !it.plugged && it.currentUa < 0 && it.timestampMs >= nowMs - WINDOW_MS }
        if (window.size < MIN_SAMPLES) return null
        if (window.last().timestampMs - window.first().timestampMs < MIN_SPAN_MS) return null

        val avgDrawUa = window.map { -it.currentUa.toDouble() }.average()
        if (avgDrawUa <= 0.0) return null

        val last = window.last()
        val remainingUah = last.chargeCounterUah?.takeIf { it > 0 }?.toDouble()
            ?: designCapacityMah?.takeIf { it > 0 }?.let { it * 1000.0 * last.levelPercent / 100.0 }
            ?: return null
        return (remainingUah / avgDrawUa * 3_600_000.0).toLong()
    }
}
