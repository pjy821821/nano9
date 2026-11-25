package kr.techbridge.battery.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 세션 목록 CSV 한 줄에 필요한 것. DB 엔티티를 모르는 core가 쓰도록 평평하게 둔다. */
data class SessionExportRow(
    val id: Long,
    val session: ChargingSession,
    val chargerName: String?,
    val note: String?,
)

/**
 * CSV 생성. 엑셀(한국어 윈도)이 바로 열도록 UTF-8 BOM을 앞에 붙이고 CRLF로 끝낸다.
 * 값에 쉼표·따옴표·줄바꿈이 있으면 RFC 4180대로 따옴표로 감싼다. 숫자는 소수점 '.'(Locale.US), 시각은 주어진 시간대의 로컬 시각.
 */
object CsvExport {
    const val BOM = "﻿"
    private const val EOL = "\r\n"
    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val SESSION_HEADER = listOf(
        "id", "start", "end", "duration_min", "start_level", "end_level", "charged_mah",
        "avg_power_w", "peak_power_w", "peak_temp_c", "sample_count", "completed", "charger", "note",
    )
    val SAMPLE_HEADER = listOf("time", "elapsed_s", "level", "voltage_v", "current_ma", "power_w", "temp_c")

    fun sessions(rows: List<SessionExportRow>, zone: ZoneId = ZoneId.systemDefault()): String =
        build(SESSION_HEADER, rows.map { r ->
            val s = r.session
            listOf(
                r.id.toString(),
                time(s.startMs, zone),
                time(s.endMs, zone),
                fmt(s.durationMs / 60_000.0, 1),
                s.startLevel.toString(),
                s.endLevel.toString(),
                fmt(s.chargedMah, 0),
                fmt(s.avgPowerMw / 1000.0, 2),
                fmt(s.peakPowerMw / 1000.0, 2),
                fmt(s.peakTemperatureDeciC / 10.0, 1),
                s.sampleCount.toString(),
                if (s.completed) "1" else "0",
                r.chargerName ?: "",
                r.note ?: "",
            )
        })

    /** 한 세션의 샘플. elapsed_s는 첫 샘플부터 지난 초. */
    fun samples(samples: List<Sample>, zone: ZoneId = ZoneId.systemDefault()): String {
        val t0 = samples.firstOrNull()?.timestampMs ?: 0L
        return build(SAMPLE_HEADER, samples.map { s ->
            listOf(
                time(s.timestampMs, zone),
                fmt((s.timestampMs - t0) / 1000.0, 1),
                s.levelPercent.toString(),
                fmt(s.voltageV, 3),
                fmt(s.currentMa, 0),
                fmt(s.powerW, 3),
                fmt(s.temperatureC, 1),
            )
        })
    }

    private fun build(header: List<String>, rows: List<List<String>>): String = buildString {
        append(BOM)
        append(header.joinToString(",")).append(EOL)
        for (row in rows) append(row.joinToString(",") { escape(it) }).append(EOL)
    }

    internal fun escape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun time(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).format(timeFmt)
    private fun fmt(v: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", v)
}
