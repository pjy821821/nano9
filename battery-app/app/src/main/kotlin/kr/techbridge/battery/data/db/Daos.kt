package kr.techbridge.battery.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: SessionEntity): Long
    @Update suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startMs DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE startMs >= :sinceMs ORDER BY startMs DESC")
    fun observeSince(sinceMs: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE completed = 1 ORDER BY startMs DESC LIMIT :limit")
    suspend fun recentCompleted(limit: Int): List<SessionEntity>

    /** 끝맺지 못한 세션(서비스가 죽었거나 지금 진행 중). 오래된 것부터. */
    @Query("SELECT * FROM sessions WHERE completed = 0 ORDER BY startMs")
    suspend fun open(): List<SessionEntity>

    @Query("UPDATE sessions SET chargerId = :chargerId WHERE id = :sessionId")
    suspend fun setCharger(sessionId: Long, chargerId: Long?)

    @Query("UPDATE sessions SET note = :note WHERE id = :sessionId")
    suspend fun setNote(sessionId: Long, note: String?)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sessions WHERE endMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}

@Dao
interface SampleDao {
    @Insert suspend fun insert(sample: SampleEntity): Long
    @Insert suspend fun insertAll(samples: List<SampleEntity>)

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY timestampMs")
    fun observeForSession(sessionId: Long): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY timestampMs")
    suspend fun forSession(sessionId: Long): List<SampleEntity>

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY timestampMs DESC LIMIT 1")
    suspend fun last(sessionId: Long): SampleEntity?

    @Query("DELETE FROM samples WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface ChargerDao {
    @Insert suspend fun insert(charger: ChargerEntity): Long
    @Update suspend fun update(charger: ChargerEntity)

    @Query("SELECT * FROM chargers ORDER BY name")
    fun observeAll(): Flow<List<ChargerEntity>>

    @Query("SELECT * FROM chargers WHERE id = :id")
    suspend fun get(id: Long): ChargerEntity?

    @Query("DELETE FROM chargers WHERE id = :id")
    suspend fun delete(id: Long)
}
