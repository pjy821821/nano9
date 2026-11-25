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
        val short = (2 downTo 0).map { s(it * 10_000L, -300_000, counter = 3_000_000) } // 20초 범위
        assertNull(DischargeEstimator.estimateRemainingMs(short, 4000, now))
        // 5초 간격 7개(30초 범위)면 충분하다: 3,000 mAh / 300 mA = 10시간
        val enough = (6 downTo 0).map { s(it * 5_000L, -300_000, counter = 3_000_000) }
        assertEquals(10 * 3_600_000L, DischargeEstimator.estimateRemainingMs(enough, null, now))
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
    fun `average is weighted by the interval before each sample`() {
        // 0·10·20초에 200 mA, 50초에 400 mA: (200×10 + 200×10 + 400×30) / 50 = 320 mA → 3,000 mAh / 320 mA = 9.375시간
        val samples = listOf(s(50_000, -200_000, counter = 3_000_000), s(40_000, -200_000, counter = 3_000_000),
            s(30_000, -200_000, counter = 3_000_000), s(0, -400_000, counter = 3_000_000))
        assertEquals((9.375 * 3_600_000).toLong(), DischargeEstimator.estimateRemainingMs(samples, null, now))
    }

    @Test
    fun `a long gap is capped so one sample cannot dominate`() {
        // 10분 공백 뒤 샘플의 가중치는 60초로 잘린다: (200×10 + 200×10 + 400×60) / 80 = 350 mA
        val samples = listOf(s(620_000, -200_000, counter = 3_000_000), s(610_000, -200_000, counter = 3_000_000),
            s(600_000, -200_000, counter = 3_000_000), s(0, -400_000, counter = 3_000_000))
        assertEquals((3_000_000.0 / 350_000.0 * 3_600_000).toLong(), DischargeEstimator.estimateRemainingMs(samples, null, now))
    }

    @Test
    fun `average uses every interval in the window`() {
        // 30초 간격 200 mA 3개 + 400 mA 3개. 첫 샘플은 앞 구간이 없어 가중치 0 → (200×2 + 400×3)/5 = 320 mA → 9.375시간
        val samples = (5 downTo 3).map { s(it * 30_000L, -200_000, counter = 3_000_000) } +
            (2 downTo 0).map { s(it * 30_000L, -400_000, counter = 3_000_000) }
        assertEquals((9.375 * 3_600_000).toLong(), DischargeEstimator.estimateRemainingMs(samples, null, now))
    }
}
