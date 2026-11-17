package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HealthEstimatorTest {
    private fun session(gain: Int, mah: Double, completed: Boolean = true) =
        ChargingSession(0, 1, 10, 10 + gain, mah * 1000, 0.0, 0.0, 0, 2, completed)

    @Test
    fun `median of implied capacities with quartile range`() {
        val sessions = listOf(
            session(70, 3220.0), // 4600
            session(65, 3055.0), // 4700
            session(80, 3600.0), // 4500
            session(60, 2880.0), // 4800
            session(75, 3450.0), // 4600
        )
        val e = HealthEstimator.estimate(sessions, designCapacityMah = 5000)!!
        assertEquals(4600.0, e.capacityMah, 0.01)
        assertEquals(4600.0, e.capacityLowMah, 0.01)
        assertEquals(4700.0, e.capacityHighMah, 0.01)
        assertEquals(5, e.sessionsUsed)
        assertEquals(92.0, e.healthPercent!!, 0.01)
        assertEquals(92.0, e.healthLowPercent!!, 0.01)
        assertEquals(94.0, e.healthHighPercent!!, 0.01)
    }

    @Test
    fun `short and incomplete sessions are excluded`() {
        val sessions = listOf(
            session(70, 3220.0),
            session(65, 3055.0),
            session(80, 3600.0),
            session(20, 2000.0),               // gain too small → excluded
            session(90, 1000.0, completed = false), // incomplete → excluded
        )
        val e = HealthEstimator.estimate(sessions, designCapacityMah = null)!!
        assertEquals(3, e.sessionsUsed)
        assertNull(e.healthPercent)
    }

    @Test
    fun `fewer than three usable sessions gives no estimate`() {
        assertNull(HealthEstimator.estimate(listOf(session(70, 3220.0), session(65, 3055.0)), 5000))
    }

    @Test
    fun `percentile interpolates`() {
        val sorted = listOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(2.5, HealthEstimator.percentile(sorted, 0.5), 1e-9)
        assertEquals(1.75, HealthEstimator.percentile(sorted, 0.25), 1e-9)
        assertEquals(4.0, HealthEstimator.percentile(sorted, 1.0), 1e-9)
    }
}
