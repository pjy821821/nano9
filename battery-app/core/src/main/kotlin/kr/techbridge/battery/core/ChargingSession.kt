package kr.techbridge.battery.core

/** 케이블을 꽂았다 뺄 때까지의 한 충전 세션 요약. */
data class ChargingSession(
    val startMs: Long,
    val endMs: Long,
    val startLevel: Int,
    val endLevel: Int,
    /** 적분한 충전량(µAh). 양의 전류만 더한다. */
    val chargedMicroAmpHours: Double,
    val avgPowerMw: Double,
    val peakPowerMw: Double,
    val peakTemperatureDeciC: Int,
    val sampleCount: Int,
    /** 케이블을 빼서 정상 종료되면 true. 서비스가 죽어 끊긴 세션은 false. */
    val completed: Boolean,
) {
    val durationMs: Long get() = endMs - startMs
    val chargedMah: Double get() = chargedMicroAmpHours / 1000.0
    val levelGain: Int get() = endLevel - startLevel

    /**
     * 이 세션으로 추정한 전체 용량(mAh). 잔량 변화가 없으면 null.
     * 예: 2,310 mAh 들어가서 23%→72%(49%p)이면 2310/0.49 ≈ 4,714 mAh.
     */
    val impliedCapacityMah: Double?
        get() = if (levelGain > 0) chargedMah / (levelGain / 100.0) else null
}
