package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTrackerTest {
    private fun sample(t: Long, level: Int, currentUa: Long, plugged: Boolean, voltageMv: Int = 4000, tempDeciC: Int = 300) =
        Sample(t, level, voltageMv, currentUa, tempDeciC, plugged, charging = plugged)

    @Test
    fun `constant 2A at 4V for 60s integrates to 33 mAh and 8 W average`() {
        val tracker = SessionTracker()
        assertTrue(tracker.onSample(sample(0, 30, -200_000, plugged = false)).isEmpty())

        var ended: ChargingSession? = null
        for (sec in 0..60) {
            val events = tracker.onSample(sample(sec * 1000L, 30 + sec / 10, 2_000_000, plugged = true))
            if (sec == 0) assertIs<SessionTracker.Event.Started>(events.first())
        }
        val out = tracker.onSample(sample(61_000L, 36, -150_000, plugged = false))
        ended = (out.single() as SessionTracker.Event.Ended).session

        assertEquals(0L, ended.startMs)
        assertEquals(61_000L, ended.endMs)
        assertEquals(30, ended.startLevel)
        assertEquals(36, ended.endLevel)
        assertEquals(2000.0 * 60 / 3600, ended.chargedMah, 0.01)
        assertEquals(8000.0, ended.avgPowerMw, 0.5)
        assertEquals(8000.0, ended.peakPowerMw, 0.01)
        assertTrue(ended.completed)
        assertNull(tracker.current)
    }

    @Test
    fun `ramping current uses trapezoid rule`() {
        val tracker = SessionTracker()
        tracker.onSample(sample(0, 50, 0, plugged = true))
        tracker.onSample(sample(60_000L, 51, 1_000_000, plugged = true)) // 0 → 1A over 60s = 8.33 mAh
        val s = tracker.current!!
        assertEquals(1000.0 / 2 * 60 / 3600, s.chargedMah, 0.01)
        assertEquals(4000.0, s.peakPowerMw, 0.01)
        assertEquals(2000.0, s.avgPowerMw, 0.01)
    }

    @Test
    fun `gap longer than tolerance splits the session as incomplete`() {
        val tracker = SessionTracker(gapToleranceMs = 60_000)
        tracker.onSample(sample(0, 20, 1_000_000, plugged = true))
        tracker.onSample(sample(10_000, 21, 1_000_000, plugged = true))
        val events = tracker.onSample(sample(600_000, 40, 1_000_000, plugged = true))

        val ended = events.filterIsInstance<SessionTracker.Event.Ended>().single().session
        assertFalse(ended.completed)
        assertEquals(10_000L, ended.endMs)
        assertIs<SessionTracker.Event.Started>(events[1])
        assertEquals(600_000L, tracker.current!!.startMs)
        assertEquals(40, tracker.current!!.startLevel)
    }

    @Test
    fun `negative current while plugged is not counted as charge`() {
        val tracker = SessionTracker()
        tracker.onSample(sample(0, 99, -50_000, plugged = true))
        tracker.onSample(sample(60_000, 99, -50_000, plugged = true))
        assertEquals(0.0, tracker.current!!.chargedMah, 0.0)
    }

    @Test
    fun `peak temperature is tracked`() {
        val tracker = SessionTracker()
        tracker.onSample(sample(0, 10, 1_000_000, plugged = true, tempDeciC = 290))
        tracker.onSample(sample(1000, 10, 1_000_000, plugged = true, tempDeciC = 345))
        tracker.onSample(sample(2000, 10, 1_000_000, plugged = true, tempDeciC = 330))
        assertEquals(345, tracker.current!!.peakTemperatureDeciC)
    }

    @Test
    fun `flush returns incomplete session and clears`() {
        val tracker = SessionTracker()
        tracker.onSample(sample(0, 10, 1_000_000, plugged = true))
        val s = tracker.flush()!!
        assertFalse(s.completed)
        assertNull(tracker.current)
        assertNull(tracker.flush())
    }

    @Test
    fun `implied capacity from level gain`() {
        val s = ChargingSession(0, 1, 23, 72, 2_310_000.0, 0.0, 0.0, 0, 2, true)
        assertEquals(2310.0 / 0.49, s.impliedCapacityMah!!, 0.01)
    }
}
