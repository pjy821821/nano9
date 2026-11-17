package kr.techbridge.battery.data.repo

import kotlinx.coroutines.flow.Flow
import kr.techbridge.battery.core.ChargingSession
import kr.techbridge.battery.core.Sample
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

    /** 세션 종료 후 샘플을 [keepEveryMs] 간격으로 줄인다. 마지막 샘플은 항상 남긴다. */
    suspend fun finishSession(id: Long, session: ChargingSession, keepEveryMs: Long = 10_000) {
        updateSession(id, session)
        val all = samples.forSession(id)
        if (all.size < 3) return
        val toDelete = ArrayList<Long>()
        var lastKeptMs = Long.MIN_VALUE
        for ((index, s) in all.withIndex()) {
            val isLast = index == all.lastIndex
            if (isLast || s.timestampMs - lastKeptMs >= keepEveryMs) {
                lastKeptMs = s.timestampMs
            } else {
                toDelete += s.id
            }
        }
        toDelete.chunked(500).forEach { samples.deleteByIds(it) }
    }

    suspend fun recentCompleted(limit: Int = 50): List<ChargingSession> =
        sessions.recentCompleted(limit).map { it.toCore() }

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
}
