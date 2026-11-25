package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChargerMatcherTest {
    private val officePc = KnownCharger(1, ChargerFingerprint(ChargerFingerprint.PLUG_USB, 500_000, 5_000_000), typicalPowerMw = 1_800.0)
    private val homeAdapter = KnownCharger(2, ChargerFingerprint(ChargerFingerprint.PLUG_AC, 3_000_000, 9_000_000), typicalPowerMw = 14_000.0)
    private val wireless = KnownCharger(3, ChargerFingerprint(ChargerFingerprint.PLUG_WIRELESS, null, null), typicalPowerMw = 7_000.0)
    private val known = listOf(officePc, homeAdapter, wireless)

    @Test
    fun `negotiation - exact plug, current and voltage match`() {
        val fp = ChargerFingerprint(ChargerFingerprint.PLUG_AC, 3_000_000, 9_000_000)
        assertEquals(2L, ChargerMatcher.matchByNegotiation(fp, known))
    }

    @Test
    fun `negotiation - missing voltage on both sides still matches`() {
        val k = KnownCharger(9, ChargerFingerprint(ChargerFingerprint.PLUG_USB, 500_000, null), null)
        val fp = ChargerFingerprint(ChargerFingerprint.PLUG_USB, 500_000, 0)
        assertEquals(9L, ChargerMatcher.matchByNegotiation(fp, listOf(k)))
    }

    @Test
    fun `negotiation - no negotiated current means no exact match`() {
        assertNull(ChargerMatcher.matchByNegotiation(ChargerFingerprint(ChargerFingerprint.PLUG_USB, null, null), known))
        assertNull(ChargerMatcher.matchByNegotiation(ChargerFingerprint(ChargerFingerprint.PLUG_USB, 0, 0), known))
    }

    @Test
    fun `negotiation - two chargers with the same values are ambiguous`() {
        val twin = officePc.copy(id = 7)
        val fp = ChargerFingerprint(ChargerFingerprint.PLUG_USB, 500_000, 5_000_000)
        assertNull(ChargerMatcher.matchByNegotiation(fp, known + twin))
    }

    @Test
    fun `power - within tolerance and same plug type`() {
        assertEquals(1L, ChargerMatcher.matchByPower(ChargerFingerprint.PLUG_USB, 1_650.0, known))
        assertEquals(2L, ChargerMatcher.matchByPower(ChargerFingerprint.PLUG_AC, 15_500.0, known))
    }

    @Test
    fun `power - different plug type is excluded even if power matches`() {
        assertNull(ChargerMatcher.matchByPower(ChargerFingerprint.PLUG_AC, 1_800.0, known))
    }

    @Test
    fun `power - unknown plug type on either side does not exclude`() {
        val k = KnownCharger(5, null, typicalPowerMw = 10_000.0)
        assertEquals(5L, ChargerMatcher.matchByPower(ChargerFingerprint.PLUG_AC, 9_500.0, listOf(k)))
        assertEquals(1L, ChargerMatcher.matchByPower(ChargerFingerprint.PLUG_UNKNOWN, 1_800.0, known))
    }

    @Test
    fun `power - outside tolerance or trickle returns null`() {
        assertNull(ChargerMatcher.matchByPower(ChargerFingerprint.PLUG_USB, 2_500.0, known))
        assertNull(ChargerMatcher.matchByPower(ChargerFingerprint.PLUG_USB, 300.0, known))
    }

    @Test
    fun `power - two candidates inside tolerance are ambiguous`() {
        val similar = KnownCharger(8, ChargerFingerprint(ChargerFingerprint.PLUG_AC, null, null), typicalPowerMw = 13_000.0)
        assertNull(ChargerMatcher.matchByPower(ChargerFingerprint.PLUG_AC, 13_500.0, known + similar))
    }

    @Test
    fun `median of even and odd lists`() {
        assertEquals(2.5, ChargerMatcher.median(listOf(4.0, 1.0, 3.0, 2.0)))
        assertEquals(3.0, ChargerMatcher.median(listOf(5.0, 1.0, 3.0)))
    }
}
