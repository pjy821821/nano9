package kr.techbridge.battery.data.repo

import kotlinx.coroutines.flow.Flow
import kr.techbridge.battery.core.ChargingSession
import kr.techbridge.battery.core.Sample
import kr.techbridge.battery.core.SampleThinning
import kr.techbridge.battery.data.db.ChargerDao
import kr.techbridge.battery.data.db.ChargerEntity
import kr.techbridge.battery.data.db.SampleDao
import kr.techbridge.battery.data.db.SampleEntity
import kr.techbridge.battery.data.db.SessionDao
import kr.techbridge.battery.data.db.SessionEntity

class SessionRepository(
    private val sessions: SessionDao,
    private val samples: SampleDao,
    private val chargers: ChargerDao,
) {
    fun observeSessions(): Flow<List<SessionEntity>> = sessions.observeAll()
    fun observeSessionsSince(sinceMs: Long): Flow<List<SessionEntity>> = sessions.observeSince(sinceMs)
    fun observeSession(id: Long): Flow<SessionEntity?> = sessions.observe(id)
    fun observeSamples(sessionId: Long): Flow<List<SampleEntity>> = samples.observeForSession(sessionId)
    fun observeChargers(): Flow<List<ChargerEntity>> = chargers.observeAll()

    suspend fun startSession(session: ChargingSession, chargerId: Long? = null): Long =
        sessions.insert(SessionEntity.from(session, chargerId = chargerId))

    suspend fun updateSession(id: Long, session: ChargingSession) {
        val existing = sessions.get(id) ?: return
        sessions.update(SessionEntity.from(session, id = id, chargerId = existing.chargerId, note = existing.note))
    }

    suspend fun addSample(sessionId: Long, sample: Sample) {
        samples.insert(SampleEntity.from(sessionId, sample))
    }

    /** 세션 종료 후 샘플을 [keepEveryMs] 간격으로 줄인다. 첫 샘플과 마지막 샘플은 항상 남긴다. 선택 규칙은 [SampleThinning]. */
    suspend fun finishSession(id: Long, session: ChargingSession, keepEveryMs: Long = 10_000) {
        updateSession(id, session)
        val all = samples.forSession(id)
        val drop = SampleThinning.indicesToDrop(all.map { it.timestampMs }, keepEveryMs)
        drop.map { all[it].id }.chunked(500).forEach { samples.deleteByIds(it) }
    }

    suspend fun recentCompleted(limit: Int = 50): List<ChargingSession> =
        sessions.recentCompleted(limit).map { it.toCore() }

    /** 끝맺지 못한 세션(completed = 0). 서비스가 다시 뜰 때 이어받거나 닫는다. */
    suspend fun openSessions(): List<SessionEntity> = sessions.open()

    /** 세션의 마지막 샘플 시각. 세션 행의 endMs는 10초마다만 갱신되므로 이것이 더 정확하다. */
    suspend fun lastSampleMs(sessionId: Long): Long? = samples.last(sessionId)?.timestampMs

    /**
     * 서비스가 죽어 끝맺지 못한 세션을 마지막 샘플 시각·잔량으로 닫는다. completed는 false로 둔다(기록 화면에 "중단"으로 보임).
     * 샘플은 [finishSession]과 같은 규칙으로 줄인다.
     */
    suspend fun closeInterrupted(entity: SessionEntity) {
        val last = samples.last(entity.id)
        val core = entity.toCore()
        val fixed = if (last != null && last.timestampMs > core.endMs) {
            core.copy(endMs = last.timestampMs, endLevel = last.levelPercent)
        } else core
        finishSession(entity.id, fixed.copy(completed = false))
    }

    suspend fun setCharger(sessionId: Long, chargerId: Long?) = sessions.setCharger(sessionId, chargerId)
    suspend fun setNote(sessionId: Long, note: String?) = sessions.setNote(sessionId, note)
    suspend fun deleteSession(id: Long) = sessions.delete(id)

    suspend fun createCharger(name: String): Long =
        chargers.insert(ChargerEntity(name = name.trim(), createdMs = System.currentTimeMillis()))

    suspend fun renameCharger(id: Long, name: String) {
        val c = chargers.get(id) ?: return
        chargers.update(c.copy(name = name.trim()))
    }

    suspend fun charger(id: Long): ChargerEntity? = chargers.get(id)

    /**
     * 세션의 충전기에 이름을 붙인다. 세션에 이미 충전기가 있으면 그 이름을 바꾸고, 없으면 새로 만들어 연결한다.
     * 이름이 비어 있거나 세션이 없으면 null, 아니면 충전기 id.
     */
    suspend fun nameChargerForSession(sessionId: Long, name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val session = sessions.get(sessionId) ?: return null
        val existing = session.chargerId
        return if (existing != null) {
            renameCharger(existing, trimmed)
            existing
        } else {
            createCharger(trimmed).also { sessions.setCharger(sessionId, it) }
        }
    }
}
