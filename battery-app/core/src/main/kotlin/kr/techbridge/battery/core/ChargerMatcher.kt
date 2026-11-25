package kr.techbridge.battery.core

import kotlin.math.abs

/**
 * 충전 소스에 대해 OS가 주는 정보. USB 호스트(PC)의 이름 같은 것은 어떤 경로로도 오지 않으므로,
 * 연결 종류와 협상된 최대 전류·전압, 그리고 측정 전력으로만 충전기를 구분한다.
 */
data class ChargerFingerprint(
    /** BatteryManager.EXTRA_PLUGGED 값: 1 AC, 2 USB, 4 무선, 8 독. 0은 모름. */
    val plugType: Int,
    /** 협상된 최대 충전 전류(µA). 기기가 주지 않으면 null. */
    val maxCurrentUa: Long?,
    /** 협상된 최대 충전 전압(µV). 기기가 주지 않으면 null. 삼성은 0으로 주는 경우가 있다. */
    val maxVoltageUv: Long?,
) {
    /** 협상값이 있어 정확 비교가 가능한가. */
    val hasNegotiation: Boolean get() = (maxCurrentUa ?: 0L) > 0L

    companion object {
        const val PLUG_UNKNOWN = 0
        const val PLUG_AC = 1
        const val PLUG_USB = 2
        const val PLUG_WIRELESS = 4
        const val PLUG_DOCK = 8
    }
}

/** 사용자가 이름 붙인 충전기와, 이름 붙일 당시 학습한 지문. */
data class KnownCharger(
    val id: Long,
    val fingerprint: ChargerFingerprint?,
    /** 이름 붙일 당시 세션의 평균 전력(mW). 없으면 전력 매칭에서 제외. */
    val typicalPowerMw: Double?,
)

/**
 * 꽂힌 충전기를 이름 붙인 충전기 중 하나로 알아맞힌다. 두 단계다.
 * 1. 꽂자마자: 연결 종류 + 협상 전류·전압이 정확히 같은 충전기가 하나뿐이면 그것([matchByNegotiation]).
 * 2. 1분쯤 지난 뒤: 같은 연결 종류 중 대표 전력이 ±[POWER_TOLERANCE] 안에 드는 충전기가 하나뿐이면 그것([matchByPower]).
 * 한계: PC USB 포트 두 개처럼 같은 종류·같은 전력이면 구분하지 못한다. 그때는 null을 돌려주고 이름 없는 충전기로 둔다.
 */
object ChargerMatcher {
    const val POWER_TOLERANCE = 0.2
    /** 이보다 작은 전력은 완충 근처 트리클이라 충전기 특성을 반영하지 않는다. */
    const val MIN_POWER_MW = 500.0

    fun matchByNegotiation(fp: ChargerFingerprint, known: List<KnownCharger>): Long? {
        if (!fp.hasNegotiation) return null
        val hits = known.filter { k ->
            val f = k.fingerprint ?: return@filter false
            f.plugType == fp.plugType &&
                f.maxCurrentUa == fp.maxCurrentUa &&
                (f.maxVoltageUv ?: 0L) == (fp.maxVoltageUv ?: 0L)
        }
        return hits.singleOrNull()?.id
    }

    fun matchByPower(plugType: Int, medianPowerMw: Double, known: List<KnownCharger>, tolerance: Double = POWER_TOLERANCE): Long? {
        if (medianPowerMw < MIN_POWER_MW) return null
        val candidates = known.mapNotNull { k ->
            val p = k.typicalPowerMw ?: return@mapNotNull null
            if (p <= 0.0) return@mapNotNull null
            val kPlug = k.fingerprint?.plugType ?: ChargerFingerprint.PLUG_UNKNOWN
            if (kPlug != ChargerFingerprint.PLUG_UNKNOWN && plugType != ChargerFingerprint.PLUG_UNKNOWN && kPlug != plugType) return@mapNotNull null
            val rel = abs(p - medianPowerMw) / p
            if (rel <= tolerance) k.id else null
        }
        // 둘 이상이 허용 범위 안이면 모호하므로 단정하지 않는다.
        return candidates.singleOrNull()
    }

    fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
