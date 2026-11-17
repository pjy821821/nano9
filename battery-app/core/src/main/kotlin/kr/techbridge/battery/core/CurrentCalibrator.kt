package kr.techbridge.battery.core

import kotlin.math.abs

/**
 * 전류 단위(µA/mA)와 부호 규약을 샘플에서 추론한다.
 *
 * 근거:
 * - 규격은 µA이고 충전이 양수다. 그러나 일부 삼성 기기는 mA로 보고하고,
 *   일부 샤오미 기기는 충전 중에도 음수를 준다.
 * - 충전 중 전류는 잔량 95% 미만이면 거의 항상 100 mA 이상이다. 따라서 충전 샘플의
 *   중앙값 크기가 [MICROAMP_FLOOR_CHARGING] 미만이면 mA 단위로 본다.
 * - 충전 샘플이 없으면 방전 샘플로 추론한다. 방전은 mA로 보고할 때 5,000을 넘기 어렵다.
 * - 부호: 충전 중 샘플의 과반이 음수면 반전. 충전 샘플이 없으면 방전 샘플의 과반이 양수면 반전.
 */
object CurrentCalibrator {
    const val MIN_SAMPLES = 5
    const val MICROAMP_FLOOR_CHARGING = 20_000L
    const val MICROAMP_FLOOR_DISCHARGING = 5_000L
    private const val TRICKLE_LEVEL = 95

    /** 추론에 충분한 샘플이 없으면 null. 호출자는 그동안 [Calibration.SPEC]을 쓴다. */
    fun infer(samples: List<RawSample>): Calibration? {
        val charging = samples.filter {
            it.plugged && it.charging && it.currentRaw != 0L && it.levelPercent < TRICKLE_LEVEL
        }
        val discharging = samples.filter { !it.plugged && it.currentRaw != 0L }

        return when {
            charging.size >= MIN_SAMPLES -> {
                val median = median(charging.map { abs(it.currentRaw) })
                val scale = if (median < MICROAMP_FLOOR_CHARGING) 1000 else 1
                val negatives = charging.count { it.currentRaw < 0 }
                Calibration(scale, flipSign = negatives * 2 > charging.size)
            }
            discharging.size >= MIN_SAMPLES -> {
                val median = median(discharging.map { abs(it.currentRaw) })
                val scale = if (median < MICROAMP_FLOOR_DISCHARGING) 1000 else 1
                val positives = discharging.count { it.currentRaw > 0 }
                Calibration(scale, flipSign = positives * 2 > discharging.size)
            }
            else -> null
        }
    }

    internal fun median(values: List<Long>): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
