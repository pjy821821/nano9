package kr.techbridge.battery.core

/**
 * 배터리 측 전력을 사용자가 읽기 쉬운 구간으로 나눈다.
 * 어댑터 표기(5W/15W/25W/45W)는 배터리 측에서 보통 10~30% 낮게 보이므로 경계를 그보다 낮게 둔다.
 * 프로토콜(PD/PPS/QC)은 공개 API가 없어 표시하지 않고, 이 구간 이름만 쓴다.
 */
enum class PowerBand(val maxExclusiveW: Double) {
    TRICKLE(2.0),
    STANDARD(7.5),
    FAST(16.0),
    SUPER_FAST(28.0),
    ULTRA(Double.POSITIVE_INFINITY);

    companion object {
        fun of(powerW: Double): PowerBand = entries.first { powerW < it.maxExclusiveW }
    }
}
