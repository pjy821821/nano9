package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PowerBandTest {
    @Test
    fun `bands by battery side watts`() {
        assertEquals(PowerBand.TRICKLE, PowerBand.of(0.8))
        assertEquals(PowerBand.STANDARD, PowerBand.of(4.5))
        assertEquals(PowerBand.FAST, PowerBand.of(12.0))
        assertEquals(PowerBand.SUPER_FAST, PowerBand.of(18.4))
        assertEquals(PowerBand.ULTRA, PowerBand.of(40.0))
    }

    @Test
    fun `sample power is volts times amps`() {
        val s = Sample(0, 72, 4370, 4_210_000, 312, plugged = true, charging = true)
        assertEquals(18.3977, s.powerW, 0.001)
        assertEquals(4210.0, s.currentMa, 0.0)
        assertEquals(31.2, s.temperatureC, 0.0)
    }
}
