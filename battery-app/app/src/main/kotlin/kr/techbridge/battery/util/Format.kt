package kr.techbridge.battery.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

    fun watts(mw: Double): String = String.format(Locale.US, "%.1f", mw / 1000.0)
    fun milliamps(ua: Long): String = String.format(Locale.US, "%,d", ua / 1000)
    fun volts(mv: Int): String = String.format(Locale.US, "%.2f", mv / 1000.0)
    fun celsius(deciC: Int): String = String.format(Locale.US, "%.1f", deciC / 10.0)
    fun mah(uah: Double): String = String.format(Locale.US, "%,d", (uah / 1000.0).toInt())

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt)
    fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFmt)
    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    /** "41분", "1시간 48분", "1분 미만" */
    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        if (totalMin < 1) return "1분 미만"
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "${m}분"
            m == 0L -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }
}
