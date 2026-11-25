package kr.techbridge.battery.data.battery

import kr.techbridge.battery.core.DischargeEstimator
import kr.techbridge.battery.core.Sample

enum class RemainingKind { TO_FULL, TO_EMPTY }

/** 완충까지(꽂힘) 또는 방전까지(뺌) 남은 시간. */
data class Remaining(val ms: Long, val kind: RemainingKind)

/**
 * 남은 시간 계산을 홈·알림·위젯이 같은 규칙으로 쓰게 모은다.
 * - 꽂힘: OS의 완충 예상(`computeChargeTimeRemaining`).
 * - 뺌: OS의 방전 예측(`PowerManager.getBatteryDischargePrediction`, 설정 → 배터리의 값)을 먼저 쓰고,
 *   없으면 최근 샘플의 평균 방전 전류와 충전 카운터(또는 설계 용량)로 [DischargeEstimator]가 추정한다.
 * binder 호출이 있으므로 매초가 아니라 30초마다 부른다.
 */
object RemainingTimes {
    fun compute(reader: BatteryReader, sample: Sample?, recent: List<Sample>, designCapacityMah: Int?): Remaining? {
        if (sample == null) return null
        val info = reader.deviceInfo()
        return if (sample.plugged) {
            info.chargeTimeRemainingMs?.let { Remaining(it, RemainingKind.TO_FULL) }
        } else {
            val ms = info.dischargePredictionMs
                ?: DischargeEstimator.estimateRemainingMs(recent, designCapacityMah, sample.timestampMs)
            ms?.let { Remaining(it, RemainingKind.TO_EMPTY) }
        }
    }
}
