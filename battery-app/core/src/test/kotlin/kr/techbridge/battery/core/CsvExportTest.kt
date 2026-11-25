package kr.techbridge.battery.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvExportTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    // 2026-11-25 13:50:03 KST
    private val startMs = 1_795_582_203_000L

    private val session = ChargingSession(
        startMs = startMs, endMs = startMs + 41 * 60_000L + 30_000L, startLevel = 87, endLevel = 91,
        chargedMicroAmpHours = 206_539.0, avgPowerMw = 1_357.4, peakPowerMw = 1_612.0,
        peakTemperatureDeciC = 285, sampleCount = 2172, completed = true,
    )

    @Test
    fun `sessions csv has bom, header and one formatted row`() {
        val csv = CsvExport.sessions(listOf(SessionExportRow(7, session, "사무실 PC", null)), seoul)
        val lines = csv.removePrefix(CsvExport.BOM).split("\r\n")
        assertTrue(csv.startsWith(CsvExport.BOM))
        assertEquals("id,start,end,duration_min,start_level,end_level,charged_mah,avg_power_w,peak_power_w,peak_temp_c,sample_count,completed,charger,note", lines[0])
        assertEquals("7,2026-11-25 13:50:03,2026-11-25 14:31:33,41.5,87,91,207,1.36,1.61,28.5,2172,1,사무실 PC,", lines[1])
        assertEquals("", lines[2]) // 마지막 줄도 CRLF로 끝난다
    }

    @Test
    fun `values with comma, quote or newline are quoted`() {
        val csv = CsvExport.sessions(listOf(SessionExportRow(1, session, "집, 거실 \"빠른\" 충전기", "첫 줄\n둘째 줄")), seoul)
        val row = csv.split("\r\n")[1]
        assertTrue(row.endsWith(",\"집, 거실 \"\"빠른\"\" 충전기\",\"첫 줄\n둘째 줄\""))
    }

    @Test
    fun `samples csv uses elapsed seconds from the first sample`() {
        val samples = listOf(
            Sample(startMs, 87, 4_181, 383_000, 285, plugged = true, charging = true),
            Sample(startMs + 1_500, 87, 4_190, 400_500, 286, plugged = true, charging = true),
        )
        val lines = CsvExport.samples(samples, seoul).removePrefix(CsvExport.BOM).split("\r\n")
        assertEquals("time,elapsed_s,level,voltage_v,current_ma,power_w,temp_c", lines[0])
        assertEquals("2026-11-25 13:50:03,0.0,87,4.181,383,1.601,28.5", lines[1])
        assertEquals("2026-11-25 13:50:04,1.5,87,4.190,401,1.678,28.6", lines[2]) // 400.5 mA는 반올림(HALF_UP)해 401
    }

    @Test
    fun `empty input still yields header`() {
        assertEquals(CsvExport.BOM + CsvExport.SAMPLE_HEADER.joinToString(",") + "\r\n", CsvExport.samples(emptyList(), seoul))
    }

    @Test
    fun `escape leaves plain values alone`() {
        assertEquals("plain", CsvExport.escape("plain"))
        assertEquals("\"a,b\"", CsvExport.escape("a,b"))
    }
}
