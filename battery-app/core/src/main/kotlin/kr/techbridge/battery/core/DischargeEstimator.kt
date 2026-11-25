package kr.techbridge.battery.core

/**
 * 뺀 상태에서 방전까지 남은 시간을 최근 샘플로 추정한다. OS가 방전 예측을 주지 않을 때의 대안.
 *
 * - 최근 [WINDOW_MS] 안의 뺀 상태·방전 중(전류 음수) 샘플만 쓴다.
 * - 샘플이 [MIN_SAMPLES]개 미만이거나 처음과 끝 사이가 [MIN_SPAN_MS] 미만이면 근거가 부족해 null.
 *   뺀 직후에는 5초 간격으로 샘플링하므로 뺀 뒤 약 30초면 첫 값이 나온다(2026-11-25 실기기에서 2분 조건은
 *   사용자가 값을 보기 전에 다시 꽂을 만큼 길었고, 1분도 느리다고 했다).
 * - 남은 전하는 충전 카운터(µAh, 연료 게이지가 셈)가 있으면 그것, 없으면 설계 용량 × 잔량%.
 * - 남은 시간 = 남은 전하 / 평균 방전 전류. 평균은 샘플 간격으로 가중한다(5초·10초·30초 간격이 섞여도 특정 구간에
 *   치우치지 않게). 화면이 꺼진 대기 소비와 켠 소비가 섞인 평균이므로 "약"으로 표시한다.
 */
object DischargeEstimator {
    const val MIN_SAMPLES = 3
    const val MIN_SPAN_MS = 30_000L
    const val WINDOW_MS = 30 * 60_000L
    /** 샘플 사이 간격이 이보다 길면(서비스가 죽었던 구간 등) 가중치를 이 값으로 자른다. */
    const val MAX_WEIGHT_MS = 60_000L

    fun estimateRemainingMs(samples: List<Sample>, designCapacityMah: Int?, nowMs: Long): Long? {
        val window = samples.filter { !it.plugged && it.currentUa < 0 && it.timestampMs >= nowMs - WINDOW_MS }
        if (window.size < MIN_SAMPLES) return null
        if (window.last().timestampMs - window.first().timestampMs < MIN_SPAN_MS) return null

        val avgDrawUa = timeWeightedDrawUa(window)
        if (avgDrawUa <= 0.0) return null

        val last = window.last()
        val remainingUah = last.chargeCounterUah?.takeIf { it > 0 }?.toDouble()
            ?: designCapacityMah?.takeIf { it > 0 }?.let { it * 1000.0 * last.levelPercent / 100.0 }
            ?: return null
        return (remainingUah / avgDrawUa * 3_600_000.0).toLong()
    }

    /** 각 샘플의 방전 전류를 직전 샘플과의 간격으로 가중한 평균(µA). 시간순 입력. */
    internal fun timeWeightedDrawUa(window: List<Sample>): Double {
        var sum = 0.0
        var weight = 0.0
        for (i in 1 until window.size) {
            val dt = (window[i].timestampMs - window[i - 1].timestampMs).coerceIn(1L, MAX_WEIGHT_MS).toDouble()
            sum += -window[i].currentUa.toDouble() * dt
            weight += dt
        }
        return if (weight > 0.0) sum / weight else 0.0
    }
}
