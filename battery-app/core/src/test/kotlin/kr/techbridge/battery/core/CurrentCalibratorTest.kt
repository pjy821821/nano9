package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentCalibratorTest {
    private fun raw(current: Long, plugged: Boolean, level: Int = 50, t: Long = 0) = RawSample(
        timestampMs = t, levelPercent = level, voltageMv = 4000, currentRaw = current,
        temperatureDeciC = 300, plugged = plugged, charging = plugged,
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
}
