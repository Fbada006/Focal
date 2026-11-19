package io.disruption.focal.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockListDao {
    @Query("SELECT * FROM block_lists ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<BlockListEntity>>

    @Query("SELECT * FROM block_lists WHERE id = :id")
    suspend fun get(id: String): BlockListEntity?

    @Upsert
    suspend fun upsert(entity: BlockListEntity)

    @Query("DELETE FROM block_lists WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions WHERE outcome IS NULL ORDER BY plannedEnd")
    fun observeRunning(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE outcome IS NULL ORDER BY plannedEnd")
    suspend fun getRunning(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE scheduleId = :scheduleId AND startedAt >= :since")
    suspend fun getStartedBySchedule(scheduleId: String, since: Long): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun get(id: String): FocusSessionEntity?

    @Upsert
    suspend fun upsert(entity: FocusSessionEntity)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun get(id: String): ScheduleEntity?

    @Upsert
    suspend fun upsert(entity: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: String)
}
