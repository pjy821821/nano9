package kr.techbridge.battery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleThinningTest {
    private fun everySecond(count: Int, startMs: Long = 1_795_569_402_098L) = List(count) { startMs + it * 1000L }

    @Test
    fun `keeps first, last, and one every interval`() {
        val ts = everySecond(31) // 0..30초
        val drop = SampleThinning.indicesToDrop(ts, keepEveryMs = 10_000)
        val kept = ts.indices.filter { it !in drop }
        assertEquals(listOf(0, 10, 20, 30), kept)
    }

    @Test
    fun `real-device size - 2172 one-second samples thin to 219`() {
        val ts = everySecond(2172)
        val kept = ts.size - SampleThinning.indicesToDrop(ts, 10_000).size
        // 0, 10, 20, ..., 2170 (218개) + 마지막(2171) = 219개
        assertEquals(219, kept)
    }

    @Test
    fun `fewer than three samples are untouched`() {
        assertTrue(SampleThinning.indicesToDrop(everySecond(2), 10_000).isEmpty())
        assertTrue(SampleThinning.indicesToDrop(emptyList(), 10_000).isEmpty())
    }

    @Test
    fun `last sample is always kept even when close to the previous kept one`() {
        val ts = everySecond(12) // 0..11초: 0, 10 유지, 11은 마지막이라 유지
        val drop = SampleThinning.indicesToDrop(ts, 10_000)
        assertTrue(11 !in drop)
        assertTrue(10 !in drop)
        assertTrue(0 !in drop)
    }

    @Test
    fun `irregular gaps keep the first sample after each gap`() {
        val ts = listOf(0L, 1_000L, 2_000L, 25_000L, 26_000L, 40_000L)
        val drop = SampleThinning.indicesToDrop(ts, 10_000)
        assertEquals(listOf(1, 2, 4), drop)
    }
}
