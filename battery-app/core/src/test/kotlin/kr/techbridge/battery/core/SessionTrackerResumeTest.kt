package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTrackerResumeTest {
    private fun sample(t: Long, plugged: Boolean = true, level: Int = 50, currentUa: Long = 1_000_000, voltageMv: Int = 4000, temp: Int = 300) =
        Sample(timestampMs = t, levelPercent = level, voltageMv = voltageMv, currentUa = currentUa, temperatureDeciC = temp, plugged = plugged, charging = plugged)

    private val saved = ChargingSession(
        startMs = 1_000_000L, endMs = 1_600_000L, startLevel = 40, endLevel = 50,
        chargedMicroAmpHours = 150_000.0, avgPowerMw = 4000.0, peakPowerMw = 5000.0,
        peakTemperatureDeciC = 320, sampleCount = 600, completed = false,
    )

    @Test
    fun `resume keeps start and continues integration without a Started event`() {
        val t = SessionTracker()
        t.resume(saved)
        assertEquals(saved.startMs, t.current?.startMs)

        // 10초 뒤 샘플: 1 A × 10 s = 2,777.8 µAh인데 직전 전류를 모르므로 절반(1,388.9)만 더해진다.
        val events = t.onSample(sample(saved.endMs + 10_000, level = 51))
        assertTrue(events.none { it is SessionTracker.Event.Started })
        val progress = assertIs<SessionTracker.Event.Progress>(events.single())
        assertEquals(saved.startMs, progress.snapshot.startMs)
        assertEquals(40, progress.snapshot.startLevel)
        assertEquals(51, progress.snapshot.endLevel)
        assertEquals(601, progress.snapshot.sampleCount)
        assertEquals(150_000.0 + 1_000_000.0 / 2.0 * 10_000 / 3_600_000.0, progress.snapshot.chargedMicroAmpHours, 0.01)

        // 그 다음 10초는 온전히 더해진다.
        val next = assertIs<SessionTracker.Event.Progress>(t.onSample(sample(saved.endMs + 20_000, level = 51)).single())
        assertEquals(progress.snapshot.chargedMicroAmpHours + 1_000_000.0 * 10_000 / 3_600_000.0, next.snapshot.chargedMicroAmpHours, 0.01)
        assertEquals(5000.0, next.snapshot.peakPowerMw) // 4 W 샘플은 저장된 최대 5 W를 넘지 않는다
    }

    @Test
    fun `resumed session keeps its average power close to the saved one`() {
        val t = SessionTracker()
        t.resume(saved)
        val p = assertIs<SessionTracker.Event.Progress>(t.onSample(sample(saved.endMs + 1_000)).single())
        // 600초 평균 4 W에 1초짜리 2 W(0과 4 W의 중간)가 더해지면 거의 4 W다.
        assertEquals(4000.0, p.snapshot.avgPowerMw, 5.0)
    }

    @Test
    fun `resume then unplug completes the original session`() {
        val t = SessionTracker()
        t.resume(saved)
        val ended = assertIs<SessionTracker.Event.Ended>(t.onSample(sample(saved.endMs + 5_000, plugged = false, level = 52, currentUa = -300_000)).single())
        assertTrue(ended.session.completed)
        assertEquals(saved.startMs, ended.session.startMs)
        assertEquals(saved.endMs + 5_000, ended.session.endMs)
        assertEquals(52, ended.session.endLevel)
        assertNull(t.current)
    }

    @Test
    fun `resume after a long gap closes the old session as interrupted and starts a new one`() {
        val t = SessionTracker()
        t.resume(saved)
        val events = t.onSample(sample(saved.endMs + SessionTracker.DEFAULT_GAP_TOLERANCE_MS + 1))
        val ended = assertIs<SessionTracker.Event.Ended>(events[0])
        assertEquals(false, ended.session.completed)
        assertEquals(saved.endMs, ended.session.endMs)
        assertIs<SessionTracker.Event.Started>(events[1])
    }
}
