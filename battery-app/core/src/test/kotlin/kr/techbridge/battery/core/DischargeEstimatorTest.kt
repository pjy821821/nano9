package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DischargeEstimatorTest {
    private val now = 10_000_000L

    private fun s(agoMs: Long, currentUa: Long, level: Int = 50, counter: Long? = null, plugged: Boolean = false) = Sample(
        timestampMs = now - agoMs, levelPercent = level, voltageMv = 3_900, currentUa = currentUa,
        temperatureDeciC = 280, plugged = plugged, charging = plugged, chargeCounterUah = counter,
    )

    @Test
    fun `counter based - remaining charge over average draw`() {
        // 30초 간격 5개, 평균 300 mA, 남은 3,000 mAh → 10시간
        val samples = (4 downTo 0).map { s(it * 30_000L, -300_000, counter = 3_000_000) }
        assertEquals(10 * 3_600_000L, DischargeEstimator.estimateRemainingMs(samples, null, now))
    }

    @Test
    fun `design capacity fallback when no counter`() {
        // 4,000 mAh × 50% = 2,000 mAh, 평균 400 mA → 5시간
        val samples = (4 downTo 0).map { s(it * 30_000L, -400_000, level = 50) }
        assertEquals(5 * 3_600_000L, DischargeEstimator.estimateRemainingMs(samples, 4000, now))
    }

    @Test
    fun `no counter and no design capacity returns null`() {
        val samples = (4 downTo 0).map { s(it * 30_000L, -400_000) }
        assertNull(DischargeEstimator.estimateRemainingMs(samples, null, now))
    }

    @Test
    fun `too few samples or too short span returns null`() {
        assertNull(DischargeEstimator.estimateRemainingMs(listOf(s(60_000, -300_000, counter = 1), s(0, -300_000, counter = 1)), 4000, now))
        val short = (2 downTo 0).map { s(it * 30_000L, -300_000, counter = 3_000_000) } // 60초 간격
        assertNull(DischargeEstimator.estimateRemainingMs(short, 4000, now))
    }

    @Test
    fun `plugged, charging-sign and stale samples are ignored`() {
        val samples = listOf(
            s(40 * 60_000L, -3_000_000, counter = 3_000_000), // 창 밖
            s(120_000, 200_000, counter = 3_000_000),         // 양수(충전 방향) 제외
            s(90_000, -300_000, counter = 3_000_000, plugged = true), // 꽂힘 제외
        ) + (4 downTo 0).map { s(it * 30_000L, -300_000, counter = 3_000_000) }
        assertEquals(10 * 3_600_000L, DischargeEstimator.estimateRemainingMs(samples, null, now))
    }

    @Test
    fun `average uses every sample in the window`() {
        // 200 mA 3개 + 400 mA 3개 → 평균 300 mA, 3,000 mAh → 10시간
        val samples = (5 downTo 3).map { s(it * 30_000L, -200_000, counter = 3_000_000) } +
            (2 downTo 0).map { s(it * 30_000L, -400_000, counter = 3_000_000) }
        assertEquals(10 * 3_600_000L, DischargeEstimator.estimateRemainingMs(samples, null, now))
    }
}
