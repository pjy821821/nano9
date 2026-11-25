package kr.techbridge.battery.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kr.techbridge.battery.core.ChargerFingerprint
import kr.techbridge.battery.core.ChargingSession
import kr.techbridge.battery.core.KnownCharger
import kr.techbridge.battery.core.Sample

/**
 * 사용자가 이름 붙인 충전기. 이름 붙일 당시 세션의 지문(연결 종류, 협상 전류·전압)과 평균 전력을 학습해
 * 다음에 같은 충전기가 꽂히면 이름을 자동으로 붙인다. 2차에서 BLE 식별자가 추가된다.
 */
@Entity(tableName = "chargers")
data class ChargerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdMs: Long,
    val plugType: Int? = null,
    val maxCurrentUa: Long? = null,
    val maxVoltageUv: Long? = null,
    val typicalPowerMw: Double? = null,
) {
    val fingerprint: ChargerFingerprint? get() = plugType?.let { ChargerFingerprint(it, maxCurrentUa, maxVoltageUv) }
    fun toKnown() = KnownCharger(id, fingerprint, typicalPowerMw)
}

@Entity(
    tableName = "sessions",
    indices = [Index("startMs"), Index("chargerId")],
    foreignKeys = [ForeignKey(
        entity = ChargerEntity::class, parentColumns = ["id"], childColumns = ["chargerId"],
        onDelete = ForeignKey.SET_NULL,
    )],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val chargedMicroAmpHours: Double,
    val avgPowerMw: Double,
    val peakPowerMw: Double,
    val peakTemperatureDeciC: Int,
    val sampleCount: Int,
    val completed: Boolean,
    val chargerId: Long? = null,
    val note: String? = null,
    /** 세션 시작 때의 충전기 지문. 나중에 이 세션에 이름을 붙이면 충전기가 이 값을 학습한다. */
    val plugType: Int? = null,
    val maxCurrentUa: Long? = null,
    val maxVoltageUv: Long? = null,
) {
    fun toCore() = ChargingSession(
        startMs, endMs, startLevel, endLevel, chargedMicroAmpHours, avgPowerMw, peakPowerMw,
        peakTemperatureDeciC, sampleCount, completed,
    )

    val fingerprint: ChargerFingerprint? get() = plugType?.let { ChargerFingerprint(it, maxCurrentUa, maxVoltageUv) }

    companion object {
        fun from(
            s: ChargingSession, id: Long = 0, chargerId: Long? = null, note: String? = null,
            fingerprint: ChargerFingerprint? = null,
        ) = SessionEntity(
            id = id, startMs = s.startMs, endMs = s.endMs, startLevel = s.startLevel, endLevel = s.endLevel,
            chargedMicroAmpHours = s.chargedMicroAmpHours, avgPowerMw = s.avgPowerMw, peakPowerMw = s.peakPowerMw,
            peakTemperatureDeciC = s.peakTemperatureDeciC, sampleCount = s.sampleCount, completed = s.completed,
            chargerId = chargerId, note = note,
            plugType = fingerprint?.plugType, maxCurrentUa = fingerprint?.maxCurrentUa, maxVoltageUv = fingerprint?.maxVoltageUv,
        )
    }
}

/** 세션 중 샘플. 수집은 1초마다, 세션이 끝나면 10초 간격으로 줄여 보관한다. */
@Entity(
    tableName = "samples",
    indices = [Index("sessionId", "timestampMs")],
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val levelPercent: Int,
    val voltageMv: Int,
    val currentUa: Long,
    val temperatureDeciC: Int,
) {
    val powerMw: Double get() = voltageMv.toDouble() * currentUa.toDouble() / 1_000_000.0

    companion object {
        fun from(sessionId: Long, s: Sample) = SampleEntity(
            sessionId = sessionId, timestampMs = s.timestampMs, levelPercent = s.levelPercent,
            voltageMv = s.voltageMv, currentUa = s.currentUa, temperatureDeciC = s.temperatureDeciC,
        )
    }
}
