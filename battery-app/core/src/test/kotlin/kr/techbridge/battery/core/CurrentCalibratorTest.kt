package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentCalibratorTest {
    private fun raw(current: Long, plugged: Boolean, level: Int = 50, t: Long = 0, counter: Long? = null) = RawSample(
        timestampMs = t, levelPercent = level, voltageMv = 4000, currentRaw = current,
        temperatureDeciC = 300, plugged = plugged, charging = plugged, chargeCounterUah = counter,
    )

    @Test
    fun `spec device - microamps positive while charging`() {
        val samples = (1..6).map { raw(1_800_000L + it, plugged = true) }
        assertEquals(Calibration(1, false), CurrentCalibrator.infer(samples))
    }

    @Test
    fun `samsung style - milliamps reported`() {
        val samples = (1..6).map { raw(1800L + it, plugged = true) }
        assertEquals(Calibration(1000, false), CurrentCalibrator.infer(samples))
    }

    @Test
    fun `xiaomi style - negative while charging flips sign`() {
        val samples = (1..6).map { raw(-2_000_000L, plugged = true) }
        assertEquals(Calibration(1, true), CurrentCalibrator.infer(samples))
    }

    @Test
    fun `milliamps and inverted together`() {
        val samples = (1..6).map { raw(-2000L, plugged = true) }
        val cal = CurrentCalibrator.infer(samples)!!
        assertEquals(Calibration(1000, true), cal)
        assertEquals(2_000_000L, cal.normalizeCurrent(-2000L))
    }

    @Test
    fun `discharging only - spec device stays spec`() {
        val samples = (1..6).map { raw(-350_000L, plugged = false) }
        assertEquals(Calibration(1, false), CurrentCalibrator.infer(samples))
    }

    @Test
    fun `discharging only - positive discharge flips`() {
        val samples = (1..6).map { raw(350L, plugged = false) }
        assertEquals(Calibration(1000, true), CurrentCalibrator.infer(samples))
    }

    @Test
    fun `trickle samples near full are ignored for unit inference`() {
        val trickle = (1..6).map { raw(90_000L, plugged = true, level = 98) }
        assertNull(CurrentCalibrator.infer(trickle))
    }

    @Test
    fun `too few samples returns null`() {
        assertNull(CurrentCalibrator.infer((1..3).map { raw(1_000_000L, plugged = true) }))
    }

    @Test
    fun `median of even list averages the middle pair`() {
        assertEquals(25L, CurrentCalibrator.median(listOf(10L, 40L, 20L, 30L)))
    }

    // ---- 약한 충전(PC USB)에서의 부호 판정 ----

    @Test
    fun `weak charging with net-negative current and no counter is undecided`() {
        // Z Flip6를 PC USB에 꽂고 화면을 켠 상태: 상태는 충전 중, 순전류는 -200~-400 mA.
        val samples = (1..6).map { raw(-300_000L - it * 1000, plugged = true, level = 87, t = it * 1000L) }
        assertNull(CurrentCalibrator.infer(samples))
    }

    @Test
    fun `weak charging with positive current and no counter is undecided too`() {
        // 반전 기기가 PC USB에서 순방전 중이면 양수로 보일 수 있으므로 양수여도 단정하지 않는다.
        val samples = (1..6).map { raw(230_000L, plugged = true, level = 87, t = it * 1000L) }
        assertNull(CurrentCalibrator.infer(samples))
    }

    @Test
    fun `weak net-negative charging with rising counter means device is inverted`() {
        // 카운터가 늘었는데 원전류가 음수 → 반전 기기.
        val samples = (0..5).map { raw(-300_000L, plugged = true, level = 87, t = it * 1000L, counter = 3_000_000L + it * 1000L) }
        assertEquals(Calibration(1, true), CurrentCalibrator.infer(samples))
    }

    @Test
    fun `weak net-negative charging with falling counter means device is spec`() {
        // Z Flip6 실측 상황: 상태는 충전 중, 원전류 음수, 카운터도 감소 → 규격대로 보고하는 기기.
        val samples = (0..5).map { raw(-300_000L, plugged = true, level = 87, t = it * 1000L, counter = 3_000_000L - it * 500L) }
        assertEquals(Calibration(1, false), CurrentCalibrator.infer(samples))
    }

    @Test
    fun `counter delta below resolution is ignored`() {
        val samples = (0..5).map { raw(-300_000L, plugged = true, level = 87, t = it * 1000L, counter = 3_000_000L - it * 100L) }
        assertNull(CurrentCalibrator.flipFromChargeCounter(samples))
        assertNull(CurrentCalibrator.infer(samples))
    }

    @Test
    fun `counter decides even when charging current is strong`() {
        // 카운터가 있으면 과반 부호보다 카운터를 믿는다.
        val samples = (0..5).map { raw(2_000_000L, plugged = true, t = it * 1000L, counter = 3_000_000L - it * 1000L) }
        assertEquals(Calibration(1, true), CurrentCalibrator.infer(samples))
    }

    @Test
    fun `discharging samples settle the sign when charging is weak`() {
        val unplugged = (0..4).map { raw(-400_000L, plugged = false, t = it * 30_000L) }
        val weak = (0..5).map { raw(-250_000L, plugged = true, level = 87, t = 200_000L + it * 1000L) }
        assertEquals(Calibration(1, false), CurrentCalibrator.infer(unplugged + weak))
    }

    @Test
    fun `counter integral weighs samples by their interval`() {
        // 30초 간격 방전 샘플 4개(-400 mA)와 1초 간격 충전 샘플 5개(+2 A): 적분은 음수, 카운터도 감소 → 규격.
        val unplugged = (0..3).map { raw(-400_000L, plugged = false, t = it * 30_000L, counter = 3_000_000L - it * 3_333L) }
        val plugged = (0..4).map { raw(2_000_000L, plugged = true, t = 90_000L + it * 1000L, counter = 2_990_001L + it * 555L) }
        assertEquals(Calibration(1, false), CurrentCalibrator.infer(unplugged + plugged))
    }
}
